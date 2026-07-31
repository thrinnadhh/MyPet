import React, { useState, useEffect, useCallback } from 'react';
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
  ScrollView,
  Modal
} from 'react-native';
import DateTimePicker from '@react-native-community/datetimepicker';
import { SafeAreaView } from 'react-native-safe-area-context';
import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { Spacing, Colors } from '@/constants/theme';
import { useAuth } from '@/context/AuthContext';
import { apiClient } from '@/services/api-client';
import { appConfig } from '@/utils/app-config';

// Stitch Design System Theme Colors
const PRIMARY_BLUE = '#2563eb';
const SUCCESS_EMERALD = '#10b981';
const WARNING_AMBER = '#f59e0b';
const ERROR_RED = '#ba1a1a';

interface Offering {
  offeringId?: string;
  providerId: string;
  name: string;
  description?: string;
  category?: string;
  price: number;
  imageUrl?: string;
  status: 'ACTIVE' | 'INACTIVE' | 'OUT_OF_STOCK';
  stockQuantity?: number;
  sku?: string;
  durationMinutes?: number;
}

interface Slot {
  slotId?: string;
  offeringId: string;
  slotStart: string; // ISO String
  slotEnd: string; // ISO String
  status: 'AVAILABLE' | 'HELD' | 'BOOKED' | 'BLOCKED';
}

/**
 * DEMO_PROVIDERS: Placeholder provider data used until Supabase Auth supplies
 * real session-based provider IDs. Replace with auth context in Sprint 3.
 */
const DEMO_PROVIDERS = [
  {
    id: 'e1b07384-d113-4e4e-9c8e-3d8e3d8e3d8e',
    label: '🏬 Pet Store',
    fulfillmentType: 'DELIVERY' as const,
  },
  {
    id: 'e2b07384-d113-4e4e-9c8e-3d8e3d8e3d8e',
    label: '✂️ Groomer',
    fulfillmentType: 'APPOINTMENT' as const,
  },
];

const OFFLINE_MOCK_OFFERINGS: Record<string, Offering[]> = {
  // e1b07384-d113-4e4e-9c8e-3d8e3d8e3d8e (PET_STORE - DELIVERY)
  'e1b07384-d113-4e4e-9c8e-3d8e3d8e3d8e': [
    {
      offeringId: 'off-1',
      providerId: 'e1b07384-d113-4e4e-9c8e-3d8e3d8e3d8e',
      name: 'Premium Salmon Kibble 5kg',
      description: 'High protein nutrition for adult dogs.',
      category: 'Food',
      price: 45.99,
      status: 'ACTIVE',
      stockQuantity: 15,
      sku: 'KIB-SAL-5KG',
    },
    {
      offeringId: 'off-2',
      providerId: 'e1b07384-d113-4e4e-9c8e-3d8e3d8e3d8e',
      name: 'Orthopedic Dog Bed (Medium)',
      description: 'Memory foam support for joint pain relief.',
      category: 'Furniture',
      price: 79.99,
      status: 'ACTIVE',
      stockQuantity: 4,
      sku: 'BED-ORTHO-M',
    }
  ],
  // e2b07384-d113-4e4e-9c8e-3d8e3d8e3d8e (GROOMING_CENTER - APPOINTMENT)
  'e2b07384-d113-4e4e-9c8e-3d8e3d8e3d8e': [
    {
      offeringId: 'off-3',
      providerId: 'e2b07384-d113-4e4e-9c8e-3d8e3d8e3d8e',
      name: 'Premium Bath & Brush',
      description: 'Hypoallergenic organic shampoo treatment.',
      category: 'Spa',
      price: 35.00,
      status: 'ACTIVE',
      durationMinutes: 45,
    },
    {
      offeringId: 'off-4',
      providerId: 'e2b07384-d113-4e4e-9c8e-3d8e3d8e3d8e',
      name: 'Full Style Groom & Haircut',
      description: 'Breed-specific haircut by expert groomers.',
      category: 'Styling',
      price: 65.00,
      status: 'ACTIVE',
      durationMinutes: 75,
    }
  ]
};

const OFFLINE_MOCK_SLOTS: Record<string, Slot[]> = {
  'off-3': [
    {
      slotId: 'slot-1',
      offeringId: 'off-3',
      slotStart: '2026-06-25T10:00:00Z',
      slotEnd: '2026-06-25T10:45:00Z',
      status: 'AVAILABLE'
    },
    {
      slotId: 'slot-2',
      offeringId: 'off-3',
      slotStart: '2026-06-25T11:00:00Z',
      slotEnd: '2026-06-25T11:45:00Z',
      status: 'AVAILABLE'
    }
  ],
  'off-4': [
    {
      slotId: 'slot-3',
      offeringId: 'off-4',
      slotStart: '2026-06-25T13:00:00Z',
      slotEnd: '2026-06-25T14:15:00Z',
      status: 'BOOKED'
    }
  ]
};

export default function InventoryScreen() {
  const scheme = useColorScheme() === 'dark' ? 'dark' : 'light';
  const colors = Colors[scheme];

  const { providerId } = useAuth();

  type ProviderOption = {
    id: string;
    label: string;
    fulfillmentType: 'DELIVERY' | 'APPOINTMENT';
  };

  const [providers, setProviders] = useState<ProviderOption[]>([]);
  const [selectedProvider, setSelectedProvider] = useState<ProviderOption | null>(null);
  const selectedProviderId = selectedProvider?.id ?? providerId;
  const selectedProviderFulfillment = selectedProvider?.fulfillmentType ?? 'DELIVERY';

  const fetchProviders = useCallback(async () => {
    if (!providerId) {
      setProviders([]);
      setSelectedProvider(null);
      return;
    }
    try {
      const data = await apiClient.get<any[]>('/api/v1/providers/me');
      const mapped: ProviderOption[] = data.map((provider) => ({
        id: provider.providerId,
        label:
          provider.providerType === 'PET_STORE'
            ? `🏬 ${provider.name}`
            : provider.providerType === 'VET_HOSPITAL'
              ? `🏥 ${provider.name}`
              : `✂️ ${provider.name}`,
        fulfillmentType: provider.fulfillmentType || 'DELIVERY',
      }));
      setProviders(mapped);
      setSelectedProvider(
        mapped.find((provider) => provider.id === providerId) ?? mapped[0] ?? null,
      );
    } catch (error) {
      console.warn('Failed to resolve provider businesses', error);
      setProviders([]);
      setSelectedProvider(null);
    }
  }, [providerId]);

  useEffect(() => {
    void fetchProviders();
  }, [fetchProviders]);

  // Inventory state
  const [offerings, setOfferings] = useState<Offering[]>([]);
  const [loading, setLoading] = useState(true);
  const [isOffline, setIsOffline] = useState(false);

  // Offering form modal
  const [showAddForm, setShowAddForm] = useState(false);
  const [formName, setFormName] = useState('');
  const [formDesc, setFormDesc] = useState('');
  const [formCategory, setFormCategory] = useState('');
  const [formPrice, setFormPrice] = useState('');
  const [formStock, setFormStock] = useState('');
  const [formSku, setFormSku] = useState('');
  const [formDuration, setFormDuration] = useState('');
  const [submittingOffering, setSubmittingOffering] = useState(false);

  // Slots management modal
  const [selectedOffering, setSelectedOffering] = useState<Offering | null>(null);
  const [slots, setSlots] = useState<Slot[]>([]);
  const [loadingSlots, setLoadingSlots] = useState(false);
  const [newSlotStart, setNewSlotStart] = useState('');
  const [newSlotEnd, setNewSlotEnd] = useState('');
  const [defaultSlotEndDate] = useState(() => new Date(Date.now() + 45 * 60 * 1000));
  const [showStartPicker, setShowStartPicker] = useState(false);
  const [showEndPicker, setShowEndPicker] = useState(false);
  const [creatingSlot, setCreatingSlot] = useState(false);

  // Load catalog items
  const fetchCatalog = useCallback(async () => {
    setLoading(true);
    setIsOffline(false);
    if (!selectedProviderId) {
      setOfferings([]);
      setLoading(false);
      return;
    }
    try {
      const data = await apiClient.get<Offering[]>(
        `/api/v1/catalog/offerings?providerId=${encodeURIComponent(selectedProviderId)}`,
      );
      setOfferings(data);
    } catch (error) {
      console.warn('Catalog API unreachable.', error);
      setIsOffline(appConfig.allowDemoMode);
      setOfferings(
        appConfig.allowDemoMode ? OFFLINE_MOCK_OFFERINGS[selectedProviderId] || [] : [],
      );
    } finally {
      setLoading(false);
    }
  }, [selectedProviderId]);

  useEffect(() => {
    fetchCatalog();
  }, [fetchCatalog]);

  const resetForm = () => {
    setFormName('');
    setFormDesc('');
    setFormCategory('');
    setFormPrice('');
    setFormStock('');
    setFormSku('');
    setFormDuration('');
  };

  // Submit new offering to catalog service
  const handleAddOffering = useCallback(async () => {
    if (!selectedProviderId) {
      Alert.alert('Provider Required', 'Complete provider onboarding before managing inventory.');
      return;
    }
    if (!formName.trim() || !formPrice.trim()) {
      Alert.alert('Validation Error', 'Offering name and price are required.');
      return;
    }

    const priceNum = parseFloat(formPrice);
    if (isNaN(priceNum) || priceNum <= 0) {
      Alert.alert('Validation Error', 'Please enter a valid price.');
      return;
    }

    const payload: Offering = {
      providerId: selectedProviderId,
      name: formName,
      description: formDesc.trim() || undefined,
      category: formCategory.trim() || undefined,
      price: priceNum,
      status: 'ACTIVE',
    };

    if (selectedProviderFulfillment === 'DELIVERY') {
      const stockNum = parseInt(formStock);
      if (isNaN(stockNum) || stockNum < 0) {
        Alert.alert('Validation Error', 'Stock quantity is required for delivery products.');
        return;
      }
      payload.stockQuantity = stockNum;
      payload.sku = formSku.trim() || undefined;
    } else {
      const durationNum = parseInt(formDuration);
      if (isNaN(durationNum) || durationNum <= 0) {
        Alert.alert('Validation Error', 'Duration (in minutes) is required for services.');
        return;
      }
      payload.durationMinutes = durationNum;
    }

    setSubmittingOffering(true);
    try {
      if (isOffline && appConfig.allowDemoMode) {
        // Offline sandbox mode simulation
        const mockNew: Offering = {
          ...payload,
          offeringId: 'mock-off-' + Date.now(),
        };
        setOfferings((prev) => [...prev, mockNew]);
        Alert.alert('Success', 'Item added to offline catalog sandbox.');
        setShowAddForm(false);
        resetForm();
      } else {
        await apiClient.post('/api/v1/catalog/offerings', payload);
        Alert.alert('Success', 'Offering created successfully!');
        void fetchCatalog();
        setShowAddForm(false);
        resetForm();
      }
    } catch (err) {
      Alert.alert('Connection Error', 'Cannot connect to gateway service.');
    } finally {
      setSubmittingOffering(false);
    }
  }, [
    selectedProviderId,
    selectedProviderFulfillment,
    formName,
    formDesc,
    formCategory,
    formPrice,
    formStock,
    formSku,
    formDuration,
    isOffline,
    fetchCatalog
  ]);

  // Toggle offering status
  const handleToggleOfferingStatus = useCallback(async (item: Offering) => {
    const newStatus = item.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE';
    try {
      if (isOffline && appConfig.allowDemoMode) {
        setOfferings((prev) =>
          prev.map((o) => (o.offeringId === item.offeringId ? { ...o, status: newStatus } : o))
        );
      } else {
        await apiClient.put(
          `/api/v1/catalog/offerings/${item.offeringId}`,
          { ...item, status: newStatus },
        );
        void fetchCatalog();
      }
    } catch (err) {
      Alert.alert('Connection Error', 'Gateway service unreachable.');
    }
  }, [isOffline, fetchCatalog]);

  // Load slots for selected service
  const openSlotsManager = useCallback(async (offering: Offering) => {
    setSelectedOffering(offering);
    setLoadingSlots(true);
    try {
      const data = await apiClient.get<Slot[]>(
        `/api/v1/catalog/slots?offeringId=${encodeURIComponent(offering.offeringId || '')}`,
      );
      setSlots(data);
    } catch (err) {
      console.warn('Slots API unreachable.');
      setSlots(appConfig.allowDemoMode ? OFFLINE_MOCK_SLOTS[offering.offeringId || ''] || [] : []);
    } finally {
      setLoadingSlots(false);
    }
  }, []);

  // Create slot for appointment-based offering
  const handleCreateSlot = useCallback(async () => {
    if (!selectedOffering || !selectedOffering.offeringId) return;
    if (!newSlotStart.trim() || !newSlotEnd.trim()) {
      Alert.alert('Validation Error', 'Start time and end time are required.');
      return;
    }

    const startInstant = new Date(newSlotStart);
    const endInstant = new Date(newSlotEnd);

    if (isNaN(startInstant.getTime()) || isNaN(endInstant.getTime())) {
      Alert.alert('Validation Error', 'Please enter valid ISO date strings.');
      return;
    }

    if (endInstant <= startInstant) {
      Alert.alert('Validation Error', 'Slot end time must be after the start time.');
      return;
    }

    const payload: Slot = {
      offeringId: selectedOffering.offeringId,
      slotStart: startInstant.toISOString(),
      slotEnd: endInstant.toISOString(),
      status: 'AVAILABLE'
    };

    setCreatingSlot(true);
    try {
      if (isOffline && appConfig.allowDemoMode) {
        const mockNew: Slot = {
          ...payload,
          slotId: 'mock-slot-' + Date.now(),
        };
        setSlots((prev) => [...prev, mockNew]);
        Alert.alert('Success', 'Slot added in offline sandbox mode.');
        setNewSlotStart('');
        setNewSlotEnd('');
      } else {
        await apiClient.post('/api/v1/catalog/slots', payload);
        Alert.alert('Success', 'Time slot created.');
        setNewSlotStart('');
        setNewSlotEnd('');
        void openSlotsManager(selectedOffering);
      }
    } catch (err) {
      Alert.alert('Connection Error', 'Gateway service unreachable.');
    } finally {
      setCreatingSlot(false);
    }
  }, [selectedOffering, newSlotStart, newSlotEnd, isOffline, openSlotsManager]);

  // Update slot status
  const handleUpdateSlotStatus = useCallback(async (slotId: string, currentStatus: Slot['status']) => {
    const nextStatusMap: Record<Slot['status'], Slot['status']> = {
      AVAILABLE: 'HELD',
      HELD: 'BOOKED',
      BOOKED: 'BLOCKED',
      BLOCKED: 'AVAILABLE',
    };
    const nextStatus = nextStatusMap[currentStatus];

    try {
      if (isOffline && appConfig.allowDemoMode) {
        setSlots((prev) =>
          prev.map((s) => (s.slotId === slotId ? { ...s, status: nextStatus } : s))
        );
      } else {
        await apiClient.put(
          `/api/v1/catalog/slots/${slotId}/status?status=${nextStatus}`,
        );
        if (selectedOffering) {
          void openSlotsManager(selectedOffering);
        }
      }
    } catch (err) {
      Alert.alert('Connection Error', 'Gateway service unreachable.');
    }
  }, [isOffline, selectedOffering, openSlotsManager]);

  // Render offering item
  const renderOfferingItem = useCallback(({ item }: { item: Offering }) => {
    const isService = item.durationMinutes !== undefined && item.durationMinutes !== null;
    return (
      <View style={[styles.card, { backgroundColor: colors.backgroundElement, borderColor: colors.textSecondary }]}>
        <View style={styles.cardHeader}>
          <View style={{ flex: 1 }}>
            <ThemedText style={styles.cardTitle}>{item.name}</ThemedText>
            <ThemedText type="small" style={{ color: colors.textSecondary, marginTop: Spacing.half }}>
              {item.category || 'Uncategorized'}
            </ThemedText>
          </View>
          <View style={[styles.statusBadge, { backgroundColor: item.status === 'ACTIVE' ? SUCCESS_EMERALD : ERROR_RED }]}>
            <ThemedText style={styles.statusBadgeText}>{item.status}</ThemedText>
          </View>
        </View>

        <ThemedText type="small" style={styles.cardDesc}>
          {item.description || 'No description provided.'}
        </ThemedText>

        <View style={styles.cardDetails}>
          <ThemedText style={styles.cardPrice}>
            ${item.price.toFixed(2)}
          </ThemedText>

          {isService ? (
            <ThemedText type="small" style={{ color: colors.textSecondary }}>
              🕒 {item.durationMinutes} mins duration
            </ThemedText>
          ) : (
            <ThemedText type="small" style={{ color: colors.textSecondary }}>
              📦 Stock: {item.stockQuantity} | SKU: {item.sku || 'N/A'}
            </ThemedText>
          )}
        </View>

        <View style={styles.cardActions}>
          <TouchableOpacity
            style={[styles.actionBtn, { borderColor: colors.text, borderWidth: 1 }]}
            onPress={() => handleToggleOfferingStatus(item)}
            activeOpacity={0.7}
          >
            <ThemedText type="small" style={{ fontWeight: '600' }}>
              {item.status === 'ACTIVE' ? 'Deactivate' : 'Activate'}
            </ThemedText>
          </TouchableOpacity>

          {isService && (
            <TouchableOpacity
              style={[styles.actionBtn, { backgroundColor: PRIMARY_BLUE }]}
              onPress={() => openSlotsManager(item)}
              activeOpacity={0.7}
            >
              <ThemedText type="small" style={{ color: '#ffffff', fontWeight: '800' }}>
                Manage Slots
              </ThemedText>
            </TouchableOpacity>
          )}
        </View>
      </View>
    );
  }, [colors, handleToggleOfferingStatus, openSlotsManager]);

  const keyExtractor = useCallback((item: Offering) => item.offeringId || '', []);

  return (
    <ThemedView style={[styles.container, { backgroundColor: colors.background }]}>
      <SafeAreaView style={styles.safeArea}>
        {/* Connection status warning */}
        {isOffline && (
          <View style={styles.offlineBanner}>
            <ThemedText type="small" style={styles.offlineText}>
              ⚠️ You are currently offline. Retry again.
            </ThemedText>
            <TouchableOpacity style={styles.retryBtn} onPress={fetchCatalog}>
              <ThemedText type="small" style={styles.retryBtnText}>Retry</ThemedText>
            </TouchableOpacity>
          </View>
        )}

        {/* Business Selector Header */}
        <View style={[styles.header, { borderBottomColor: colors.backgroundSelected }]}>
          <ThemedText type="subtitle" style={{ fontWeight: '800' }}>Catalog & Inventory</ThemedText>
          <View style={styles.tabRow}>
            {providers.map((provider) => (
              <TouchableOpacity
                key={provider.id}
                style={[
                  styles.tabBtn,
                  { backgroundColor: colors.backgroundElement },
                  selectedProviderId === provider.id && {
                    backgroundColor: colors.backgroundSelected,
                    borderWidth: 2,
                    borderColor: colors.text,
                  }
                ]}
                onPress={() => setSelectedProvider(provider)}
                activeOpacity={0.7}
                accessibilityRole="tab"
                accessibilityLabel={`Switch to ${provider.label}`}
                accessibilityState={{ selected: selectedProviderId === provider.id }}
              >
                <ThemedText type="small" style={{ fontWeight: '700' }}>{provider.label}</ThemedText>
              </TouchableOpacity>
            ))}
          </View>
        </View>

        {/* List Content */}
        {loading ? (
          <View style={styles.centered}>
            <ActivityIndicator size="large" color={PRIMARY_BLUE} />
          </View>
        ) : (
          <FlatList
            data={offerings}
            renderItem={renderOfferingItem}
            keyExtractor={keyExtractor}
            contentContainerStyle={styles.listContent}
            ListEmptyComponent={
              <View style={styles.centered}>
                <ThemedText style={{ color: colors.textSecondary }}>No products or services found.</ThemedText>
              </View>
            }
          />
        )}

        {/* Sticky FAB to add offering */}
        <TouchableOpacity
          style={[styles.fab, { backgroundColor: colors.text }]}
          onPress={() => setShowAddForm(true)}
          activeOpacity={0.8}
        >
          <ThemedText style={{ color: colors.background, fontSize: 32, fontWeight: '300' }}>+</ThemedText>
        </TouchableOpacity>

        {/* Modal: Add Offering Form */}
        <Modal visible={showAddForm} animationType="slide" transparent>
          <View style={styles.modalOverlay}>
            <ThemedView style={[styles.modalContent, { backgroundColor: colors.background }]}>
              <ScrollView showsVerticalScrollIndicator={false}>
                <View style={styles.modalHeader}>
                  <ThemedText type="subtitle" style={{ fontWeight: '800' }}>Add Offering</ThemedText>
                  <TouchableOpacity onPress={() => setShowAddForm(false)} style={styles.closeBtn}>
                    <ThemedText style={{ fontSize: 24 }}>✕</ThemedText>
                  </TouchableOpacity>
                </View>

                <View style={styles.formGroup}>
                  <ThemedText type="small" style={styles.formLabel}>Item Name *</ThemedText>
                  <TextInput
                    style={[styles.formInput, { backgroundColor: colors.backgroundElement, color: colors.text }]}
                    value={formName}
                    onChangeText={setFormName}
                    placeholder="e.g. Organic Shampoo Treatment"
                    placeholderTextColor="#888"
                  />
                </View>

                <View style={styles.formGroup}>
                  <ThemedText type="small" style={styles.formLabel}>Description</ThemedText>
                  <TextInput
                    style={[styles.formInput, { backgroundColor: colors.backgroundElement, color: colors.text }]}
                    value={formDesc}
                    onChangeText={setFormDesc}
                    multiline
                    numberOfLines={3}
                    placeholder="Enter short description"
                    placeholderTextColor="#888"
                  />
                </View>

                <View style={styles.formGroup}>
                  <ThemedText type="small" style={styles.formLabel}>Category</ThemedText>
                  <TextInput
                    style={[styles.formInput, { backgroundColor: colors.backgroundElement, color: colors.text }]}
                    value={formCategory}
                    onChangeText={setFormCategory}
                    placeholder="e.g. Grooming, Food, Accessory"
                    placeholderTextColor="#888"
                  />
                </View>

                <View style={styles.formGroup}>
                  <ThemedText type="small" style={styles.formLabel}>Price ($) *</ThemedText>
                  <TextInput
                    style={[styles.formInput, { backgroundColor: colors.backgroundElement, color: colors.text }]}
                    value={formPrice}
                    onChangeText={setFormPrice}
                    keyboardType="numeric"
                    placeholder="0.00"
                    placeholderTextColor="#888"
                  />
                </View>

                {selectedProviderFulfillment === 'DELIVERY' ? (
                  <>
                    <View style={styles.formGroup}>
                      <ThemedText type="small" style={styles.formLabel}>Stock Quantity *</ThemedText>
                      <TextInput
                        style={[styles.formInput, { backgroundColor: colors.backgroundElement, color: colors.text }]}
                        value={formStock}
                        onChangeText={setFormStock}
                        keyboardType="number-pad"
                        placeholder="e.g. 25"
                        placeholderTextColor="#888"
                      />
                    </View>
                    <View style={styles.formGroup}>
                      <ThemedText type="small" style={styles.formLabel}>SKU Code</ThemedText>
                      <TextInput
                        style={[styles.formInput, { backgroundColor: colors.backgroundElement, color: colors.text }]}
                        value={formSku}
                        onChangeText={setFormSku}
                        placeholder="e.g. ITEM-SHAMP-01"
                        placeholderTextColor="#888"
                      />
                    </View>
                  </>
                ) : (
                  <View style={styles.formGroup}>
                    <ThemedText type="small" style={styles.formLabel}>Duration (Minutes) *</ThemedText>
                    <TextInput
                      style={[styles.formInput, { backgroundColor: colors.backgroundElement, color: colors.text }]}
                      value={formDuration}
                      onChangeText={setFormDuration}
                      keyboardType="number-pad"
                      placeholder="e.g. 45"
                      placeholderTextColor="#888"
                    />
                  </View>
                )}

                <TouchableOpacity
                  style={[styles.submitBtn, { backgroundColor: PRIMARY_BLUE }]}
                  onPress={handleAddOffering}
                  disabled={submittingOffering}
                  activeOpacity={0.8}
                >
                  {submittingOffering ? (
                    <ActivityIndicator size="small" color="#ffffff" />
                  ) : (
                    <ThemedText style={styles.submitBtnText}>Create Offering</ThemedText>
                  )}
                </TouchableOpacity>
              </ScrollView>
            </ThemedView>
          </View>
        </Modal>

        {/* Modal: Slots Management Screen */}
        <Modal visible={selectedOffering !== null} animationType="slide" transparent>
          <View style={styles.modalOverlay}>
            <ThemedView style={[styles.modalContent, { backgroundColor: colors.background }]}>
              <View style={styles.modalHeader}>
                <View style={{ flex: 1 }}>
                  <ThemedText type="subtitle" style={{ fontWeight: '800' }}>Manage Booking Slots</ThemedText>
                  <ThemedText type="small" style={{ color: colors.textSecondary }}>
                    {selectedOffering?.name}
                  </ThemedText>
                </View>
                <TouchableOpacity onPress={() => setSelectedOffering(null)} style={styles.closeBtn}>
                  <ThemedText style={{ fontSize: 24 }}>✕</ThemedText>
                </TouchableOpacity>
              </View>

              {/* List of Time Slots */}
              <View style={{ flex: 1 }}>
                {loadingSlots ? (
                  <ActivityIndicator size="large" color={PRIMARY_BLUE} style={{ marginTop: Spacing.four }} />
                ) : (
                  <FlatList
                    data={slots}
                    keyExtractor={(item) => item.slotId || ''}
                    renderItem={({ item }) => {
                      const startStr = new Date(item.slotStart).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
                      const endStr = new Date(item.slotEnd).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
                      const dateStr = new Date(item.slotStart).toLocaleDateString([], { month: 'short', day: 'numeric' });

                      const statusColors: Record<Slot['status'], string> = {
                        AVAILABLE: SUCCESS_EMERALD,
                        HELD: WARNING_AMBER,
                        BOOKED: PRIMARY_BLUE,
                        BLOCKED: ERROR_RED,
                      };

                      return (
                        <View style={[styles.slotItem, { borderBottomColor: colors.backgroundElement }]}>
                          <View style={{ flex: 1 }}>
                            <ThemedText style={{ fontWeight: '700' }}>
                              📅 {dateStr}  |  🕒 {startStr} - {endStr}
                            </ThemedText>
                            <ThemedText type="small" style={{ color: colors.textSecondary, marginTop: Spacing.half }}>
                              Timezone: UTC
                            </ThemedText>
                          </View>

                          <TouchableOpacity
                            style={[styles.statusToggleBtn, { backgroundColor: statusColors[item.status] }]}
                            onPress={() => item.slotId && handleUpdateSlotStatus(item.slotId, item.status)}
                          >
                            <ThemedText style={styles.statusToggleText}>
                              {item.status}
                            </ThemedText>
                          </TouchableOpacity>
                        </View>
                      );
                    }}
                    ListEmptyComponent={
                      <View style={styles.centered}>
                        <ThemedText style={{ color: colors.textSecondary }}>No time slots created yet.</ThemedText>
                      </View>
                    }
                  />
                )}
              </View>

              {/* Form to Create Slot */}
              <View style={[styles.createSlotBox, { borderTopColor: colors.backgroundSelected }]}>
                <ThemedText style={{ fontWeight: '700', marginBottom: Spacing.two }}>Create New Slot</ThemedText>
                
                {Platform.OS === 'web' ? (
                  <View style={{ marginBottom: Spacing.two, gap: Spacing.one }}>
                    <ThemedText type="small" style={{ color: colors.textSecondary }}>Start Time</ThemedText>
                    <input
                      type="datetime-local"
                      value={newSlotStart ? newSlotStart.substring(0, 16) : ''}
                      onChange={(e) => {
                        const val = e.target.value;
                        if (val) {
                          setNewSlotStart(new Date(val).toISOString());
                        } else {
                          setNewSlotStart('');
                        }
                      }}
                      style={{
                        height: 48,
                        borderRadius: 8,
                        borderWidth: 1,
                        borderColor: colors.backgroundSelected,
                        padding: 12,
                        backgroundColor: colors.backgroundElement,
                        color: colors.text,
                        fontSize: 14,
                        fontFamily: 'inherit'
                      }}
                    />
                    <ThemedText type="small" style={{ color: colors.textSecondary, marginTop: Spacing.one }}>End Time</ThemedText>
                    <input
                      type="datetime-local"
                      value={newSlotEnd ? newSlotEnd.substring(0, 16) : ''}
                      onChange={(e) => {
                        const val = e.target.value;
                        if (val) {
                          setNewSlotEnd(new Date(val).toISOString());
                        } else {
                          setNewSlotEnd('');
                        }
                      }}
                      style={{
                        height: 48,
                        borderRadius: 8,
                        borderWidth: 1,
                        borderColor: colors.backgroundSelected,
                        padding: 12,
                        backgroundColor: colors.backgroundElement,
                        color: colors.text,
                        fontSize: 14,
                        fontFamily: 'inherit',
                        marginBottom: Spacing.two
                      }}
                    />
                  </View>
                ) : (
                  <View style={{ marginBottom: Spacing.two, gap: Spacing.two }}>
                    <TouchableOpacity
                      style={[styles.formInput, { backgroundColor: colors.backgroundElement, justifyContent: 'center' }]}
                      onPress={() => setShowStartPicker(true)}
                    >
                      <ThemedText type="small" style={{ color: newSlotStart ? colors.text : '#888' }}>
                        {newSlotStart ? `Start: ${new Date(newSlotStart).toLocaleString()}` : 'Select Start Time *'}
                      </ThemedText>
                    </TouchableOpacity>
                    {showStartPicker && (
                      <DateTimePicker
                        value={newSlotStart ? new Date(newSlotStart) : new Date()}
                        mode="datetime"
                        display="default"
                        onChange={(event, selectedDate) => {
                          setShowStartPicker(false);
                          if (selectedDate) {
                            setNewSlotStart(selectedDate.toISOString());
                          }
                        }}
                      />
                    )}

                    <TouchableOpacity
                      style={[styles.formInput, { backgroundColor: colors.backgroundElement, justifyContent: 'center', marginBottom: Spacing.one }]}
                      onPress={() => setShowEndPicker(true)}
                    >
                      <ThemedText type="small" style={{ color: newSlotEnd ? colors.text : '#888' }}>
                        {newSlotEnd ? `End: ${new Date(newSlotEnd).toLocaleString()}` : 'Select End Time *'}
                      </ThemedText>
                    </TouchableOpacity>
                    {showEndPicker && (
                      <DateTimePicker
                        value={newSlotEnd ? new Date(newSlotEnd) : defaultSlotEndDate}
                        mode="datetime"
                        display="default"
                        onChange={(event, selectedDate) => {
                          setShowEndPicker(false);
                          if (selectedDate) {
                            setNewSlotEnd(selectedDate.toISOString());
                          }
                        }}
                      />
                    )}
                  </View>
                )}

                <TouchableOpacity
                  style={[styles.submitBtn, { backgroundColor: PRIMARY_BLUE, marginTop: 0 }]}
                  onPress={handleCreateSlot}
                  disabled={creatingSlot}
                  activeOpacity={0.8}
                >
                  {creatingSlot ? (
                    <ActivityIndicator size="small" color="#ffffff" />
                  ) : (
                    <ThemedText style={styles.submitBtnText}>Add Time Slot</ThemedText>
                  )}
                </TouchableOpacity>
              </View>
            </ThemedView>
          </View>
        </Modal>
      </SafeAreaView>
    </ThemedView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  safeArea: {
    flex: 1,
  },
  offlineBanner: {
    backgroundColor: WARNING_AMBER,
    paddingHorizontal: Spacing.four,
    paddingVertical: Spacing.two,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  offlineText: {
    color: '#000000',
    fontWeight: '700',
  },
  retryBtn: {
    backgroundColor: '#000000',
    paddingHorizontal: Spacing.three,
    paddingVertical: Spacing.one,
    borderRadius: 8,
  },
  retryBtnText: {
    color: '#ffffff',
    fontWeight: '800',
  },
  header: {
    paddingHorizontal: Spacing.four,
    paddingVertical: Spacing.three,
    borderBottomWidth: 2,
  },
  tabRow: {
    flexDirection: 'row',
    gap: Spacing.two,
    marginTop: Spacing.two,
  },
  tabBtn: {
    flex: 1,
    paddingVertical: Spacing.two,
    borderRadius: 12,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: '#f1f1f1',
    minHeight: 44, // Touch target height
  },
  listContent: {
    padding: Spacing.four,
    gap: Spacing.four,
    paddingBottom: 80,
  },
  centered: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    padding: Spacing.six,
  },
  card: {
    padding: Spacing.four,
    borderRadius: 24,
    borderWidth: 2,
    gap: Spacing.three,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.05,
    shadowRadius: 8,
    elevation: 2,
  },
  cardHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'flex-start',
  },
  cardTitle: {
    fontSize: 18,
    fontWeight: '800',
  },
  statusBadge: {
    paddingHorizontal: Spacing.two,
    paddingVertical: Spacing.half,
    borderRadius: 8,
  },
  statusBadgeText: {
    color: '#ffffff',
    fontSize: 11,
    fontWeight: '800',
  },
  cardDesc: {
    lineHeight: 20,
  },
  cardDetails: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginTop: Spacing.one,
  },
  cardPrice: {
    fontSize: 20,
    fontWeight: '800',
    color: PRIMARY_BLUE,
  },
  cardActions: {
    flexDirection: 'row',
    justifyContent: 'flex-end',
    gap: Spacing.two,
    marginTop: Spacing.two,
  },
  actionBtn: {
    paddingHorizontal: Spacing.three,
    paddingVertical: Spacing.two,
    borderRadius: 12,
    alignItems: 'center',
    justifyContent: 'center',
    minWidth: 100,
    minHeight: 44, // Touch target height
  },
  fab: {
    position: 'absolute',
    bottom: Spacing.four,
    right: Spacing.four,
    width: 60,
    height: 60,
    borderRadius: 30,
    alignItems: 'center',
    justifyContent: 'center',
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.3,
    shadowRadius: 6,
    elevation: 6,
    zIndex: 99,
  },
  modalOverlay: {
    flex: 1,
    backgroundColor: 'rgba(0, 0, 0, 0.5)',
    justifyContent: 'flex-end',
  },
  modalContent: {
    height: '85%',
    borderTopLeftRadius: 32,
    borderTopRightRadius: 32,
    padding: Spacing.four,
  },
  modalHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: Spacing.four,
  },
  closeBtn: {
    width: 44,
    height: 44,
    alignItems: 'center',
    justifyContent: 'center',
  },
  formGroup: {
    marginBottom: Spacing.three,
  },
  formLabel: {
    fontWeight: '700',
    marginBottom: Spacing.one,
  },
  formInput: {
    padding: Spacing.three,
    borderRadius: 12,
    fontSize: 14,
    borderWidth: 1,
    borderColor: '#ccc',
    minHeight: 48, // Touch target height
  },
  submitBtn: {
    paddingVertical: Spacing.three,
    borderRadius: 16,
    alignItems: 'center',
    justifyContent: 'center',
    marginTop: Spacing.four,
    minHeight: 50,
  },
  submitBtnText: {
    color: '#ffffff',
    fontWeight: '800',
    fontSize: 16,
  },
  slotItem: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingVertical: Spacing.three,
    borderBottomWidth: 1,
  },
  statusToggleBtn: {
    paddingHorizontal: Spacing.three,
    paddingVertical: Spacing.two,
    borderRadius: 8,
    minWidth: 90,
    alignItems: 'center',
    minHeight: 44,
  },
  statusToggleText: {
    color: '#ffffff',
    fontSize: 12,
    fontWeight: '800',
  },
  createSlotBox: {
    paddingTop: Spacing.three,
    borderTopWidth: 2,
    marginTop: Spacing.two,
  },
});
