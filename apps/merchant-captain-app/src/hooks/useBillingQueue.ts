/**
 * useBillingQueue — offline-first POS billing hook
 *
 * - Cart lives in component state (fast, no persistence needed per session)
 * - Pending bills that failed to sync are stored in AsyncStorage
 * - NetInfo listener drains the pending queue when connectivity is restored
 */

import { useState, useEffect, useCallback, useRef } from 'react';
import AsyncStorage from '@react-native-async-storage/async-storage';
import NetInfo, { NetInfoState } from '@react-native-community/netinfo';
import { Platform } from 'react-native';

const API_BASE_URL = Platform.select({
  android: 'http://10.0.2.2:8080',
  ios: 'http://localhost:8080',
  default: 'http://localhost:8080',
});

const PENDING_BILLS_KEY = '@pawsnearme:pending_bills';

// ─── Types ────────────────────────────────────────────────────────────────────

export interface CartItem {
  productId: string;
  name: string;
  barcodeScanned: string;
  quantity: number;
  unitPrice: number;
  discountAmount: number;
  discountType: 'NONE' | 'FLAT' | 'PERCENT';
}

interface PendingBill {
  id: string; // client-side idempotency key (UUID v4)
  storeId: string;
  staffId: string;
  items: CartItem[];
  createdAt: string; // ISO timestamp
  retries: number;
}

// ─── Helpers ──────────────────────────────────────────────────────────────────

function generateIdempotencyKey(): string {
  // RFC 4122 v4 UUID — no external dependency required
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0;
    const v = c === 'x' ? r : (r & 0x3) | 0x8;
    return v.toString(16);
  });
}

async function loadPendingBills(): Promise<PendingBill[]> {
  try {
    const raw = await AsyncStorage.getItem(PENDING_BILLS_KEY);
    return raw ? JSON.parse(raw) : [];
  } catch {
    return [];
  }
}

async function savePendingBills(bills: PendingBill[]): Promise<void> {
  try {
    await AsyncStorage.setItem(PENDING_BILLS_KEY, JSON.stringify(bills));
  } catch {
    // Non-critical — bill will retry next time
  }
}

async function submitBill(bill: PendingBill, accessToken: string): Promise<boolean> {
  const subtotal = bill.items.reduce((s, i) => s + i.unitPrice * i.quantity, 0);
  const totalDiscount = bill.items.reduce((s, i) => s + i.discountAmount, 0);
  const tax = (subtotal - totalDiscount) * 0.18;
  const grandTotal = subtotal - totalDiscount + tax;

  try {
    const res = await fetch(`${API_BASE_URL}/api/v1/catalog/bills`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'X-User-Role': 'MERCHANT',
        'X-User-Id': bill.staffId,
        ...(accessToken ? { Authorization: `Bearer ${accessToken}` } : {}),
      },
      body: JSON.stringify({
        storeId: bill.storeId,
        staffId: bill.staffId,
        status: 'FINALIZED',
        subtotal,
        totalDiscount,
        tax,
        grandTotal,
        idempotencyKey: bill.id,
        items: bill.items.map((i) => ({
          productId: i.productId,
          barcodeScanned: i.barcodeScanned,
          quantity: i.quantity,
          unitPrice: i.unitPrice,
          discountAmount: i.discountAmount,
          discountType: i.discountType,
        })),
      }),
    });
    return res.ok;
  } catch {
    return false;
  }
}

// ─── Hook ─────────────────────────────────────────────────────────────────────

export function useBillingQueue() {
  const [cart, setCart] = useState<CartItem[]>([]);
  const [pendingBills, setPendingBills] = useState<PendingBill[]>([]);
  const [isSyncing, setIsSyncing] = useState(false);
  const tokenRef = useRef<string>('');

  // Load pending bills from storage on mount
  useEffect(() => {
    loadPendingBills().then(setPendingBills);
  }, []);

  // ── Cart operations ────────────────────────────────────────────────────────

  const addToCart = useCallback((item: CartItem) => {
    setCart((prev) => {
      const existing = prev.find((c) => c.productId === item.productId);
      if (existing) {
        return prev.map((c) =>
          c.productId === item.productId ? { ...c, quantity: c.quantity + 1 } : c
        );
      }
      return [...prev, item];
    });
  }, []);

  const updateQty = useCallback((productId: string, quantity: number, discountAmount?: number) => {
    setCart((prev) =>
      prev.map((c) =>
        c.productId === productId
          ? {
              ...c,
              quantity: Math.max(1, quantity),
              ...(discountAmount !== undefined ? { discountAmount } : {}),
            }
          : c
      )
    );
  }, []);

  const removeFromCart = useCallback((productId: string) => {
    setCart((prev) => prev.filter((c) => c.productId !== productId));
  }, []);

  const clearCart = useCallback(() => {
    setCart([]);
  }, []);

  // ── Sync logic ─────────────────────────────────────────────────────────────

  const drainQueue = useCallback(async (accessToken: string) => {
    if (isSyncing) return;
    setIsSyncing(true);
    tokenRef.current = accessToken;

    const current = await loadPendingBills();
    if (current.length === 0) {
      setIsSyncing(false);
      return;
    }

    const remaining: PendingBill[] = [];
    for (const bill of current) {
      const ok = await submitBill(bill, accessToken);
      if (!ok && bill.retries < 5) {
        remaining.push({ ...bill, retries: bill.retries + 1 });
      }
      // Bills that succeeded or exceeded max retries are dropped
    }

    await savePendingBills(remaining);
    setPendingBills(remaining);
    setIsSyncing(false);
  }, [isSyncing]);

  // NetInfo listener — auto-drain when connectivity is restored
  useEffect(() => {
    const unsubscribe = NetInfo.addEventListener((state: NetInfoState) => {
      if (state.isConnected && pendingBills.length > 0 && !isSyncing) {
        drainQueue(tokenRef.current);
      }
    });
    return () => unsubscribe();
  }, [drainQueue, pendingBills, isSyncing]);

  const syncBills = useCallback(async (accessToken: string, currentCart?: CartItem[], storeId?: string, staffId?: string) => {
    tokenRef.current = accessToken;

    let currentBillHandled = true;

    if (currentCart && currentCart.length > 0 && storeId && staffId) {
      const newBill: PendingBill = {
        id: generateIdempotencyKey(),
        storeId,
        staffId,
        items: currentCart,
        createdAt: new Date().toISOString(),
        retries: 0,
      };

      // Try to submit immediately; if offline, enqueue
      const networkState = await NetInfo.fetch();
      if (networkState.isConnected) {
        const ok = await submitBill(newBill, accessToken);
        if (!ok) {
          const updated = [...pendingBills, newBill];
          await savePendingBills(updated);
          setPendingBills(updated);
        }
      } else {
        const updated = [...pendingBills, newBill];
        await savePendingBills(updated);
        setPendingBills(updated);
      }
    } else if (currentCart && currentCart.length > 0) {
      currentBillHandled = false;
    }

    await drainQueue(accessToken);
    return currentBillHandled;
  }, [pendingBills, drainQueue]);

  return {
    cart,
    addToCart,
    updateQty,
    removeFromCart,
    clearCart,
    syncBills,
    pendingCount: pendingBills.length,
    isSyncing,
  };
}
