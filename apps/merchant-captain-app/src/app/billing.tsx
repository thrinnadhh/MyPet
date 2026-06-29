import React, { useState, useCallback, useEffect, useRef } from 'react';
import {
  StyleSheet,
  View,
  FlatList,
  TouchableOpacity,
  TextInput,
  Alert,
  ActivityIndicator,
  useColorScheme,
  Platform,
  Modal,
  Vibration,
} from 'react-native';
import { CameraView, useCameraPermissions } from 'expo-camera';
import { SafeAreaView } from 'react-native-safe-area-context';
import { ThemedText } from '@/components/themed-text';
import { Spacing, Colors, BottomTabInset } from '@/constants/theme';
import { useAuth } from '@/context/AuthContext';
import { useBillingQueue, type CartItem } from '@/hooks/useBillingQueue';

const API_BASE_URL = Platform.select({
  android: 'http://10.0.2.2:8080',
  ios: 'http://localhost:8080',
  default: 'http://localhost:8080',
});

const ACCENT_AMBER = '#f59e0b';
const SUCCESS_EMERALD = '#10b981';
const ERROR_RED = '#ba1a1a';
const PRIMARY_BLUE = '#2563eb';

// ─── Types ─────────────────────────────────────────────────────────────────

interface ScannedOffering {
  offeringId: string;
  name: string;
  price: number;
  stockQuantity: number | null;
  barcode: string;
}

interface ProviderSummary {
  providerId: string;
  providerType: string;
  name: string;
}

// ─── Memoized cart item row ──────────────────────────────────────────────────

interface CartRowProps {
  item: CartItem;
  colors: typeof Colors.light | typeof Colors.dark;
  onQtyChange: (id: string, qty: number) => void;
  onRemove: (id: string) => void;
  onDiscountChange: (id: string, discount: number) => void;
}

const CartRow = React.memo(({ item, colors, onQtyChange, onRemove, onDiscountChange }: CartRowProps) => {
  const lineTotal = (item.unitPrice * item.quantity - item.discountAmount).toFixed(2);
  return (
    <View style={[styles.cartRow, { backgroundColor: colors.backgroundElement }]}>
      <View style={styles.cartRowMeta}>
        <ThemedText style={styles.cartRowName} numberOfLines={1}>{item.name}</ThemedText>
        <ThemedText style={[styles.cartRowBarcode, { color: colors.textSecondary }]}>
          {item.barcodeScanned}
        </ThemedText>
      </View>
      <View style={styles.cartRowControls}>
        <TouchableOpacity
          style={[styles.qtyBtn, { backgroundColor: colors.backgroundSelected }]}
          onPress={() => onQtyChange(item.productId, item.quantity - 1)}
          accessibilityLabel="Decrease quantity"
          hitSlop={{ top: 8, bottom: 8, left: 8, right: 8 }}>
          <ThemedText style={styles.qtyBtnText}>−</ThemedText>
        </TouchableOpacity>
        <ThemedText style={styles.qtyText}>{item.quantity}</ThemedText>
        <TouchableOpacity
          style={[styles.qtyBtn, { backgroundColor: colors.backgroundSelected }]}
          onPress={() => onQtyChange(item.productId, item.quantity + 1)}
          accessibilityLabel="Increase quantity"
          hitSlop={{ top: 8, bottom: 8, left: 8, right: 8 }}>
          <ThemedText style={styles.qtyBtnText}>+</ThemedText>
        </TouchableOpacity>
      </View>
      <View style={styles.cartRowPricing}>
        <ThemedText style={[styles.cartRowTotal, { color: SUCCESS_EMERALD }]}>₹{lineTotal}</ThemedText>
        <TouchableOpacity
          onPress={() => onRemove(item.productId)}
          accessibilityLabel="Remove item"
          hitSlop={{ top: 8, bottom: 8, left: 8, right: 8 }}>
          <ThemedText style={{ color: ERROR_RED, fontSize: 18, lineHeight: 22 }}>✕</ThemedText>
        </TouchableOpacity>
      </View>
    </View>
  );
});
CartRow.displayName = 'CartRow';

// ─── Main Screen ──────────────────────────────────────────────────────────────

export default function BillingScreen() {
  const scheme = useColorScheme();
  const colors = Colors[scheme === 'dark' ? 'dark' : 'light'];
  const { session, user } = useAuth();

  // Cart state
  const { cart, addToCart, updateQty, removeFromCart, clearCart, syncBills, pendingCount, isSyncing } = useBillingQueue();

  // Manual barcode input
  const [barcodeInput, setBarcodeInput] = useState('');
  const [scanning, setScanning] = useState(false);
  const [discountModalItem, setDiscountModalItem] = useState<CartItem | null>(null);
  const [discountValue, setDiscountValue] = useState('');
  const barcodeRef = useRef<TextInput>(null);

  // Camera scanner state
  const [permission, requestPermission] = useCameraPermissions();
  const [cameraActive, setCameraActive] = useState(false);
  const [cameraScanned, setCameraScanned] = useState(false);
  const [storeId, setStoreId] = useState<string | null>(null);
  const [storeName, setStoreName] = useState<string | null>(null);

  useEffect(() => {
    let isMounted = true;

    async function fetchStore() {
      if (!user?.id) {
        setStoreId(null);
        setStoreName(null);
        return;
      }

      try {
        const headers: Record<string, string> = {};
        if (session?.access_token) headers.Authorization = `Bearer ${session.access_token}`;

        const res = await fetch(`${API_BASE_URL}/api/v1/providers?ownerUserId=${user.id}`, { headers });
        if (!res.ok) throw new Error('Provider lookup failed');

        const providers: ProviderSummary[] = await res.json();
        const store = providers.find((provider) => provider.providerType === 'PET_STORE') ?? providers[0];
        if (isMounted) {
          setStoreId(store?.providerId ?? null);
          setStoreName(store?.name ?? null);
        }
      } catch {
        if (isMounted) {
          setStoreId(null);
          setStoreName(null);
        }
      }
    }

    fetchStore();
    return () => {
      isMounted = false;
    };
  }, [session?.access_token, user?.id]);

  // ── Barcode resolution ────────────────────────────────────────────────────

  const resolveBarcode = useCallback(async (barcode: string) => {
    if (!barcode.trim()) return;
    const trimmed = barcode.trim();

    // Check if already in cart — just bump qty
    const existing = cart.find(c => c.barcodeScanned === trimmed);
    if (existing) {
      updateQty(existing.productId, existing.quantity + 1);
      setBarcodeInput('');
      Vibration.vibrate(30);
      return;
    }

    setScanning(true);
    try {
      const headers: Record<string, string> = {
        'Content-Type': 'application/json',
        'X-User-Role': 'MERCHANT',
      };
      if (user?.id) headers['X-User-Id'] = user.id;
      if (session?.access_token) headers['Authorization'] = `Bearer ${session.access_token}`;

      const storeQuery = storeId ? `?storeId=${storeId}` : '';
      const res = await fetch(`${API_BASE_URL}/api/v1/catalog/offerings/by-barcode/${encodeURIComponent(trimmed)}${storeQuery}`, { headers });
      if (!res.ok) {
        const body = await res.json().catch(() => ({}));
        Alert.alert('Not Found', body.error ?? `Barcode ${trimmed} not found in catalog`);
        return;
      }
      const offering: ScannedOffering = await res.json();
      addToCart({
        productId: offering.offeringId,
        name: offering.name,
        barcodeScanned: trimmed,
        quantity: 1,
        unitPrice: offering.price,
        discountAmount: 0,
        discountType: 'NONE',
      });
      Vibration.vibrate(30);
      setBarcodeInput('');
    } catch (e) {
      Alert.alert('Network Error', 'Could not resolve barcode. Check connection.');
    } finally {
      setScanning(false);
      barcodeRef.current?.focus();
    }
  }, [cart, session, storeId, user, addToCart, updateQty]);

  // ── Checkout ──────────────────────────────────────────────────────────────

  const checkout = useCallback(async () => {
    if (cart.length === 0) {
      Alert.alert('Empty Cart', 'Scan at least one product before checking out.');
      return;
    }
    if (!user?.id) {
      Alert.alert('Sign In Required', 'Sign in before creating a bill.');
      return;
    }
    if (!storeId) {
      Alert.alert('Store Required', 'No store is available for this merchant account.');
      return;
    }

    Alert.alert(
      'Confirm Checkout',
      `Submit bill for ${cart.length} item(s)${storeName ? ` at ${storeName}` : ''}?`,
      [
        { text: 'Cancel', style: 'cancel' },
        {
          text: 'Checkout',
          onPress: async () => {
            const handled = await syncBills(session?.access_token ?? '', cart, storeId, user.id);
            if (handled) {
              clearCart();
            } else {
              Alert.alert('Bill Not Saved', 'The bill was not submitted or queued. Please try again.');
            }
          },
        },
      ]
    );
  }, [cart, session, storeId, storeName, user, syncBills, clearCart]);

  // ── Discount modal ────────────────────────────────────────────────────────

  const openDiscount = useCallback((item: CartItem) => {
    setDiscountModalItem(item);
    setDiscountValue(item.discountAmount > 0 ? String(item.discountAmount) : '');
  }, []);

  const applyDiscount = useCallback(() => {
    if (!discountModalItem) return;
    const val = parseFloat(discountValue) || 0;
    const maxDiscount = discountModalItem.unitPrice * discountModalItem.quantity;
    if (val < 0 || val > maxDiscount) {
      Alert.alert('Invalid Discount', `Discount must be between ₹0 and ₹${maxDiscount.toFixed(2)}`);
      return;
    }
    updateQty(discountModalItem.productId, discountModalItem.quantity, val);
    setDiscountModalItem(null);
  }, [discountModalItem, discountValue, updateQty]);

  // ── Totals ────────────────────────────────────────────────────────────────

  const subtotal = cart.reduce((s, i) => s + i.unitPrice * i.quantity, 0);
  const totalDiscount = cart.reduce((s, i) => s + i.discountAmount, 0);
  const taxable = subtotal - totalDiscount;
  const tax = taxable * 0.18;
  const grandTotal = taxable + tax;

  // ── Render helpers ────────────────────────────────────────────────────────

  const renderCartItem = useCallback(({ item }: { item: CartItem }) => (
    <CartRow
      item={item}
      colors={colors}
      onQtyChange={(id, qty) => updateQty(id, Math.max(1, qty))}
      onRemove={removeFromCart}
      onDiscountChange={(id, disc) => {
        const found = cart.find(c => c.productId === id);
        if (found) updateQty(id, found.quantity, disc);
      }}
    />
  ), [colors, cart, updateQty, removeFromCart]);

  const keyExtractor = useCallback((item: CartItem) => item.productId, []);

  return (
    <SafeAreaView style={[styles.container, { backgroundColor: colors.background }]} edges={['top']}>
      {/* ── Header ── */}
      <View style={[styles.header, { borderBottomColor: colors.backgroundElement }]}>
        <ThemedText style={styles.headerTitle}>Point of Sale</ThemedText>
        {pendingCount > 0 && (
          <TouchableOpacity
            style={[styles.syncBadge, { backgroundColor: ACCENT_AMBER }]}
            onPress={() => syncBills(session?.access_token ?? '')}
            disabled={isSyncing}
            accessibilityLabel={`Sync ${pendingCount} pending bills`}>
            {isSyncing
              ? <ActivityIndicator size="small" color="#fff" />
              : <ThemedText style={styles.syncBadgeText}>{pendingCount} pending — Sync</ThemedText>}
          </TouchableOpacity>
        )}
      </View>

      {/* ── Barcode Input ── */}
      <View style={[styles.scanBar, { backgroundColor: colors.backgroundElement }]}>
        <TextInput
          ref={barcodeRef}
          style={[styles.scanInput, { color: colors.text }]}
          value={barcodeInput}
          onChangeText={setBarcodeInput}
          onSubmitEditing={() => resolveBarcode(barcodeInput)}
          placeholder="Scan barcode or type EAN/UPC…"
          placeholderTextColor={colors.textSecondary}
          autoCorrect={false}
          autoCapitalize="none"
          returnKeyType="search"
          keyboardType="number-pad"
          accessibilityLabel="Barcode input field"
          blurOnSubmit={false}
        />
        <TouchableOpacity
          style={[styles.cameraToggleBtn, { backgroundColor: colors.backgroundSelected }]}
          onPress={async () => {
            if (!permission || !permission.granted) {
              const res = await requestPermission();
              if (!res.granted) {
                Alert.alert('Permission Required', 'Camera access is required to scan barcodes.');
                return;
              }
            }
            setCameraScanned(false);
            setCameraActive(true);
          }}
          accessibilityLabel="Open camera scanner">
          <ThemedText style={{ fontSize: 20 }}>📷</ThemedText>
        </TouchableOpacity>
        <TouchableOpacity
          style={[styles.scanBtn, { backgroundColor: PRIMARY_BLUE }]}
          onPress={() => resolveBarcode(barcodeInput)}
          disabled={scanning}
          accessibilityLabel="Add product by barcode">
          {scanning
            ? <ActivityIndicator size="small" color="#fff" />
            : <ThemedText style={styles.scanBtnText}>Add</ThemedText>}
        </TouchableOpacity>
      </View>

      {/* ── Cart ── */}
      {cart.length === 0 ? (
        <View style={styles.emptyCart}>
          <ThemedText style={[styles.emptyCartIcon]}>🛒</ThemedText>
          <ThemedText style={[styles.emptyCartText, { color: colors.textSecondary }]}>
            Scan a product barcode to begin
          </ThemedText>
        </View>
      ) : (
        <FlatList
          data={cart}
          renderItem={renderCartItem}
          keyExtractor={keyExtractor}
          contentContainerStyle={{ paddingHorizontal: Spacing.three, paddingTop: Spacing.two }}
          ItemSeparatorComponent={() => <View style={{ height: Spacing.two }} />}
          showsVerticalScrollIndicator={false}
          getItemLayout={(_, index) => ({ length: 80, offset: 80 * index + Spacing.two * index, index })}
        />
      )}

      {/* ── Bill Summary & Checkout ── */}
      {cart.length > 0 && (
        <View style={[styles.billSummary, { backgroundColor: colors.backgroundElement, borderTopColor: colors.backgroundSelected }]}>
          <View style={styles.billRow}>
            <ThemedText style={{ color: colors.textSecondary }}>Subtotal</ThemedText>
            <ThemedText>₹{subtotal.toFixed(2)}</ThemedText>
          </View>
          {totalDiscount > 0 && (
            <View style={styles.billRow}>
              <ThemedText style={{ color: SUCCESS_EMERALD }}>Discount</ThemedText>
              <ThemedText style={{ color: SUCCESS_EMERALD }}>−₹{totalDiscount.toFixed(2)}</ThemedText>
            </View>
          )}
          <View style={styles.billRow}>
            <ThemedText style={{ color: colors.textSecondary }}>GST (18%)</ThemedText>
            <ThemedText>₹{tax.toFixed(2)}</ThemedText>
          </View>
          <View style={[styles.billRow, styles.grandTotalRow]}>
            <ThemedText style={styles.grandTotalLabel}>Grand Total</ThemedText>
            <ThemedText style={[styles.grandTotalAmount, { color: PRIMARY_BLUE }]}>₹{grandTotal.toFixed(2)}</ThemedText>
          </View>
          <TouchableOpacity
            style={[styles.checkoutBtn, { backgroundColor: SUCCESS_EMERALD }]}
            onPress={checkout}
            accessibilityLabel="Checkout and sync bill">
            <ThemedText style={styles.checkoutBtnText}>Checkout  ₹{grandTotal.toFixed(2)}</ThemedText>
          </TouchableOpacity>
        </View>
      )}

      {/* ── Discount Modal ── */}
      <Modal
        visible={discountModalItem !== null}
        transparent
        animationType="slide"
        onRequestClose={() => setDiscountModalItem(null)}>
        <View style={styles.modalOverlay}>
          <View style={[styles.modalCard, { backgroundColor: colors.background }]}>
            <ThemedText style={styles.modalTitle}>Apply Discount</ThemedText>
            <ThemedText style={{ color: colors.textSecondary, marginBottom: Spacing.three }}>
              {discountModalItem?.name}
            </ThemedText>
            <TextInput
              style={[styles.discountInput, { color: colors.text, borderColor: colors.backgroundSelected }]}
              value={discountValue}
              onChangeText={setDiscountValue}
              placeholder="Discount amount (₹)"
              placeholderTextColor={colors.textSecondary}
              keyboardType="decimal-pad"
              autoFocus
              accessibilityLabel="Discount amount input"
            />
            <View style={styles.modalActions}>
              <TouchableOpacity
                style={[styles.modalBtn, { backgroundColor: colors.backgroundElement }]}
                onPress={() => setDiscountModalItem(null)}
                accessibilityLabel="Cancel discount">
                <ThemedText>Cancel</ThemedText>
              </TouchableOpacity>
              <TouchableOpacity
                style={[styles.modalBtn, { backgroundColor: SUCCESS_EMERALD }]}
                onPress={applyDiscount}
                accessibilityLabel="Apply discount">
                <ThemedText style={{ color: '#fff', fontWeight: '600' }}>Apply</ThemedText>
              </TouchableOpacity>
            </View>
          </View>
        </View>
      </Modal>
      {/* ── Barcode Scanner Modal ── */}
      <Modal
        visible={cameraActive}
        animationType="slide"
        onRequestClose={() => setCameraActive(false)}>
        <View style={styles.scannerOverlay}>
          {permission?.granted ? (
            <CameraView
              style={StyleSheet.absoluteFill}
              barcodeScannerSettings={{
                barcodeTypes: ['ean13', 'ean8', 'upc_a', 'upc_e', 'code128', 'code39'],
              }}
              onBarcodeScanned={cameraScanned ? undefined : async ({ data }) => {
                setCameraScanned(true);
                setCameraActive(false);
                Vibration.vibrate(50);
                if (data) {
                  await resolveBarcode(data);
                }
              }}
            />
          ) : (
            <View style={styles.scannerNoPermission}>
              <ThemedText>No camera permission granted.</ThemedText>
            </View>
          )}

          {/* Scanner Overlay Frame */}
          <View style={styles.scannerOverlayFrame}>
            <View style={styles.scanTargetFrame} />
            <ThemedText style={styles.scannerTipText}>
              Align barcode inside the box to scan
            </ThemedText>
          </View>

          {/* Close Button */}
          <TouchableOpacity
            style={styles.scannerCloseBtn}
            onPress={() => setCameraActive(false)}
            accessibilityLabel="Close scanner">
            <ThemedText style={styles.scannerCloseText}>Close</ThemedText>
          </TouchableOpacity>
        </View>
      </Modal>
    </SafeAreaView>
  );
}

// ─── Styles ──────────────────────────────────────────────────────────────────

const styles = StyleSheet.create({
  container: { flex: 1 },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: Spacing.three,
    paddingVertical: Spacing.three,
    borderBottomWidth: StyleSheet.hairlineWidth,
  },
  headerTitle: { fontSize: 20, fontWeight: '700' },
  syncBadge: {
    paddingHorizontal: Spacing.two,
    paddingVertical: 6,
    borderRadius: 20,
    minHeight: 32,
    justifyContent: 'center',
  },
  syncBadgeText: { color: '#fff', fontSize: 12, fontWeight: '600' },
  scanBar: {
    flexDirection: 'row',
    margin: Spacing.three,
    borderRadius: 12,
    overflow: 'hidden',
  },
  scanInput: {
    flex: 1,
    paddingHorizontal: Spacing.three,
    paddingVertical: Spacing.three,
    fontSize: 15,
    minHeight: 52,
  },
  scanBtn: {
    paddingHorizontal: Spacing.four,
    justifyContent: 'center',
    alignItems: 'center',
    minWidth: 72,
    minHeight: 52,
  },
  scanBtnText: { color: '#fff', fontWeight: '700', fontSize: 15 },
  emptyCart: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    gap: Spacing.three,
  },
  emptyCartIcon: { fontSize: 56 },
  emptyCartText: { fontSize: 15 },
  cartRow: {
    flexDirection: 'row',
    alignItems: 'center',
    borderRadius: 12,
    padding: Spacing.three,
    minHeight: 72,
    gap: Spacing.two,
  },
  cartRowMeta: { flex: 1 },
  cartRowName: { fontWeight: '600', fontSize: 14 },
  cartRowBarcode: { fontSize: 11, marginTop: 2 },
  cartRowControls: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.two,
  },
  qtyBtn: {
    width: 34,
    height: 34,
    borderRadius: 8,
    justifyContent: 'center',
    alignItems: 'center',
  },
  qtyBtnText: { fontSize: 18, fontWeight: '700', lineHeight: 22 },
  qtyText: { fontSize: 15, fontWeight: '600', minWidth: 24, textAlign: 'center' },
  cartRowPricing: {
    alignItems: 'flex-end',
    gap: 6,
  },
  cartRowTotal: { fontWeight: '700', fontSize: 14 },
  billSummary: {
    paddingHorizontal: Spacing.three,
    paddingTop: Spacing.three,
    paddingBottom: BottomTabInset + Spacing.three,
    borderTopWidth: StyleSheet.hairlineWidth,
    gap: Spacing.two,
  },
  billRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  grandTotalRow: { marginTop: Spacing.two },
  grandTotalLabel: { fontWeight: '700', fontSize: 17 },
  grandTotalAmount: { fontWeight: '800', fontSize: 22 },
  checkoutBtn: {
    marginTop: Spacing.two,
    paddingVertical: Spacing.three,
    borderRadius: 14,
    alignItems: 'center',
    minHeight: 54,
    justifyContent: 'center',
  },
  checkoutBtnText: { color: '#fff', fontWeight: '800', fontSize: 17 },
  modalOverlay: {
    flex: 1,
    backgroundColor: 'rgba(0,0,0,0.5)',
    justifyContent: 'flex-end',
  },
  modalCard: {
    borderTopLeftRadius: 24,
    borderTopRightRadius: 24,
    padding: Spacing.four,
    paddingBottom: BottomTabInset + Spacing.four,
  },
  modalTitle: { fontSize: 18, fontWeight: '700', marginBottom: Spacing.two },
  discountInput: {
    borderWidth: 1,
    borderRadius: 10,
    padding: Spacing.three,
    fontSize: 16,
    minHeight: 52,
  },
  modalActions: {
    flexDirection: 'row',
    gap: Spacing.three,
    marginTop: Spacing.three,
  },
  modalBtn: {
    flex: 1,
    paddingVertical: Spacing.three,
    borderRadius: 12,
    alignItems: 'center',
    minHeight: 52,
    justifyContent: 'center',
  },
  cameraToggleBtn: {
    width: 52,
    height: 52,
    justifyContent: 'center',
    alignItems: 'center',
    borderLeftWidth: StyleSheet.hairlineWidth,
    borderLeftColor: 'rgba(0,0,0,0.1)',
  },
  scannerOverlay: {
    flex: 1,
    backgroundColor: '#000',
    justifyContent: 'center',
    alignItems: 'center',
  },
  scannerNoPermission: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
  },
  scannerOverlayFrame: {
    ...StyleSheet.absoluteFill,
    justifyContent: 'center',
    alignItems: 'center',
    backgroundColor: 'rgba(0,0,0,0.4)',
  },
  scanTargetFrame: {
    width: 280,
    height: 180,
    borderWidth: 2,
    borderColor: '#f59e0b',
    borderRadius: 8,
    backgroundColor: 'transparent',
  },
  scannerTipText: {
    color: '#fff',
    fontSize: 14,
    marginTop: Spacing.four,
    textAlign: 'center',
    textShadowColor: 'rgba(0, 0, 0, 0.75)',
    textShadowOffset: { width: -1, height: 1 },
    textShadowRadius: 10,
  },
  scannerCloseBtn: {
    position: 'absolute',
    bottom: 50,
    paddingHorizontal: Spacing.five,
    paddingVertical: Spacing.three,
    borderRadius: 24,
    backgroundColor: 'rgba(255,255,255,0.9)',
    minHeight: 48,
    justifyContent: 'center',
  },
  scannerCloseText: {
    color: '#000',
    fontWeight: '700',
    fontSize: 16,
  },
});
