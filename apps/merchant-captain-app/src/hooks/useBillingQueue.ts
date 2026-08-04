/**
 * Offline-first POS billing queue.
 *
 * Cart data remains in memory for the active sale. Bills that cannot reach the
 * server are persisted until a later authenticated sync. Deterministic 4xx
 * rejections are never reported as successful checkouts.
 */

import AsyncStorage from '@react-native-async-storage/async-storage';
import NetInfo, { type NetInfoState } from '@react-native-community/netinfo';
import { useCallback, useEffect, useRef, useState } from 'react';

import { appConfig } from '@/utils/app-config';

const PENDING_BILLS_KEY = '@pawsnearme:pending_bills';

export interface CartItem {
  productId: string;
  name: string;
  barcodeScanned: string;
  quantity: number;
  unitPrice: number;
  discountAmount: number;
  discountType: 'NONE' | 'FLAT' | 'PERCENT';
  availableStock?: number;
}

interface PendingBill {
  id: string;
  storeId: string;
  staffId: string;
  items: CartItem[];
  createdAt: string;
  retries: number;
  lastError?: string;
}

type SubmitBillResult =
  | { kind: 'submitted'; message?: string }
  | { kind: 'retryable'; message: string }
  | { kind: 'rejected'; message: string };

export type BillingSyncResult = {
  handled: boolean;
  queued: boolean;
  message?: string;
};

function generateIdempotencyKey(): string {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (character) => {
    const random = (Math.random() * 16) | 0;
    const value = character === 'x' ? random : (random & 0x3) | 0x8;
    return value.toString(16);
  });
}

function responseMessage(raw: unknown, fallback: string): string {
  if (!raw || typeof raw !== 'object') return fallback;
  const value = raw as Record<string, unknown>;
  if (typeof value.message === 'string' && value.message.trim()) return value.message.trim();
  if (typeof value.error === 'string' && value.error.trim()) return value.error.trim();
  return fallback;
}

async function loadPendingBills(): Promise<PendingBill[]> {
  try {
    const raw = await AsyncStorage.getItem(PENDING_BILLS_KEY);
    if (!raw) return [];
    const parsed = JSON.parse(raw);
    return Array.isArray(parsed) ? (parsed as PendingBill[]) : [];
  } catch {
    return [];
  }
}

async function savePendingBills(bills: PendingBill[]): Promise<void> {
  await AsyncStorage.setItem(PENDING_BILLS_KEY, JSON.stringify(bills));
}

async function enqueueBill(bill: PendingBill): Promise<PendingBill[]> {
  const current = await loadPendingBills();
  if (current.some((pending) => pending.id === bill.id)) return current;
  const updated = [...current, bill];
  await savePendingBills(updated);
  return updated;
}

async function submitBill(bill: PendingBill, accessToken: string): Promise<SubmitBillResult> {
  const subtotal = bill.items.reduce((sum, item) => sum + item.unitPrice * item.quantity, 0);
  const totalDiscount = bill.items.reduce((sum, item) => sum + item.discountAmount, 0);
  const tax = Math.max(0, subtotal - totalDiscount) * 0.18;
  const grandTotal = Math.max(0, subtotal - totalDiscount + tax);

  try {
    const response = await fetch(`${appConfig.apiBaseUrl}/api/v1/catalog/bills`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
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
        items: bill.items.map((item) => ({
          productId: item.productId,
          barcodeScanned: item.barcodeScanned,
          quantity: item.quantity,
          unitPrice: item.unitPrice,
          discountAmount: item.discountAmount,
          discountType: item.discountType,
        })),
      }),
    });

    const raw = await response.json().catch(() => null) as unknown;
    if (response.ok) {
      const failedItems = raw && typeof raw === 'object'
        ? (raw as { failedItems?: unknown }).failedItems
        : undefined;
      const failedCount = Array.isArray(failedItems) ? failedItems.length : 0;
      return {
        kind: 'submitted',
        message: failedCount > 0 ? `Bill saved with ${failedCount} rejected item(s). Review inventory.` : undefined,
      };
    }

    const message = responseMessage(raw, `Billing request failed (${response.status}).`);
    if (response.status === 408 || response.status === 429 || response.status >= 500) {
      return { kind: 'retryable', message };
    }
    return { kind: 'rejected', message };
  } catch {
    return { kind: 'retryable', message: 'Could not reach the billing service.' };
  }
}

export function useBillingQueue() {
  const [cart, setCart] = useState<CartItem[]>([]);
  const [pendingBills, setPendingBills] = useState<PendingBill[]>([]);
  const [isSyncing, setIsSyncing] = useState(false);
  const [lastSyncError, setLastSyncError] = useState<string | null>(null);
  const tokenRef = useRef('');
  const syncingRef = useRef(false);

  useEffect(() => {
    void loadPendingBills().then(setPendingBills);
  }, []);

  const addToCart = useCallback((item: CartItem) => {
    setCart((current) => {
      const existing = current.find((candidate) => candidate.productId === item.productId);
      if (!existing) return [...current, item];
      const maxQuantity = existing.availableStock ?? item.availableStock ?? Number.MAX_SAFE_INTEGER;
      return current.map((candidate) =>
        candidate.productId === item.productId
          ? {
              ...candidate,
              availableStock: item.availableStock ?? candidate.availableStock,
              quantity: Math.min(candidate.quantity + 1, maxQuantity),
            }
          : candidate,
      );
    });
  }, []);

  const updateQty = useCallback((productId: string, quantity: number, discountAmount?: number) => {
    setCart((current) =>
      current.map((item) => {
        if (item.productId !== productId) return item;
        const maximum = item.availableStock ?? Number.MAX_SAFE_INTEGER;
        return {
          ...item,
          quantity: Math.max(1, Math.min(quantity, maximum)),
          ...(discountAmount !== undefined ? { discountAmount } : {}),
        };
      }),
    );
  }, []);

  const removeFromCart = useCallback((productId: string) => {
    setCart((current) => current.filter((item) => item.productId !== productId));
  }, []);

  const clearCart = useCallback(() => setCart([]), []);

  const drainQueue = useCallback(async (accessToken: string) => {
    if (syncingRef.current) return;
    syncingRef.current = true;
    setIsSyncing(true);
    tokenRef.current = accessToken;
    setLastSyncError(null);

    try {
      const current = await loadPendingBills();
      const remaining: PendingBill[] = [];
      let latestError: string | null = null;

      for (const bill of current) {
        const result = await submitBill(bill, accessToken);
        if (result.kind !== 'submitted') {
          latestError = result.message;
          remaining.push({
            ...bill,
            retries: bill.retries + 1,
            lastError: result.message,
          });
        }
      }

      await savePendingBills(remaining);
      setPendingBills(remaining);
      setLastSyncError(latestError);
    } catch {
      setLastSyncError('Pending bills could not be read or saved on this device.');
    } finally {
      syncingRef.current = false;
      setIsSyncing(false);
    }
  }, []);

  useEffect(() => {
    const unsubscribe = NetInfo.addEventListener((state: NetInfoState) => {
      if (state.isConnected && pendingBills.length > 0 && !syncingRef.current) {
        void drainQueue(tokenRef.current);
      }
    });
    return unsubscribe;
  }, [drainQueue, pendingBills.length]);

  const syncBills = useCallback(async (
    accessToken: string,
    currentCart?: CartItem[],
    storeId?: string,
    staffId?: string,
  ): Promise<BillingSyncResult> => {
    tokenRef.current = accessToken;
    setLastSyncError(null);

    if (!currentCart || currentCart.length === 0) {
      await drainQueue(accessToken);
      return { handled: true, queued: false };
    }
    if (!storeId || !staffId) {
      return { handled: false, queued: false, message: 'Store and staff identity are required.' };
    }

    const bill: PendingBill = {
      id: generateIdempotencyKey(),
      storeId,
      staffId,
      items: currentCart,
      createdAt: new Date().toISOString(),
      retries: 0,
    };

    const networkState = await NetInfo.fetch();
    if (networkState.isConnected) {
      const result = await submitBill(bill, accessToken);
      if (result.kind === 'submitted') {
        await drainQueue(accessToken);
        return { handled: true, queued: false, message: result.message };
      }
      if (result.kind === 'rejected') {
        setLastSyncError(result.message);
        return { handled: false, queued: false, message: result.message };
      }
      bill.lastError = result.message;
    }

    try {
      const updated = await enqueueBill(bill);
      setPendingBills(updated);
      return {
        handled: true,
        queued: true,
        message: networkState.isConnected
          ? 'Billing service is unavailable. The bill is saved on this device for retry.'
          : 'Offline: the bill is saved on this device and will sync when connected.',
      };
    } catch {
      const message = 'The bill could not be submitted or saved on this device.';
      setLastSyncError(message);
      return { handled: false, queued: false, message };
    }
  }, [drainQueue]);

  return {
    cart,
    addToCart,
    updateQty,
    removeFromCart,
    clearCart,
    syncBills,
    pendingCount: pendingBills.length,
    isSyncing,
    lastSyncError,
  };
}
