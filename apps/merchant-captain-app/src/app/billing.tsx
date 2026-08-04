import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  ActivityIndicator,
  Alert,
  FlatList,
  Modal,
  StyleSheet,
  TextInput,
  TouchableOpacity,
  Vibration,
  View,
  useColorScheme,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { BarcodeScannerModal } from '@/components/barcode-scanner-modal';
import { FeedbackBanner } from '@/components/foundation/primitives';
import { ThemedText } from '@/components/themed-text';
import { BottomTabInset, Colors, Spacing } from '@/constants/theme';
import { apiErrorMessage } from '@/contracts/api-error';
import { useAuth } from '@/context/AuthContext';
import { useBillingQueue, type CartItem } from '@/hooks/useBillingQueue';
import {
  refreshProviderBarcodeCatalog,
  resolveMerchantBarcode,
} from '@/services/merchant-barcode';
import { fetchMerchantProviders } from '@/services/merchant-inventory';
import { normalizeBarcode } from '@/utils/barcode';

const ACCENT_AMBER = '#f59e0b';
const SUCCESS_EMERALD = '#10b981';
const ERROR_RED = '#ba1a1a';
const PRIMARY_BLUE = '#2563eb';

interface CartRowProps {
  item: CartItem;
  colors: typeof Colors.light | typeof Colors.dark;
  onQtyChange: (id: string, qty: number) => void;
  onRemove: (id: string) => void;
  onDiscount: (item: CartItem) => void;
}

const CartRow = React.memo(({ item, colors, onQtyChange, onRemove, onDiscount }: CartRowProps) => {
  const lineTotal = Math.max(0, item.unitPrice * item.quantity - item.discountAmount).toFixed(2);
  return (
    <View style={[styles.cartRow, { backgroundColor: colors.backgroundElement }]}>
      <View style={styles.cartRowMeta}>
        <ThemedText style={styles.cartRowName} numberOfLines={1}>{item.name}</ThemedText>
        <ThemedText style={[styles.cartRowBarcode, { color: colors.textSecondary }]}>
          {item.barcodeScanned}
          {item.availableStock !== undefined ? ` · ${item.availableStock} in stock` : ''}
        </ThemedText>
        <TouchableOpacity
          onPress={() => onDiscount(item)}
          accessibilityRole="button"
          accessibilityLabel={`Apply discount to ${item.name}`}
          style={styles.discountLink}
        >
          <ThemedText style={{ color: PRIMARY_BLUE, fontSize: 12, fontWeight: '700' }}>
            {item.discountAmount > 0 ? `Discount ₹${item.discountAmount.toFixed(2)}` : 'Add discount'}
          </ThemedText>
        </TouchableOpacity>
      </View>

      <View style={styles.cartRowControls}>
        <TouchableOpacity
          style={[styles.qtyBtn, { backgroundColor: colors.backgroundSelected }]}
          onPress={() => onQtyChange(item.productId, item.quantity - 1)}
          accessibilityLabel={`Decrease ${item.name} quantity`}
        >
          <ThemedText style={styles.qtyBtnText}>−</ThemedText>
        </TouchableOpacity>
        <ThemedText style={styles.qtyText}>{item.quantity}</ThemedText>
        <TouchableOpacity
          style={[styles.qtyBtn, { backgroundColor: colors.backgroundSelected }]}
          onPress={() => onQtyChange(item.productId, item.quantity + 1)}
          accessibilityLabel={`Increase ${item.name} quantity`}
        >
          <ThemedText style={styles.qtyBtnText}>+</ThemedText>
        </TouchableOpacity>
      </View>

      <View style={styles.cartRowPricing}>
        <ThemedText style={[styles.cartRowTotal, { color: SUCCESS_EMERALD }]}>₹{lineTotal}</ThemedText>
        <TouchableOpacity
          onPress={() => onRemove(item.productId)}
          accessibilityLabel={`Remove ${item.name}`}
          hitSlop={{ top: 8, bottom: 8, left: 8, right: 8 }}
        >
          <ThemedText style={{ color: ERROR_RED, fontSize: 18, lineHeight: 22 }}>✕</ThemedText>
        </TouchableOpacity>
      </View>
    </View>
  );
});
CartRow.displayName = 'CartRow';

export default function BillingScreen() {
  const scheme = useColorScheme();
  const colors = Colors[scheme === 'dark' ? 'dark' : 'light'];
  const { session, user } = useAuth();
  const {
    cart,
    addToCart,
    updateQty,
    removeFromCart,
    clearCart,
    syncBills,
    pendingCount,
    isSyncing,
    lastSyncError,
  } = useBillingQueue();

  const [barcodeInput, setBarcodeInput] = useState('');
  const [scanning, setScanning] = useState(false);
  const [scannerVisible, setScannerVisible] = useState(false);
  const [storeId, setStoreId] = useState<string | null>(null);
  const [storeName, setStoreName] = useState<string | null>(null);
  const [storeLoading, setStoreLoading] = useState(true);
  const [catalogNotice, setCatalogNotice] = useState<string | null>(null);
  const [discountModalItem, setDiscountModalItem] = useState<CartItem | null>(null);
  const [discountValue, setDiscountValue] = useState('');
  const barcodeRef = useRef<TextInput>(null);

  useEffect(() => {
    let active = true;
    setStoreLoading(true);
    void fetchMerchantProviders()
      .then((providers) => {
        if (!active) return;
        const store = providers.find((provider) =>
          provider.providerType === 'PET_STORE' && provider.fulfillmentType === 'DELIVERY',
        ) ?? providers.find((provider) => provider.fulfillmentType === 'DELIVERY');
        setStoreId(store?.providerId ?? null);
        setStoreName(store?.name ?? null);
      })
      .catch(() => {
        if (!active) return;
        setStoreId(null);
        setStoreName(null);
      })
      .finally(() => {
        if (active) setStoreLoading(false);
      });
    return () => {
      active = false;
    };
  }, []);

  useEffect(() => {
    if (!storeId) return;
    let active = true;
    void refreshProviderBarcodeCatalog(storeId)
      .then((count) => {
        if (active) setCatalogNotice(`Offline barcode catalog ready · ${count} product${count === 1 ? '' : 's'}.`);
      })
      .catch(() => {
        if (active) setCatalogNotice('Offline barcode catalog could not be refreshed. Online scanning remains available.');
      });
    return () => {
      active = false;
    };
  }, [storeId]);

  const processBarcode = useCallback(async (rawBarcode: string) => {
    const barcode = normalizeBarcode(rawBarcode);
    if (!barcode) throw new Error('Enter or scan a barcode first.');
    if (!storeId) throw new Error('No delivery store is available for this merchant account.');

    setScanning(true);
    try {
      const resolution = await resolveMerchantBarcode(storeId, barcode);
      const offering = resolution.offering;
      if (offering.status !== 'ACTIVE') throw new Error(`${offering.name} is not active for sale.`);
      if (offering.stockQuantity <= 0) throw new Error(`${offering.name} is out of stock.`);

      const existing = cart.find((item) => item.productId === offering.offeringId);
      if (existing && existing.quantity >= offering.stockQuantity) {
        throw new Error(`Only ${offering.stockQuantity} unit${offering.stockQuantity === 1 ? '' : 's'} of ${offering.name} are available.`);
      }

      addToCart({
        productId: offering.offeringId,
        name: offering.name,
        barcodeScanned: offering.barcode,
        quantity: 1,
        unitPrice: offering.price,
        discountAmount: 0,
        discountType: 'NONE',
        availableStock: offering.stockQuantity,
      });
      setBarcodeInput('');
      Vibration.vibrate(35);
      setCatalogNotice(
        resolution.source === 'cache'
          ? `Offline catalog used${resolution.cachedAt ? ` · cached ${new Date(resolution.cachedAt).toLocaleString()}` : ''}. Stock is revalidated at checkout.`
          : `${offering.name} added from the live catalog.`,
      );
    } finally {
      setScanning(false);
      barcodeRef.current?.focus();
    }
  }, [addToCart, cart, storeId]);

  const addManualBarcode = useCallback(async () => {
    try {
      await processBarcode(barcodeInput);
    } catch (error) {
      Alert.alert('Barcode not added', apiErrorMessage(error, 'Could not resolve this barcode.'));
    }
  }, [barcodeInput, processBarcode]);

  const changeQuantity = useCallback((productId: string, quantity: number) => {
    const item = cart.find((candidate) => candidate.productId === productId);
    if (!item) return;
    if (quantity < 1) {
      removeFromCart(productId);
      return;
    }
    if (item.availableStock !== undefined && quantity > item.availableStock) {
      Alert.alert('Stock limit', `Only ${item.availableStock} unit${item.availableStock === 1 ? '' : 's'} are available.`);
      return;
    }
    updateQty(productId, quantity);
  }, [cart, removeFromCart, updateQty]);

  const checkout = useCallback(() => {
    if (cart.length === 0) {
      Alert.alert('Empty cart', 'Scan at least one product before checking out.');
      return;
    }
    if (!user?.id) {
      Alert.alert('Sign in required', 'Sign in before creating a bill.');
      return;
    }
    if (!storeId) {
      Alert.alert('Store required', 'No delivery store is available for this merchant account.');
      return;
    }

    Alert.alert(
      'Confirm checkout',
      `Submit bill for ${cart.length} line item${cart.length === 1 ? '' : 's'}${storeName ? ` at ${storeName}` : ''}?`,
      [
        { text: 'Cancel', style: 'cancel' },
        {
          text: 'Checkout',
          onPress: () => {
            void syncBills(session?.access_token ?? '', cart, storeId, user.id).then((result) => {
              if (!result.handled) {
                Alert.alert('Bill not saved', result.message ?? 'The bill was rejected. Review the cart and try again.');
                return;
              }
              clearCart();
              if (result.message) {
                Alert.alert(result.queued ? 'Bill saved offline' : 'Checkout complete', result.message);
              }
            });
          },
        },
      ],
    );
  }, [cart, clearCart, session?.access_token, storeId, storeName, syncBills, user?.id]);

  const openDiscount = useCallback((item: CartItem) => {
    setDiscountModalItem(item);
    setDiscountValue(item.discountAmount > 0 ? String(item.discountAmount) : '');
  }, []);

  const applyDiscount = useCallback(() => {
    if (!discountModalItem) return;
    const value = Number(discountValue || 0);
    const maximum = discountModalItem.unitPrice * discountModalItem.quantity;
    if (!Number.isFinite(value) || value < 0 || value > maximum) {
      Alert.alert('Invalid discount', `Discount must be between ₹0 and ₹${maximum.toFixed(2)}.`);
      return;
    }
    updateQty(discountModalItem.productId, discountModalItem.quantity, value);
    setDiscountModalItem(null);
  }, [discountModalItem, discountValue, updateQty]);

  const subtotal = useMemo(
    () => cart.reduce((sum, item) => sum + item.unitPrice * item.quantity, 0),
    [cart],
  );
  const totalDiscount = useMemo(
    () => cart.reduce((sum, item) => sum + item.discountAmount, 0),
    [cart],
  );
  const taxable = Math.max(0, subtotal - totalDiscount);
  const tax = taxable * 0.18;
  const grandTotal = taxable + tax;

  const renderCartItem = useCallback(({ item }: { item: CartItem }) => (
    <CartRow
      item={item}
      colors={colors}
      onQtyChange={changeQuantity}
      onRemove={removeFromCart}
      onDiscount={openDiscount}
    />
  ), [changeQuantity, colors, openDiscount, removeFromCart]);

  return (
    <SafeAreaView style={[styles.container, { backgroundColor: colors.background }]} edges={['top']}>
      <View style={[styles.header, { borderBottomColor: colors.backgroundElement }]}>
        <View style={styles.headerCopy}>
          <ThemedText style={styles.headerTitle}>Point of Sale</ThemedText>
          <ThemedText style={{ color: colors.textSecondary, fontSize: 12 }}>
            {storeLoading ? 'Loading merchant store…' : storeName ?? 'No delivery store connected'}
          </ThemedText>
        </View>
        {pendingCount > 0 ? (
          <TouchableOpacity
            style={[styles.syncBadge, { backgroundColor: ACCENT_AMBER }]}
            onPress={() => void syncBills(session?.access_token ?? '')}
            disabled={isSyncing}
            accessibilityLabel={`Sync ${pendingCount} pending bills`}
          >
            {isSyncing
              ? <ActivityIndicator size="small" color="#fff" />
              : <ThemedText style={styles.syncBadgeText}>{pendingCount} pending · Sync</ThemedText>}
          </TouchableOpacity>
        ) : null}
      </View>

      {lastSyncError ? (
        <View style={styles.bannerWrap}>
          <FeedbackBanner tone="danger" title="Pending bill needs attention" message={lastSyncError} />
        </View>
      ) : null}
      {catalogNotice ? (
        <View style={styles.bannerWrap}>
          <FeedbackBanner tone="info" title="Barcode catalog" message={catalogNotice} />
        </View>
      ) : null}

      <View style={[styles.scanBar, { backgroundColor: colors.backgroundElement }]}>
        <TextInput
          ref={barcodeRef}
          style={[styles.scanInput, { color: colors.text }]}
          value={barcodeInput}
          onChangeText={setBarcodeInput}
          onSubmitEditing={() => void addManualBarcode()}
          placeholder="Scan barcode or type EAN, UPC, Code 39 or Code 128"
          placeholderTextColor={colors.textSecondary}
          autoCorrect={false}
          autoCapitalize="characters"
          returnKeyType="search"
          accessibilityLabel="Barcode input field"
          blurOnSubmit={false}
        />
        <TouchableOpacity
          style={[styles.cameraToggleBtn, { backgroundColor: colors.backgroundSelected }]}
          onPress={() => setScannerVisible(true)}
          accessibilityLabel="Open camera barcode scanner"
          disabled={!storeId}
        >
          <ThemedText style={{ fontSize: 20 }}>▣</ThemedText>
        </TouchableOpacity>
        <TouchableOpacity
          style={[styles.scanBtn, { backgroundColor: PRIMARY_BLUE }]}
          onPress={() => void addManualBarcode()}
          disabled={scanning || !storeId}
          accessibilityLabel="Add product by barcode"
        >
          {scanning
            ? <ActivityIndicator size="small" color="#fff" />
            : <ThemedText style={styles.scanBtnText}>Add</ThemedText>}
        </TouchableOpacity>
      </View>

      {cart.length === 0 ? (
        <View style={styles.emptyCart}>
          <ThemedText style={styles.emptyCartIcon}>🛒</ThemedText>
          <ThemedText style={[styles.emptyCartText, { color: colors.textSecondary }]}>
            Scan a product barcode to begin
          </ThemedText>
        </View>
      ) : (
        <FlatList
          data={cart}
          renderItem={renderCartItem}
          keyExtractor={(item) => item.productId}
          contentContainerStyle={styles.cartList}
          ItemSeparatorComponent={() => <View style={{ height: Spacing.two }} />}
          showsVerticalScrollIndicator={false}
        />
      )}

      {cart.length > 0 ? (
        <View style={[styles.billSummary, { backgroundColor: colors.backgroundElement, borderTopColor: colors.backgroundSelected }]}>
          <View style={styles.billRow}>
            <ThemedText style={{ color: colors.textSecondary }}>Subtotal</ThemedText>
            <ThemedText>₹{subtotal.toFixed(2)}</ThemedText>
          </View>
          {totalDiscount > 0 ? (
            <View style={styles.billRow}>
              <ThemedText style={{ color: SUCCESS_EMERALD }}>Discount</ThemedText>
              <ThemedText style={{ color: SUCCESS_EMERALD }}>−₹{totalDiscount.toFixed(2)}</ThemedText>
            </View>
          ) : null}
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
            accessibilityLabel="Checkout and sync bill"
            disabled={isSyncing}
          >
            <ThemedText style={styles.checkoutBtnText}>Checkout · ₹{grandTotal.toFixed(2)}</ThemedText>
          </TouchableOpacity>
        </View>
      ) : null}

      <Modal
        visible={discountModalItem !== null}
        transparent
        animationType="slide"
        onRequestClose={() => setDiscountModalItem(null)}
      >
        <View style={styles.modalOverlay}>
          <View style={[styles.modalCard, { backgroundColor: colors.background }]}>
            <ThemedText style={styles.modalTitle}>Apply discount</ThemedText>
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
                accessibilityLabel="Cancel discount"
              >
                <ThemedText>Cancel</ThemedText>
              </TouchableOpacity>
              <TouchableOpacity
                style={[styles.modalBtn, { backgroundColor: SUCCESS_EMERALD }]}
                onPress={applyDiscount}
                accessibilityLabel="Apply discount"
              >
                <ThemedText style={{ color: '#fff', fontWeight: '700' }}>Apply</ThemedText>
              </TouchableOpacity>
            </View>
          </View>
        </View>
      </Modal>

      <BarcodeScannerModal
        visible={scannerVisible}
        title="Scan product for billing"
        instruction="Scan a product from the selected store. Stock and price are loaded from the catalog."
        onClose={() => setScannerVisible(false)}
        onScanned={async (barcode) => {
          await processBarcode(barcode);
        }}
      />
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1 },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: Spacing.three,
    paddingVertical: Spacing.three,
    borderBottomWidth: StyleSheet.hairlineWidth,
    gap: Spacing.two,
  },
  headerCopy: { flex: 1, gap: 2 },
  headerTitle: { fontSize: 20, fontWeight: '700' },
  syncBadge: {
    paddingHorizontal: Spacing.two,
    paddingVertical: 6,
    borderRadius: 20,
    minHeight: 38,
    justifyContent: 'center',
  },
  syncBadgeText: { color: '#fff', fontSize: 12, fontWeight: '700' },
  bannerWrap: { paddingHorizontal: Spacing.three, paddingTop: Spacing.two },
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
  cameraToggleBtn: {
    width: 52,
    height: 52,
    justifyContent: 'center',
    alignItems: 'center',
    borderLeftWidth: StyleSheet.hairlineWidth,
    borderLeftColor: 'rgba(0,0,0,0.1)',
  },
  scanBtn: {
    paddingHorizontal: Spacing.four,
    justifyContent: 'center',
    alignItems: 'center',
    minWidth: 72,
    minHeight: 52,
  },
  scanBtnText: { color: '#fff', fontWeight: '700', fontSize: 15 },
  emptyCart: { flex: 1, justifyContent: 'center', alignItems: 'center', gap: Spacing.three },
  emptyCartIcon: { fontSize: 56 },
  emptyCartText: { fontSize: 15 },
  cartList: { paddingHorizontal: Spacing.three, paddingTop: Spacing.two, paddingBottom: Spacing.three },
  cartRow: {
    flexDirection: 'row',
    alignItems: 'center',
    borderRadius: 12,
    padding: Spacing.three,
    minHeight: 82,
    gap: Spacing.two,
  },
  cartRowMeta: { flex: 1 },
  cartRowName: { fontWeight: '600', fontSize: 14 },
  cartRowBarcode: { fontSize: 11, marginTop: 2 },
  discountLink: { minHeight: 28, justifyContent: 'center', alignSelf: 'flex-start' },
  cartRowControls: { flexDirection: 'row', alignItems: 'center', gap: Spacing.two },
  qtyBtn: { width: 38, height: 38, borderRadius: 8, justifyContent: 'center', alignItems: 'center' },
  qtyBtnText: { fontSize: 18, fontWeight: '700', lineHeight: 22 },
  qtyText: { fontSize: 15, fontWeight: '600', minWidth: 24, textAlign: 'center' },
  cartRowPricing: { alignItems: 'flex-end', gap: 8 },
  cartRowTotal: { fontWeight: '700', fontSize: 14 },
  billSummary: {
    paddingHorizontal: Spacing.three,
    paddingTop: Spacing.three,
    paddingBottom: BottomTabInset + Spacing.three,
    borderTopWidth: StyleSheet.hairlineWidth,
    gap: Spacing.two,
  },
  billRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
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
  modalOverlay: { flex: 1, backgroundColor: 'rgba(0,0,0,0.5)', justifyContent: 'flex-end' },
  modalCard: {
    borderTopLeftRadius: 24,
    borderTopRightRadius: 24,
    padding: Spacing.four,
    paddingBottom: BottomTabInset + Spacing.four,
  },
  modalTitle: { fontSize: 18, fontWeight: '700', marginBottom: Spacing.two },
  discountInput: { borderWidth: 1, borderRadius: 10, padding: Spacing.three, fontSize: 16, minHeight: 52 },
  modalActions: { flexDirection: 'row', gap: Spacing.three, marginTop: Spacing.three },
  modalBtn: {
    flex: 1,
    paddingVertical: Spacing.three,
    borderRadius: 12,
    alignItems: 'center',
    minHeight: 52,
    justifyContent: 'center',
  },
});
