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
} from 'react-native';

import { BarcodeScannerModal } from '@/components/barcode-scanner-modal';
import { AppIcon } from '@/components/app-icon';
import {
  ActionButton,
  AppBar,
  FeedbackBanner,
  RoleBadge,
  SectionHeader,
  StatusBadge,
} from '@/components/foundation/primitives';
import { ScreenShell } from '@/components/foundation/screen-shell';
import { ThemedText } from '@/components/themed-text';
import { AppCard } from '@/components/ui/app-card';
import { BottomTabInset } from '@/constants/theme';
import { apiErrorMessage } from '@/contracts/api-error';
import { useAuth } from '@/context/AuthContext';
import { radii, shadows, spacing, touchTarget, typography } from '@/design/tokens';
import { useBillingQueue, type CartItem } from '@/hooks/useBillingQueue';
import { useTheme } from '@/hooks/use-theme';
import {
  refreshProviderBarcodeCatalog,
  resolveMerchantBarcode,
} from '@/services/merchant-barcode';
import { fetchMerchantProviders } from '@/services/merchant-inventory';
import { normalizeBarcode } from '@/utils/barcode';
import { formatCurrency } from '@/utils/formatters';

interface CartRowProps {
  item: CartItem;
  onQtyChange: (id: string, qty: number) => void;
  onRemove: (id: string) => void;
  onDiscount: (item: CartItem) => void;
}

const CartRow = React.memo(({ item, onQtyChange, onRemove, onDiscount }: CartRowProps) => {
  const theme = useTheme();
  const lineSubtotal = Math.max(0, item.unitPrice * item.quantity - item.discountAmount);
  return (
    <View style={[styles.cartRow, { backgroundColor: theme.backgroundElement, borderColor: theme.border }]}>
      <View style={styles.cartRowMeta}>
        <ThemedText style={styles.cartRowName} numberOfLines={1}>{item.name}</ThemedText>
        <ThemedText type="small" themeColor="textSecondary" style={styles.cartRowBarcode}>
          Barcode: {item.barcodeScanned}
          {item.availableStock !== undefined ? ` · ${item.availableStock} in stock` : ''}
        </ThemedText>
        <TouchableOpacity
          onPress={() => onDiscount(item)}
          accessibilityRole="button"
          accessibilityLabel={`Apply discount to ${item.name}`}
          style={styles.discountLink}
        >
          <ThemedText type="smallBold" style={{ color: theme.primary }}>
            {item.discountAmount > 0 ? `Discount: ₹${item.discountAmount.toFixed(2)}` : '+ Add discount'}
          </ThemedText>
        </TouchableOpacity>
      </View>

      <View style={styles.cartRowControls}>
        <TouchableOpacity
          style={[styles.qtyBtn, { backgroundColor: theme.primarySoft }]}
          onPress={() => onQtyChange(item.productId, item.quantity - 1)}
          accessibilityLabel={`Decrease ${item.name} quantity`}
        >
          <AppIcon name="xmark" color={theme.primary} size={16} />
        </TouchableOpacity>
        <ThemedText style={styles.qtyText}>{item.quantity}</ThemedText>
        <TouchableOpacity
          style={[styles.qtyBtn, { backgroundColor: theme.primarySoft }]}
          onPress={() => onQtyChange(item.productId, item.quantity + 1)}
          accessibilityLabel={`Increase ${item.name} quantity`}
        >
          <AppIcon name="sparkle" color={theme.primary} size={16} />
        </TouchableOpacity>
      </View>

      <View style={styles.cartRowPricing}>
        <ThemedText style={[styles.cartRowTotal, { color: theme.success }]}>
          {formatCurrency(lineSubtotal)}
        </ThemedText>
        <TouchableOpacity
          onPress={() => onRemove(item.productId)}
          accessibilityLabel={`Remove ${item.name}`}
          hitSlop={{ top: 8, bottom: 8, left: 8, right: 8 }}
        >
          <AppIcon name="xmark" color={theme.error} size={20} />
        </TouchableOpacity>
      </View>
    </View>
  );
});
CartRow.displayName = 'CartRow';

export default function BillingScreen() {
  const theme = useTheme();
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
  }, [cart, clearCart, session?.access_token, storeId, storeName, syncBills, user]);

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
  const grandTotal = Math.max(0, subtotal - totalDiscount);
  const basePrice = grandTotal / 1.18;
  const gstAmount = grandTotal - basePrice;

  const renderCartItem = useCallback(({ item }: { item: CartItem }) => (
    <CartRow
      item={item}
      onQtyChange={changeQuantity}
      onRemove={removeFromCart}
      onDiscount={openDiscount}
    />
  ), [changeQuantity, openDiscount, removeFromCart]);

  return (
    <ScreenShell
      header={
        <AppBar
          eyebrow="POINT OF SALE"
          title="POS Terminal"
          subtitle={storeLoading ? 'Connecting merchant store…' : storeName ?? 'No store connected'}
          action={<RoleBadge role="merchant" />}
        />
      }
      testID="pos-billing"
    >
      {pendingCount > 0 ? (
        <FeedbackBanner
          tone="warning"
          title={`${pendingCount} offline bill${pendingCount === 1 ? '' : 's'} pending`}
          message="Connect to the network to sync offline POS bills with the backend catalog."
          icon="clock"
        />
      ) : null}

      {lastSyncError ? (
        <FeedbackBanner tone="danger" title="Pending bill needs attention" message={lastSyncError} icon="dispute" />
      ) : null}
      {catalogNotice ? (
        <FeedbackBanner tone="info" title="Barcode catalog" message={catalogNotice} icon="sparkle" />
      ) : null}

      <AppCard style={styles.scanCard}>
        <SectionHeader title="Scan or enter barcode" subtitle="EAN-13, UPC-A, Code 128, or custom SKU" />
        <View style={styles.scanBar}>
          <View style={[styles.searchInputWrapper, { backgroundColor: theme.muted, borderColor: theme.border }]}>
            <AppIcon name="search" color={theme.textSecondary} size={18} />
            <TextInput
              ref={barcodeRef}
              style={[styles.scanInput, { color: theme.text }]}
              value={barcodeInput}
              onChangeText={setBarcodeInput}
              onSubmitEditing={() => void addManualBarcode()}
              placeholder="Scan barcode or type SKU..."
              placeholderTextColor={theme.textSecondary}
              autoCorrect={false}
              autoCapitalize="characters"
              returnKeyType="search"
              accessibilityLabel="Barcode input field"
              blurOnSubmit={false}
            />
            {barcodeInput ? (
              <TouchableOpacity onPress={() => setBarcodeInput('')} accessibilityLabel="Clear barcode">
                <AppIcon name="xmark" color={theme.textSecondary} size={18} />
              </TouchableOpacity>
            ) : null}
          </View>
          <View style={styles.scanActions}>
            <TouchableOpacity
              style={[styles.cameraToggleBtn, { backgroundColor: theme.primarySoft, borderColor: theme.primary }]}
              onPress={() => setScannerVisible(true)}
              accessibilityLabel="Open camera barcode scanner"
              disabled={!storeId}
            >
              <AppIcon name="search" color={theme.primary} size={20} />
              <ThemedText type="smallBold" style={{ color: theme.primary }}>Scan</ThemedText>
            </TouchableOpacity>
            <ActionButton
              label={scanning ? 'Searching...' : 'Add Item'}
              icon="cart"
              onPress={() => void addManualBarcode()}
              disabled={scanning || !storeId}
              style={styles.addBtn}
            />
          </View>
        </View>
      </AppCard>

      {cart.length === 0 ? (
        <AppCard style={styles.emptyCartCard}>
          <AppIcon name="cart" color={theme.textSecondary} size={48} />
          <ThemedText type="title" style={{ textAlign: 'center' }}>Empty POS Cart</ThemedText>
          <ThemedText type="small" themeColor="textSecondary" style={{ textAlign: 'center' }}>
            Scan a barcode or enter SKU above to quickly assemble an in-store order bill.
          </ThemedText>
        </AppCard>
      ) : (
        <View style={styles.cartSection}>
          <SectionHeader title={`Cart (${cart.length} item${cart.length === 1 ? '' : 's'})`} actionLabel="Clear cart" onAction={clearCart} />
          {cart.map((item) => (
            <CartRow
              key={item.productId}
              item={item}
              onQtyChange={changeQuantity}
              onRemove={removeFromCart}
              onDiscount={openDiscount}
            />
          ))}
        </View>
      )}

      {cart.length > 0 ? (
        <AppCard style={styles.billSummaryCard}>
          <SectionHeader title="Bill summary" subtitle="Tax-inclusive MRP calculation" />
          <View style={styles.billRow}>
            <ThemedText type="small" themeColor="textSecondary">Base Price (excl. GST)</ThemedText>
            <ThemedText type="smallBold">{formatCurrency(basePrice)}</ThemedText>
          </View>
          <View style={styles.billRow}>
            <ThemedText type="small" themeColor="textSecondary">Derived GST (18%)</ThemedText>
            <ThemedText type="smallBold">{formatCurrency(gstAmount)}</ThemedText>
          </View>
          {totalDiscount > 0 ? (
            <View style={styles.billRow}>
              <ThemedText type="small" style={{ color: theme.success }}>Discount Applied</ThemedText>
              <ThemedText type="smallBold" style={{ color: theme.success }}>−{formatCurrency(totalDiscount)}</ThemedText>
            </View>
          ) : null}
          <View style={[styles.billRow, styles.grandTotalRow, { borderTopColor: theme.border }]}>
            <ThemedText type="title">Payable Total</ThemedText>
            <ThemedText type="title" style={{ color: theme.primary }}>{formatCurrency(grandTotal)}</ThemedText>
          </View>
          <ActionButton
            label={`Complete Checkout · ${formatCurrency(grandTotal)}`}
            icon="check"
            onPress={checkout}
            disabled={isSyncing}
            style={styles.checkoutBtn}
          />
        </AppCard>
      ) : null}

      <Modal
        visible={discountModalItem !== null}
        transparent
        animationType="slide"
        onRequestClose={() => setDiscountModalItem(null)}
      >
        <View style={styles.modalOverlay}>
          <View style={[styles.modalCard, { backgroundColor: theme.backgroundElement }]}>
            <ThemedText type="title">Apply Discount</ThemedText>
            <ThemedText type="small" themeColor="textSecondary" style={{ marginBottom: spacing.x3 }}>
              {discountModalItem?.name}
            </ThemedText>
            <TextInput
              style={[styles.discountInput, { color: theme.text, borderColor: theme.border, backgroundColor: theme.muted }]}
              value={discountValue}
              onChangeText={setDiscountValue}
              placeholder="Discount amount (₹)"
              placeholderTextColor={theme.textSecondary}
              keyboardType="decimal-pad"
              autoFocus
              accessibilityLabel="Discount amount input"
            />
            <View style={styles.modalActions}>
              <ActionButton
                label="Cancel"
                variant="secondary"
                onPress={() => setDiscountModalItem(null)}
                style={{ flex: 1 }}
              />
              <ActionButton
                label="Apply"
                onPress={applyDiscount}
                style={{ flex: 1 }}
              />
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
    </ScreenShell>
  );
}

const styles = StyleSheet.create({
  scanCard: {
    padding: spacing.x4,
    gap: spacing.x3,
  },
  scanBar: {
    gap: spacing.x3,
  },
  searchInputWrapper: {
    flexDirection: 'row',
    alignItems: 'center',
    borderWidth: 1,
    borderRadius: radii.compact,
    paddingHorizontal: spacing.x3,
    minHeight: touchTarget,
    gap: spacing.x2,
  },
  scanInput: {
    flex: 1,
    ...typography.body,
  },
  scanActions: {
    flexDirection: 'row',
    gap: spacing.x2,
  },
  cameraToggleBtn: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: spacing.x2,
    paddingHorizontal: spacing.x4,
    minHeight: touchTarget,
    borderRadius: radii.compact,
    borderWidth: 1,
  },
  addBtn: {
    flex: 1,
  },
  emptyCartCard: {
    padding: spacing.x6,
    alignItems: 'center',
    justifyContent: 'center',
    gap: spacing.x3,
  },
  cartSection: {
    gap: spacing.x3,
  },
  cartRow: {
    flexDirection: 'row',
    alignItems: 'center',
    borderWidth: 1,
    borderRadius: radii.card,
    padding: spacing.x4,
    gap: spacing.x3,
    ...shadows.card,
  },
  cartRowMeta: {
    flex: 1,
    gap: spacing.x1,
  },
  cartRowName: {
    ...typography.label,
  },
  cartRowBarcode: {
    marginTop: 2,
  },
  discountLink: {
    marginTop: spacing.x1,
    minHeight: 28,
    justifyContent: 'center',
  },
  cartRowControls: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing.x2,
  },
  qtyBtn: {
    width: 36,
    height: 36,
    borderRadius: 18,
    alignItems: 'center',
    justifyContent: 'center',
  },
  qtyText: {
    ...typography.label,
    minWidth: 24,
    textAlign: 'center',
  },
  cartRowPricing: {
    alignItems: 'flex-end',
    gap: spacing.x2,
  },
  cartRowTotal: {
    ...typography.label,
  },
  billSummaryCard: {
    padding: spacing.x4,
    gap: spacing.x3,
  },
  billRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  grandTotalRow: {
    paddingTop: spacing.x3,
    borderTopWidth: StyleSheet.hairlineWidth,
    marginTop: spacing.x1,
  },
  checkoutBtn: {
    marginTop: spacing.x2,
  },
  modalOverlay: {
    flex: 1,
    backgroundColor: 'rgba(0,0,0,0.5)',
    justifyContent: 'flex-end',
  },
  modalCard: {
    borderTopLeftRadius: radii.feature,
    borderTopRightRadius: radii.feature,
    padding: spacing.x6,
    paddingBottom: BottomTabInset + spacing.x6,
    gap: spacing.x3,
  },
  discountInput: {
    borderWidth: 1,
    borderRadius: radii.compact,
    padding: spacing.x3,
    minHeight: touchTarget,
    ...typography.body,
  },
  modalActions: {
    flexDirection: 'row',
    gap: spacing.x3,
    marginTop: spacing.x2,
  },
});
