import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { Alert, Modal, Pressable, ScrollView, StyleSheet, TextInput, View } from 'react-native';

import { BarcodeScannerModal } from '@/components/barcode-scanner-modal';
import { AppIcon } from '@/components/app-icon';
import {
  ActionButton,
  AppBar,
  FeedbackBanner,
  FilterChip,
  RoleBadge,
  SectionHeader,
  StateView,
  StatusBadge,
} from '@/components/foundation/primitives';
import { ScreenShell } from '@/components/foundation/screen-shell';
import { ThemedText } from '@/components/themed-text';
import { AppCard } from '@/components/ui/app-card';
import { TextField } from '@/components/ui/text-field';
import { ApiError, apiErrorKind, apiErrorMessage } from '@/contracts/api-error';
import { useAuth } from '@/context/AuthContext';
import { radii, shadows, spacing, touchTarget, typography } from '@/design/tokens';
import { useTheme } from '@/hooks/use-theme';
import {
  createMerchantOffering,
  deleteMerchantOffering,
  fetchMerchantOfferings,
  fetchMerchantProviders,
  updateMerchantOffering,
  type MerchantOffering,
  type MerchantProvider,
  type OfferingDraft,
  type OfferingStatus,
} from '@/services/merchant-inventory';
import { barcodeValidationMessage, normalizeBarcode } from '@/utils/barcode';
import { formatCurrency, formatStatusLabel } from '@/utils/formatters';

type InventoryFilter = 'ALL' | 'ACTIVE' | 'LOW_STOCK' | 'OUT_OF_STOCK' | 'INACTIVE';

type FormState = {
  name: string;
  description: string;
  category: string;
  price: string;
  stockQuantity: string;
  sku: string;
  durationMinutes: string;
  barcode: string;
  status: OfferingStatus;
};

const EMPTY_FORM: FormState = {
  name: '',
  description: '',
  category: '',
  price: '',
  stockQuantity: '',
  sku: '',
  durationMinutes: '',
  barcode: '',
  status: 'ACTIVE',
};

function providerIcon(providerType: string): string {
  if (providerType === 'PET_STORE') return 'Store';
  if (providerType === 'VET_HOSPITAL') return 'Hospital';
  if (providerType === 'GROOMING_CENTER') return 'Groomer';
  return formatStatusLabel(providerType);
}

function offeringTone(status: OfferingStatus): 'success' | 'warning' | 'danger' | 'neutral' {
  if (status === 'ACTIVE') return 'success';
  if (status === 'OUT_OF_STOCK') return 'danger';
  if (status === 'INACTIVE') return 'neutral';
  return 'warning';
}

function draftFromOffering(offering: MerchantOffering): OfferingDraft {
  return {
    name: offering.name,
    description: offering.description ?? undefined,
    category: offering.category ?? undefined,
    price: offering.price,
    status: offering.status,
    stockQuantity: offering.stockQuantity ?? undefined,
    sku: offering.sku ?? undefined,
    durationMinutes: offering.durationMinutes ?? undefined,
    barcode: offering.barcode ?? undefined,
    imageUrl: offering.imageUrl ?? undefined,
  };
}

function formFromOffering(offering: MerchantOffering): FormState {
  return {
    name: offering.name,
    description: offering.description ?? '',
    category: offering.category ?? '',
    price: String(offering.price),
    stockQuantity: offering.stockQuantity === null || offering.stockQuantity === undefined
      ? ''
      : String(offering.stockQuantity),
    sku: offering.sku ?? '',
    durationMinutes: offering.durationMinutes === null || offering.durationMinutes === undefined
      ? ''
      : String(offering.durationMinutes),
    barcode: offering.barcode ?? '',
    status: offering.status,
  };
}

export default function InventoryScreen() {
  const theme = useTheme();
  const { providerId } = useAuth();
  const [providers, setProviders] = useState<MerchantProvider[]>([]);
  const [selectedProviderId, setSelectedProviderId] = useState<string | null>(providerId);
  const [offerings, setOfferings] = useState<MerchantOffering[]>([]);
  const [loadingProviders, setLoadingProviders] = useState(true);
  const [loadingOfferings, setLoadingOfferings] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<unknown>(null);
  const [query, setQuery] = useState('');
  const [filter, setFilter] = useState<InventoryFilter>('ALL');
  const [editing, setEditing] = useState<MerchantOffering | null>(null);
  const [formVisible, setFormVisible] = useState(false);
  const [barcodeScannerVisible, setBarcodeScannerVisible] = useState(false);
  const [form, setForm] = useState<FormState>(EMPTY_FORM);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});

  const selectedProvider = useMemo(
    () => providers.find((provider) => provider.providerId === selectedProviderId) ?? null,
    [providers, selectedProviderId],
  );

  const loadProviders = useCallback(async () => {
    setLoadingProviders(true);
    setError(null);
    try {
      const liveProviders = await fetchMerchantProviders();
      setProviders(liveProviders);
      setSelectedProviderId((current) => {
        if (current && liveProviders.some((provider) => provider.providerId === current)) return current;
        if (providerId && liveProviders.some((provider) => provider.providerId === providerId)) return providerId;
        return liveProviders[0]?.providerId ?? null;
      });
    } catch (loadError) {
      setProviders([]);
      setSelectedProviderId(null);
      setError(loadError);
    } finally {
      setLoadingProviders(false);
    }
  }, [providerId]);

  const loadOfferings = useCallback(async () => {
    if (!selectedProviderId) {
      setOfferings([]);
      return;
    }
    setLoadingOfferings(true);
    setError(null);
    try {
      setOfferings(await fetchMerchantOfferings(selectedProviderId));
    } catch (loadError) {
      setOfferings([]);
      setError(loadError);
    } finally {
      setLoadingOfferings(false);
    }
  }, [selectedProviderId]);

  useEffect(() => {
    void loadProviders();
  }, [loadProviders]);

  useEffect(() => {
    void loadOfferings();
  }, [loadOfferings]);

  const counts = useMemo(
    () => ({
      ALL: offerings.length,
      ACTIVE: offerings.filter((offering) => offering.status === 'ACTIVE').length,
      LOW_STOCK: offerings.filter(
        (offering) => offering.stockQuantity !== null &&
          offering.stockQuantity !== undefined &&
          offering.stockQuantity > 0 &&
          offering.stockQuantity <= 5,
      ).length,
      OUT_OF_STOCK: offerings.filter(
        (offering) => offering.status === 'OUT_OF_STOCK' || offering.stockQuantity === 0,
      ).length,
      INACTIVE: offerings.filter((offering) => offering.status === 'INACTIVE').length,
    }),
    [offerings],
  );

  const visibleOfferings = useMemo(() => {
    const normalizedQuery = query.trim().toLowerCase();
    return offerings.filter((offering) => {
      const matchesFilter =
        filter === 'ALL' ||
        (filter === 'ACTIVE' && offering.status === 'ACTIVE') ||
        (filter === 'LOW_STOCK' &&
          offering.stockQuantity !== null &&
          offering.stockQuantity !== undefined &&
          offering.stockQuantity > 0 &&
          offering.stockQuantity <= 5) ||
        (filter === 'OUT_OF_STOCK' &&
          (offering.status === 'OUT_OF_STOCK' || offering.stockQuantity === 0)) ||
        (filter === 'INACTIVE' && offering.status === 'INACTIVE');
      if (!matchesFilter) return false;
      if (!normalizedQuery) return true;
      return [offering.name, offering.category, offering.sku, offering.barcode]
        .some((value) => value?.toLowerCase().includes(normalizedQuery));
    });
  }, [filter, offerings, query]);

  const openCreate = () => {
    setEditing(null);
    setForm(EMPTY_FORM);
    setFieldErrors({});
    setError(null);
    setBarcodeScannerVisible(false);
    setFormVisible(true);
  };

  const openEdit = (offering: MerchantOffering) => {
    setEditing(offering);
    setForm(formFromOffering(offering));
    setFieldErrors({});
    setError(null);
    setBarcodeScannerVisible(false);
    setFormVisible(true);
  };

  const closeForm = () => {
    setFormVisible(false);
    setBarcodeScannerVisible(false);
  };

  const openBarcodeScanner = () => {
    setFormVisible(false);
    setBarcodeScannerVisible(true);
  };

  const closeBarcodeScanner = () => {
    setBarcodeScannerVisible(false);
    setFormVisible(true);
  };

  const validateDraft = (): OfferingDraft | null => {
    const errors: Record<string, string> = {};
    const price = Number(form.price);
    const isDelivery = selectedProvider?.fulfillmentType === 'DELIVERY';
    const stockQuantity = form.stockQuantity.trim() ? Number(form.stockQuantity) : undefined;
    const durationMinutes = form.durationMinutes.trim() ? Number(form.durationMinutes) : undefined;
    const barcode = isDelivery ? normalizeBarcode(form.barcode) : '';
    const barcodeError = isDelivery ? barcodeValidationMessage(barcode) : undefined;

    if (!form.name.trim()) errors.name = 'Name is required.';
    if (!Number.isFinite(price) || price <= 0) errors.price = 'Enter a price greater than zero.';
    if (isDelivery && (!Number.isInteger(stockQuantity) || (stockQuantity ?? -1) < 0)) {
      errors.stockQuantity = 'Enter a whole stock quantity of zero or more.';
    }
    if (!isDelivery && (!Number.isInteger(durationMinutes) || (durationMinutes ?? 0) <= 0)) {
      errors.durationMinutes = 'Enter a service duration in minutes.';
    }
    if (barcodeError) errors.barcode = barcodeError;

    setFieldErrors(errors);
    if (Object.keys(errors).length > 0) return null;

    return {
      name: form.name,
      description: form.description,
      category: form.category,
      price,
      status: isDelivery && stockQuantity === 0 ? 'OUT_OF_STOCK' : form.status,
      stockQuantity: isDelivery ? stockQuantity : undefined,
      sku: isDelivery ? form.sku : undefined,
      durationMinutes: isDelivery ? undefined : durationMinutes,
      barcode: isDelivery ? barcode : undefined,
    };
  };

  const save = async () => {
    if (!selectedProviderId) return;
    const draft = validateDraft();
    if (!draft) return;
    setSaving(true);
    setError(null);
    try {
      const saved = editing
        ? await updateMerchantOffering(editing, draft)
        : await createMerchantOffering(selectedProviderId, draft);
      setOfferings((current) => {
        const withoutSaved = current.filter((offering) => offering.offeringId !== saved.offeringId);
        return [...withoutSaved, saved].sort((left, right) => left.name.localeCompare(right.name));
      });
      closeForm();
      setEditing(null);
      setForm(EMPTY_FORM);
    } catch (saveError) {
      setError(saveError);
      if (saveError instanceof ApiError) {
        setFieldErrors(
          Object.fromEntries(
            Object.entries(saveError.fieldErrors).map(([field, messages]) => [
              field,
              messages[0] ?? 'Invalid value.',
            ]),
          ),
        );
      }
    } finally {
      setSaving(false);
    }
  };

  const toggleStatus = useCallback(async (offering: MerchantOffering) => {
    setSaving(true);
    setError(null);
    try {
      const nextStatus: OfferingStatus = offering.status === 'ACTIVE'
        ? 'INACTIVE'
        : offering.stockQuantity === 0
          ? 'OUT_OF_STOCK'
          : 'ACTIVE';
      const updated = await updateMerchantOffering(offering, {
        ...draftFromOffering(offering),
        status: nextStatus,
      });
      setOfferings((current) =>
        current.map((item) => (item.offeringId === updated.offeringId ? updated : item)),
      );
    } catch (updateError) {
      setError(updateError);
    } finally {
      setSaving(false);
    }
  }, []);

  const remove = useCallback((offering: MerchantOffering) => {
    if (!offering.offeringId) return;
    Alert.alert(
      'Delete offering',
      `Delete “${offering.name}”? Existing orders retain their item snapshot, but customers will no longer discover this offering.`,
      [
        { text: 'Keep', style: 'cancel' },
        {
          text: 'Delete',
          style: 'destructive',
          onPress: () => {
            setSaving(true);
            setError(null);
            void deleteMerchantOffering(offering.offeringId!)
              .then(() => setOfferings((current) =>
                current.filter((item) => item.offeringId !== offering.offeringId),
              ))
              .catch(setError)
              .finally(() => setSaving(false));
          },
        },
      ],
    );
  }, []);

  const errorTrace = error instanceof ApiError && error.traceId ? ` Reference: ${error.traceId}.` : '';

  return (
    <ScreenShell
      header={
        <AppBar
          eyebrow="MERCHANT WORKSPACE"
          title="Catalog & inventory"
          subtitle="Maintain customer-visible products and services"
          action={<RoleBadge role="merchant" />}
        />
      }
      testID="merchant-inventory"
    >
      {error ? (
        <FeedbackBanner
          tone="danger"
          title={apiErrorKind(error) === 'conflict' ? 'Inventory changed on the server' : 'Inventory action failed'}
          message={`${apiErrorMessage(error, 'Could not complete the inventory action.')}${errorTrace}`}
          icon="dispute"
        />
      ) : null}

      {loadingProviders ? <StateView kind="loading" title="Loading businesses" /> : null}
      {!loadingProviders && providers.length === 0 ? (
        <StateView
          kind="unauthorized"
          title="Approved business required"
          message="Complete provider onboarding before publishing products or services."
          actionLabel="Retry"
          onAction={() => void loadProviders()}
        />
      ) : null}

      {providers.length > 0 ? (
        <>
          <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.providerRow}>
            {providers.map((provider) => (
              <FilterChip
                key={provider.providerId}
                label={`${providerIcon(provider.providerType)} · ${provider.name}`}
                selected={provider.providerId === selectedProviderId}
                onPress={() => setSelectedProviderId(provider.providerId)}
              />
            ))}
          </ScrollView>

          <View style={styles.headingRow}>
            <SectionHeader
              title={selectedProvider?.name ?? 'Inventory'}
              subtitle={selectedProvider?.fulfillmentType === 'DELIVERY'
                ? 'Delivery products, stock and pricing'
                : 'Bookable services, duration and pricing'}
            />
            <ActionButton label="Add offering" icon="inventory" onPress={openCreate} />
          </View>

          {selectedProvider?.fulfillmentType === 'APPOINTMENT' ? (
            <FeedbackBanner
              tone="info"
              title="Appointment availability"
              message="Create and price services here. Slot scheduling and appointment operations remain in the Bookings workspace."
              icon="calendar"
            />
          ) : null}

          <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.filters}>
            {(['ALL', 'ACTIVE', 'LOW_STOCK', 'OUT_OF_STOCK', 'INACTIVE'] as const).map((value) => (
              <FilterChip
                key={value}
                label={`${value === 'ALL'
                  ? 'All'
                  : value === 'LOW_STOCK'
                    ? 'Low stock'
                    : value === 'OUT_OF_STOCK'
                      ? 'Out of stock'
                      : formatStatusLabel(value)} (${counts[value]})`}
                selected={filter === value}
                onPress={() => setFilter(value)}
              />
            ))}
          </ScrollView>

          <View style={[styles.search, { backgroundColor: theme.backgroundElement, borderColor: theme.border }]}>
            <AppIcon name="search" color={theme.textSecondary} size={18} />
            <TextInput
              value={query}
              onChangeText={setQuery}
              placeholder="Search name, category, SKU or barcode"
              placeholderTextColor={theme.textSecondary}
              style={[styles.searchInput, { color: theme.text }]}
              accessibilityLabel="Search inventory"
              returnKeyType="search"
            />
            {query ? (
              <Pressable
                onPress={() => setQuery('')}
                accessibilityRole="button"
                accessibilityLabel="Clear inventory search"
                style={styles.clear}
              >
                <AppIcon name="xmark" color={theme.textSecondary} size={18} />
              </Pressable>
            ) : null}
          </View>

          {loadingOfferings ? (
            <StateView kind="loading" title="Loading inventory" message="Reading the latest catalog state…" />
          ) : null}
          {!loadingOfferings && visibleOfferings.length === 0 ? (
            <StateView
              kind="empty"
              title={query ? 'No matching offerings' : 'No offerings in this view'}
              message={query
                ? 'Try another product, service, SKU or barcode.'
                : 'Add the first offering or select another filter.'}
              actionLabel={query ? 'Clear search' : 'Add offering'}
              onAction={query ? () => setQuery('') : openCreate}
            />
          ) : null}

          {!loadingOfferings && visibleOfferings.length > 0 ? (
            <View style={styles.list}>
              {visibleOfferings.map((offering) => {
                const isService = offering.durationMinutes !== null && offering.durationMinutes !== undefined;
                return (
                  <AppCard key={offering.offeringId ?? `${offering.providerId}-${offering.name}`} style={styles.card}>
                    <View style={styles.cardHeader}>
                      <View style={[styles.itemIcon, { backgroundColor: theme.primarySoft }]}>
                        <AppIcon name={isService ? 'calendar' : 'inventory'} color={theme.primary} size={22} />
                      </View>
                      <View style={styles.flex}>
                        <ThemedText style={styles.itemTitle}>{offering.name}</ThemedText>
                        <ThemedText type="small" themeColor="textSecondary">
                          {offering.category || 'Uncategorized'}
                          {offering.sku ? ` · SKU ${offering.sku}` : ''}
                          {offering.barcode ? ` · Barcode ${offering.barcode}` : ''}
                        </ThemedText>
                      </View>
                      <StatusBadge label={formatStatusLabel(offering.status)} tone={offeringTone(offering.status)} />
                    </View>

                    {offering.description ? (
                      <ThemedText type="small" themeColor="textSecondary">{offering.description}</ThemedText>
                    ) : null}

                    <View style={[styles.metrics, { backgroundColor: theme.muted }]}>
                      <View style={styles.metric}>
                        <ThemedText type="small" themeColor="textSecondary">Price</ThemedText>
                        <ThemedText style={styles.amount}>{formatCurrency(offering.price)}</ThemedText>
                      </View>
                      <View style={styles.metric}>
                        <ThemedText type="small" themeColor="textSecondary">
                          {isService ? 'Duration' : 'Stock'}
                        </ThemedText>
                        <ThemedText type="smallBold">
                          {isService
                            ? `${offering.durationMinutes} minutes`
                            : `${offering.stockQuantity ?? 0} available`}
                        </ThemedText>
                      </View>
                    </View>

                    {!isService &&
                    offering.stockQuantity !== null &&
                    offering.stockQuantity !== undefined &&
                    offering.stockQuantity <= 5 ? (
                      <FeedbackBanner
                        tone={offering.stockQuantity === 0 ? 'danger' : 'warning'}
                        title={offering.stockQuantity === 0 ? 'Out of stock' : 'Low stock'}
                        message={offering.stockQuantity === 0
                          ? 'Update stock before reactivating this offering.'
                          : `${offering.stockQuantity} units remain.`}
                        icon="inventory"
                      />
                    ) : null}

                    <View style={styles.actions}>
                      <ActionButton
                        label="Edit"
                        variant="secondary"
                        icon="inventory"
                        onPress={() => openEdit(offering)}
                        style={styles.action}
                      />
                      <ActionButton
                        label={offering.status === 'ACTIVE' ? 'Deactivate' : 'Activate'}
                        variant="ghost"
                        icon={offering.status === 'ACTIVE' ? 'xmark' : 'check'}
                        loading={saving}
                        disabled={offering.status !== 'ACTIVE' && offering.stockQuantity === 0}
                        onPress={() => void toggleStatus(offering)}
                        style={styles.action}
                      />
                      <ActionButton
                        label="Delete"
                        variant="destructive"
                        icon="xmark"
                        disabled={saving}
                        onPress={() => remove(offering)}
                        style={styles.action}
                      />
                    </View>
                  </AppCard>
                );
              })}
            </View>
          ) : null}
        </>
      ) : null}

      <Modal visible={formVisible} transparent animationType="slide" onRequestClose={closeForm}>
        <View style={styles.modalBackdrop}>
          <View
            style={[styles.modal, shadows.card, { backgroundColor: theme.backgroundElement, borderColor: theme.border }]}
            accessibilityViewIsModal
          >
            <ScrollView
              showsVerticalScrollIndicator={false}
              contentContainerStyle={styles.formContent}
              keyboardShouldPersistTaps="handled"
            >
              <View style={styles.cardHeader}>
                <View style={styles.flex}>
                  <ThemedText type="title">{editing ? 'Edit offering' : 'Add offering'}</ThemedText>
                  <ThemedText type="small" themeColor="textSecondary">
                    {selectedProvider?.fulfillmentType === 'DELIVERY'
                      ? 'Product details and current stock'
                      : 'Service details and customer-facing duration'}
                  </ThemedText>
                </View>
                <Pressable
                  onPress={closeForm}
                  accessibilityRole="button"
                  accessibilityLabel="Close offering form"
                  style={styles.clear}
                >
                  <AppIcon name="xmark" color={theme.textSecondary} size={20} />
                </Pressable>
              </View>

              <TextField
                label="Name"
                value={form.name}
                onChangeText={(value) => setForm((current) => ({ ...current, name: value }))}
                error={fieldErrors.name}
              />
              <TextField
                label="Description"
                value={form.description}
                onChangeText={(value) => setForm((current) => ({ ...current, description: value }))}
                multiline
                numberOfLines={3}
              />
              <TextField
                label="Category"
                value={form.category}
                onChangeText={(value) => setForm((current) => ({ ...current, category: value }))}
              />
              <TextField
                label="Price (₹)"
                value={form.price}
                onChangeText={(value) => setForm((current) => ({ ...current, price: value }))}
                keyboardType="decimal-pad"
                error={fieldErrors.price}
              />

              {selectedProvider?.fulfillmentType === 'DELIVERY' ? (
                <>
                  <TextField
                    label="Stock quantity"
                    value={form.stockQuantity}
                    onChangeText={(value) => setForm((current) => ({ ...current, stockQuantity: value }))}
                    keyboardType="number-pad"
                    error={fieldErrors.stockQuantity}
                  />
                  <TextField
                    label="SKU"
                    value={form.sku}
                    onChangeText={(value) => setForm((current) => ({ ...current, sku: value }))}
                    autoCapitalize="characters"
                  />
                  <View style={styles.barcodeRow}>
                    <View style={styles.flex}>
                      <TextField
                        label="Barcode"
                        value={form.barcode}
                        onChangeText={(value) => {
                          setForm((current) => ({ ...current, barcode: value }));
                          setFieldErrors((current) => {
                            const { barcode: _barcode, ...rest } = current;
                            return rest;
                          });
                        }}
                        autoCapitalize="characters"
                        autoCorrect={false}
                        error={fieldErrors.barcode}
                        hint="EAN, UPC, Code 39 or Code 128. Scan the package label or enter it manually."
                      />
                    </View>
                    <ActionButton
                      label="Scan barcode"
                      variant="secondary"
                      icon="inventory"
                      onPress={openBarcodeScanner}
                      style={styles.barcodeAction}
                    />
                  </View>
                </>
              ) : (
                <TextField
                  label="Duration in minutes"
                  value={form.durationMinutes}
                  onChangeText={(value) => setForm((current) => ({ ...current, durationMinutes: value }))}
                  keyboardType="number-pad"
                  error={fieldErrors.durationMinutes}
                />
              )}

              <View style={styles.statusSection}>
                <ThemedText type="smallBold">Visibility</ThemedText>
                <View style={styles.filters}>
                  {(['ACTIVE', 'INACTIVE'] as const).map((status) => (
                    <FilterChip
                      key={status}
                      label={formatStatusLabel(status)}
                      selected={form.status === status}
                      onPress={() => setForm((current) => ({ ...current, status }))}
                    />
                  ))}
                </View>
              </View>

              {error && formVisible ? (
                <FeedbackBanner tone="danger" title="Could not save offering" message={apiErrorMessage(error)} />
              ) : null}

              <View style={styles.actions}>
                <ActionButton label="Cancel" variant="ghost" onPress={closeForm} style={styles.action} />
                <ActionButton
                  label={editing ? 'Save changes' : 'Create offering'}
                  icon="check"
                  loading={saving}
                  onPress={() => void save()}
                  style={styles.action}
                />
              </View>
            </ScrollView>
          </View>
        </View>
      </Modal>

      <BarcodeScannerModal
        visible={barcodeScannerVisible}
        title="Scan inventory barcode"
        instruction="Scan the barcode printed on the product package. It will be attached to this offering."
        onClose={closeBarcodeScanner}
        onScanned={(barcode) => {
          setForm((current) => ({ ...current, barcode }));
          setFieldErrors((current) => {
            const { barcode: _barcode, ...rest } = current;
            return rest;
          });
        }}
      />
    </ScreenShell>
  );
}

const styles = StyleSheet.create({
  flex: { flex: 1 },
  providerRow: { gap: spacing.x2, paddingRight: spacing.x4 },
  headingRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    flexWrap: 'wrap',
    gap: spacing.x3,
  },
  filters: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing.x2 },
  search: {
    minHeight: touchTarget,
    borderWidth: 1,
    borderRadius: radii.compact,
    paddingLeft: spacing.x3,
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing.x2,
  },
  searchInput: { flex: 1, minHeight: touchTarget, ...typography.body, paddingVertical: 0 },
  clear: { width: touchTarget, height: touchTarget, alignItems: 'center', justifyContent: 'center' },
  list: { gap: spacing.x3 },
  card: { padding: spacing.x4, gap: spacing.x3 },
  cardHeader: { flexDirection: 'row', alignItems: 'center', gap: spacing.x3 },
  itemIcon: { width: 48, height: 48, borderRadius: 24, alignItems: 'center', justifyContent: 'center' },
  itemTitle: { ...typography.title, fontSize: 18, lineHeight: 24 },
  metrics: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: spacing.x4,
    padding: spacing.x3,
    borderRadius: radii.compact,
  },
  metric: { flexGrow: 1, minWidth: 140, gap: spacing.x1 },
  amount: { ...typography.title, fontSize: 19, lineHeight: 24 },
  actions: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing.x2 },
  action: { flexGrow: 1, flexBasis: 150 },
  barcodeRow: { flexDirection: 'row', alignItems: 'flex-end', flexWrap: 'wrap', gap: spacing.x3 },
  barcodeAction: { minWidth: 160, marginBottom: spacing.x3 },
  modalBackdrop: { flex: 1, backgroundColor: 'rgba(11,28,48,0.58)', justifyContent: 'flex-end' },
  modal: {
    maxHeight: '92%',
    borderTopLeftRadius: radii.feature,
    borderTopRightRadius: radii.feature,
    borderWidth: StyleSheet.hairlineWidth,
  },
  formContent: { padding: spacing.x5, gap: spacing.x4, paddingBottom: spacing.x8 },
  statusSection: { gap: spacing.x2 },
});
