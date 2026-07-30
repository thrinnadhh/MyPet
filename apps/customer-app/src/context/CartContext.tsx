import AsyncStorage from '@react-native-async-storage/async-storage';
import React, { createContext, useCallback, useContext, useEffect, useState } from 'react';
import { Alert } from 'react-native';

import { type CommerceProduct, type ProductVariant } from '@/services/catalog-data';

export interface CartItem {
  product: CommerceProduct;
  selectedVariant?: ProductVariant;
  quantity: number;
  unitPrice: number;
}

interface CartContextType {
  items: CartItem[];
  providerId: string | null;
  providerName: string | null;
  totalItemsCount: number;
  subtotalAmount: number;
  loading: boolean;
  addToCart: (product: CommerceProduct, variant?: ProductVariant, qty?: number) => boolean;
  removeFromCart: (productId: string, variantId?: string) => void;
  updateQuantity: (productId: string, variantId: string | undefined, qty: number) => void;
  clearCart: () => Promise<void>;
  revalidateCart: () => boolean;
}

const CartContext = createContext<CartContextType | null>(null);
const STORAGE_KEY = 'mypet_cart_v1';

export function CartProvider({ children }: { children: React.ReactNode }) {
  const [items, setItems] = useState<CartItem[]>([]);
  const [providerId, setProviderId] = useState<string | null>(null);
  const [providerName, setProviderName] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  // Load stored cart on mount
  useEffect(() => {
    const loadStoredCart = async () => {
      try {
        const stored = await AsyncStorage.getItem(STORAGE_KEY);
        if (stored) {
          const parsed = JSON.parse(stored);
          if (Array.isArray(parsed.items) && parsed.items.length > 0) {
            setItems(parsed.items);
            setProviderId(parsed.providerId ?? null);
            setProviderName(parsed.providerName ?? null);
          }
        }
      } catch (e) {
        console.warn('Failed to load stored cart', e);
      } finally {
        setLoading(false);
      }
    };
    void loadStoredCart();
  }, []);

  // Sync to AsyncStorage
  const saveCartToStorage = useCallback(async (newItems: CartItem[], pId: string | null, pName: string | null) => {
    try {
      await AsyncStorage.setItem(
        STORAGE_KEY,
        JSON.stringify({ items: newItems, providerId: pId, providerName: pName })
      );
    } catch (e) {
      console.warn('Failed to save cart to storage', e);
    }
  }, []);

  const clearCart = useCallback(async () => {
    setItems([]);
    setProviderId(null);
    setProviderName(null);
    await AsyncStorage.removeItem(STORAGE_KEY);
  }, []);

  const addToCart = useCallback(
    (product: CommerceProduct, variant?: ProductVariant, qty: number = 1): boolean => {
      // 1. Single Provider Constraint Check
      if (providerId && providerId !== product.providerId && items.length > 0) {
        Alert.alert(
          'Replace Cart Items?',
          `Your cart contains items from "${providerName ?? 'another shop'}". Would you like to clear your cart and start a new order from "${product.providerName}"?`,
          [
            { text: 'Cancel', style: 'cancel' },
            {
              text: 'Clear & Continue',
              style: 'destructive',
              onPress: () => {
                const unitPrice = variant ? variant.price : product.price;
                const newItem: CartItem = { product, selectedVariant: variant, quantity: qty, unitPrice };
                const newItems = [newItem];
                setItems(newItems);
                setProviderId(product.providerId);
                setProviderName(product.providerName);
                void saveCartToStorage(newItems, product.providerId, product.providerName);
              },
            },
          ]
        );
        return false;
      }

      // 2. Add or increment item quantity
      const unitPrice = variant ? variant.price : product.price;
      setItems((prev) => {
        const existingIndex = prev.findIndex(
          (i) => i.product.id === product.id && i.selectedVariant?.id === variant?.id
        );
        let next: CartItem[];
        if (existingIndex >= 0) {
          next = [...prev];
          const currentQty = next[existingIndex].quantity;
          const maxStock = variant ? variant.stockCount : product.stockCount;
          next[existingIndex] = {
            ...next[existingIndex],
            quantity: Math.min(currentQty + qty, maxStock),
          };
        } else {
          next = [...prev, { product, selectedVariant: variant, quantity: qty, unitPrice }];
        }
        const nextPId = product.providerId;
        const nextPName = product.providerName;
        setProviderId(nextPId);
        setProviderName(nextPName);
        void saveCartToStorage(next, nextPId, nextPName);
        return next;
      });
      return true;
    },
    [items.length, providerId, providerName, saveCartToStorage]
  );

  const removeFromCart = useCallback(
    (productId: string, variantId?: string) => {
      setItems((prev) => {
        const next = prev.filter(
          (i) => !(i.product.id === productId && i.selectedVariant?.id === variantId)
        );
        const nextPId = next.length > 0 ? providerId : null;
        const nextPName = next.length > 0 ? providerName : null;
        if (next.length === 0) {
          setProviderId(null);
          setProviderName(null);
        }
        void saveCartToStorage(next, nextPId, nextPName);
        return next;
      });
    },
    [providerId, providerName, saveCartToStorage]
  );

  const updateQuantity = useCallback(
    (productId: string, variantId: string | undefined, qty: number) => {
      if (qty <= 0) {
        removeFromCart(productId, variantId);
        return;
      }
      setItems((prev) => {
        const next = prev.map((i) => {
          if (i.product.id === productId && i.selectedVariant?.id === variantId) {
            const maxStock = i.selectedVariant ? i.selectedVariant.stockCount : i.product.stockCount;
            return { ...i, quantity: Math.min(qty, maxStock) };
          }
          return i;
        });
        void saveCartToStorage(next, providerId, providerName);
        return next;
      });
    },
    [providerId, providerName, removeFromCart, saveCartToStorage]
  );

  const revalidateCart = useCallback((): boolean => {
    // Check if any items are out of stock
    let valid = true;
    setItems((prev) => {
      const next = prev.filter((i) => {
        const inStock = i.selectedVariant ? i.selectedVariant.inStock : i.product.inStock;
        if (!inStock) {
          valid = false;
          return false;
        }
        return true;
      });
      void saveCartToStorage(next, next.length > 0 ? providerId : null, next.length > 0 ? providerName : null);
      return next;
    });
    return valid;
  }, [providerId, providerName, saveCartToStorage]);

  const totalItemsCount = items.reduce((acc, i) => acc + i.quantity, 0);
  const subtotalAmount = items.reduce((acc, i) => acc + i.unitPrice * i.quantity, 0);

  return (
    <CartContext.Provider
      value={{
        items,
        providerId,
        providerName,
        totalItemsCount,
        subtotalAmount,
        loading,
        addToCart,
        removeFromCart,
        updateQuantity,
        clearCart,
        revalidateCart,
      }}
    >
      {children}
    </CartContext.Provider>
  );
}

export function useCart() {
  const context = useContext(CartContext);
  if (!context) {
    throw new Error('useCart must be used within CartProvider');
  }
  return context;
}
