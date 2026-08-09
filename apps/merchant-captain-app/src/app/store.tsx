import * as Location from 'expo-location';
import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { Alert, ScrollView, StyleSheet, View } from 'react-native';

import {
  ActionButton,
  AppBar,
  FeedbackBanner,
  FilterChip,
  RoleBadge,
  StateView,
  StatusBadge,
} from '@/components/foundation/primitives';
import { ScreenShell } from '@/components/foundation/screen-shell';
import { ThemedText } from '@/components/themed-text';
import { AppCard } from '@/components/ui/app-card';
import { TextField } from '@/components/ui/text-field';
import { useAuth } from '@/context/AuthContext';
import { spacing, typography } from '@/design/tokens';
import {
  fetchMerchantStores,
  updateMerchantStore,
  type MerchantStoreProfile,
} from '@/services/merchant-store';
import { formatPercentage, formatStatusLabel } from '@/utils/formatters';

type StoreForm = {
  name: string;
  description: string;
  addressLine: string;
  city: string;
  pincode: string;
  longitude: string;
  latitude: string;
};

const EMPTY_FORM: StoreForm = {
  name: '',
  description: '',
  addressLine: '',
  city: '',
  pincode: '',
  longitude: '',
  latitude: '',
};

function formFromStore(store: MerchantStoreProfile): StoreForm {
  return {
    name: store.name,
    description: store.description ?? '',
    addressLine: store.addressLine,
    city: store.city,
    pincode: store.pincode,
    longitude: String(store.longitude),
    latitude: String(store.latitude),
  };
}

function statusTone(status: MerchantStoreProfile['status']): 'success' | 'warning' | 'danger' | 'neutral' {
  if (status === 'ACTIVE') return 'success';
  if (status === 'SUSPENDED' || status === 'REJECTED') return 'danger';
  if (status === 'PENDING_APPROVAL' || status === 'INFO_REQUESTED') return 'warning';
  return 'neutral';
}

export default function MerchantStoreScreen() {
  const { activeRole } = useAuth();
  const [stores, setStores] = useState<MerchantStoreProfile[]>([]);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [form, setForm] = useState<StoreForm>(EMPTY_FORM);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [locating, setLocating] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const selected = useMemo(
    () => stores.find((store) => store.providerId === selectedId) ?? null,
    [selectedId, stores],
  );

  const selectStore = useCallback((store: MerchantStoreProfile) => {
    setSelectedId(store.providerId);
    setForm(formFromStore(store));
    setError(null);
  }, []);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const next = await fetchMerchantStores();
      setStores(next);
      const preferred = next.find((store) => store.providerId === selectedId) ?? next[0] ?? null;
      if (preferred) {
        setSelectedId(preferred.providerId);
        setForm(formFromStore(preferred));
      } else {
        setSelectedId(null);
        setForm(EMPTY_FORM);
      }
      setError(null);
    } catch (cause: unknown) {
      setError(cause instanceof Error ? cause.message : 'Could not load your business profile.');
      setStores([]);
      setSelectedId(null);
    } finally {
      setLoading(false);
    }
  }, [selectedId]);

  useEffect(() => {
    void load();
    // Initial provider hydration only. Subsequent store changes are controlled by selectStore/save.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const useCurrentLocation = useCallback(async () => {
    setLocating(true);
    try {
      const permission = await Location.requestForegroundPermissionsAsync();
      if (permission.status !== 'granted') {
        Alert.alert(
          'Location permission required',
          'Allow foreground location while editing the business profile, or enter verified coordinates manually.',
        );
        return;
      }
      const current = await Location.getCurrentPositionAsync({ accuracy: Location.Accuracy.High });
      setForm((value) => ({
        ...value,
        longitude: current.coords.longitude.toFixed(6),
        latitude: current.coords.latitude.toFixed(6),
      }));
    } catch (cause: unknown) {
      Alert.alert('Location unavailable', cause instanceof Error ? cause.message : 'Could not read this device location.');
    } finally {
      setLocating(false);
    }
  }, []);

  const save = useCallback(async () => {
    if (!selected) return;
    const name = form.name.trim();
    const addressLine = form.addressLine.trim();
    const city = form.city.trim();
    const pincode = form.pincode.trim();
    const longitude = Number(form.longitude);
    const latitude = Number(form.latitude);

    if (!name || !addressLine || !city) {
      Alert.alert('Missing business details', 'Business name, address and city are required.');
      return;
    }
    if (!/^[1-9]\d{5}$/.test(pincode)) {
      Alert.alert('Invalid pincode', 'Enter a valid 6-digit Indian pincode.');
      return;
    }
    if (!Number.isFinite(longitude) || longitude < -180 || longitude > 180 || !Number.isFinite(latitude) || latitude < -90 || latitude > 90) {
      Alert.alert('Invalid location', 'Capture or enter valid business longitude and latitude.');
      return;
    }

    setSaving(true);
    try {
      const updated = await updateMerchantStore(selected.providerId, {
        name,
        description: form.description.trim() || null,
        addressLine,
        city,
        pincode,
        longitude,
        latitude,
      });
      setStores((current) => current.map((store) => store.providerId === updated.providerId ? updated : store));
      setForm(formFromStore(updated));
      setError(null);
      Alert.alert('Business profile saved', 'Customer-visible store details were updated on the server.');
    } catch (cause: unknown) {
      setError(cause instanceof Error ? cause.message : 'Could not save the business profile.');
    } finally {
      setSaving(false);
    }
  }, [form, selected]);

  if (activeRole !== 'PROVIDER') {
    return (
      <ScreenShell scroll={false} header={<AppBar title="Store profile" />}>
        <StateView kind="unauthorized" title="Merchant access required" message="Only the owning merchant can manage a provider profile." />
      </ScreenShell>
    );
  }

  return (
    <ScreenShell
      header={
        <AppBar
          eyebrow="MERCHANT SETTINGS"
          title="Business profile"
          subtitle="Server-persisted customer-visible store details"
          action={<RoleBadge role="merchant" />}
        />
      }
      testID="merchant-store-profile"
    >
      {loading ? <StateView kind="loading" title="Loading businesses" /> : null}
      {error ? <StateView kind="error" title="Business profile needs attention" message={error} actionLabel="Retry" onAction={() => void load()} /> : null}
      {!loading && !error && stores.length === 0 ? (
        <StateView kind="empty" title="No provider profile" message="Complete merchant onboarding before managing your customer-visible business profile." />
      ) : null}

      {stores.length > 0 ? (
        <>
          <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.storeRow}>
            {stores.map((store) => (
              <FilterChip
                key={store.providerId}
                label={store.name}
                selected={selectedId === store.providerId}
                onPress={() => selectStore(store)}
                icon="store"
              />
            ))}
          </ScrollView>

          {selected ? (
            <>
              <AppCard style={styles.summaryCard}>
                <View style={styles.summaryHeader}>
                  <View style={styles.flex}>
                    <ThemedText style={styles.title}>{selected.name}</ThemedText>
                    <ThemedText type="small" themeColor="textSecondary">
                      {formatStatusLabel(selected.providerType)} · {formatStatusLabel(selected.fulfillmentType)}
                    </ThemedText>
                  </View>
                  <StatusBadge label={formatStatusLabel(selected.status)} tone={statusTone(selected.status)} />
                </View>
                <ThemedText type="small" themeColor="textSecondary">
                  Rating {Number(selected.ratingAvg).toFixed(2)} ({selected.ratingCount}) · commission {formatPercentage(Number(selected.commissionPct), 2)}
                </ThemedText>
              </AppCard>

              <FeedbackBanner
                tone="info"
                title="Trust-sensitive fields are locked"
                message="Provider type, fulfilment type, approval status, commission and licence identity cannot be changed from this form. Verification documents continue through the controlled onboarding/document flow."
                icon="shield"
              />

              <AppCard style={styles.formCard}>
                <ThemedText style={styles.title}>Customer-visible details</ThemedText>
                <TextField label="Business name" value={form.name} onChangeText={(name) => setForm((value) => ({ ...value, name }))} />
                <TextField label="Description" value={form.description} onChangeText={(description) => setForm((value) => ({ ...value, description }))} multiline />
                <TextField label="Street address" value={form.addressLine} onChangeText={(addressLine) => setForm((value) => ({ ...value, addressLine }))} />
                <View style={styles.fieldRow}>
                  <View style={styles.flex}>
                    <TextField label="City" value={form.city} onChangeText={(city) => setForm((value) => ({ ...value, city }))} />
                  </View>
                  <View style={styles.pincodeField}>
                    <TextField label="Pincode" keyboardType="number-pad" value={form.pincode} onChangeText={(pincode) => setForm((value) => ({ ...value, pincode }))} />
                  </View>
                </View>
                <View style={styles.fieldRow}>
                  <View style={styles.flex}>
                    <TextField label="Longitude" keyboardType="numeric" value={form.longitude} onChangeText={(longitude) => setForm((value) => ({ ...value, longitude }))} />
                  </View>
                  <View style={styles.flex}>
                    <TextField label="Latitude" keyboardType="numeric" value={form.latitude} onChangeText={(latitude) => setForm((value) => ({ ...value, latitude }))} />
                  </View>
                </View>
                <View style={styles.actions}>
                  <ActionButton label={locating ? 'Locating…' : 'Use current location'} variant="secondary" icon="location" disabled={locating || saving} onPress={() => void useCurrentLocation()} />
                  <ActionButton label="Save profile" icon="check" loading={saving} disabled={locating} onPress={() => void save()} />
                </View>
              </AppCard>
            </>
          ) : null}
        </>
      ) : null}
    </ScreenShell>
  );
}

const styles = StyleSheet.create({
  storeRow: { gap: spacing.x2, paddingRight: spacing.x4 },
  summaryCard: { gap: spacing.x2 },
  summaryHeader: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: spacing.x3 },
  formCard: { gap: spacing.x3 },
  fieldRow: { flexDirection: 'row', alignItems: 'flex-start', gap: spacing.x3 },
  actions: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing.x2 },
  flex: { flex: 1 },
  pincodeField: { width: 150 },
  title: { ...typography.title },
});
