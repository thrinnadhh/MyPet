import { Image } from 'expo-image';
import * as ImagePicker from 'expo-image-picker';
import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { Alert, ScrollView, StyleSheet, View } from 'react-native';

import {
  ActionButton,
  AppBar,
  FeedbackBanner,
  FilterChip,
  RoleBadge,
  SectionHeader,
  StateView,
} from '@/components/foundation/primitives';
import { ScreenShell } from '@/components/foundation/screen-shell';
import { ThemedText } from '@/components/themed-text';
import { AppCard } from '@/components/ui/app-card';
import { useAuth } from '@/context/AuthContext';
import { radii, spacing, touchTarget } from '@/design/tokens';
import {
  fetchMerchantOfferings,
  fetchMerchantProviders,
  uploadMerchantOfferingImage,
  type MerchantOffering,
  type MerchantProvider,
} from '@/services/merchant-inventory';

const MAX_IMAGE_BYTES = 5 * 1024 * 1024;
const SUPPORTED_MIME_TYPES = new Set(['image/jpeg', 'image/png', 'image/webp']);

type SupportedMimeType = 'image/jpeg' | 'image/png' | 'image/webp';

function normalizedMimeType(value: string | null | undefined, filename: string): SupportedMimeType | null {
  const normalized = value?.toLowerCase();
  if (normalized && SUPPORTED_MIME_TYPES.has(normalized)) return normalized as SupportedMimeType;
  const lower = filename.toLowerCase();
  if (lower.endsWith('.png')) return 'image/png';
  if (lower.endsWith('.webp')) return 'image/webp';
  if (lower.endsWith('.jpg') || lower.endsWith('.jpeg')) return 'image/jpeg';
  return null;
}

export default function CatalogMediaScreen() {
  const { providerId } = useAuth();
  const [providers, setProviders] = useState<MerchantProvider[]>([]);
  const [selectedProviderId, setSelectedProviderId] = useState<string | null>(providerId);
  const [offerings, setOfferings] = useState<MerchantOffering[]>([]);
  const [loading, setLoading] = useState(true);
  const [uploadingId, setUploadingId] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);

  const selectedProvider = useMemo(
    () => providers.find((provider) => provider.providerId === selectedProviderId) ?? null,
    [providers, selectedProviderId],
  );

  const loadProviders = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const nextProviders = await fetchMerchantProviders();
      setProviders(nextProviders);
      setSelectedProviderId((current) => {
        if (current && nextProviders.some((provider) => provider.providerId === current)) return current;
        if (providerId && nextProviders.some((provider) => provider.providerId === providerId)) return providerId;
        return nextProviders[0]?.providerId ?? null;
      });
    } catch (loadError) {
      setProviders([]);
      setSelectedProviderId(null);
      setError(loadError instanceof Error ? loadError.message : 'Could not load merchant businesses.');
    } finally {
      setLoading(false);
    }
  }, [providerId]);

  const loadOfferings = useCallback(async () => {
    if (!selectedProviderId) {
      setOfferings([]);
      return;
    }
    setLoading(true);
    setError(null);
    try {
      setOfferings(await fetchMerchantOfferings(selectedProviderId));
    } catch (loadError) {
      setOfferings([]);
      setError(loadError instanceof Error ? loadError.message : 'Could not load catalog offerings.');
    } finally {
      setLoading(false);
    }
  }, [selectedProviderId]);

  useEffect(() => {
    void loadProviders();
  }, [loadProviders]);

  useEffect(() => {
    void loadOfferings();
  }, [loadOfferings]);

  const pickAndUpload = useCallback(async (offering: MerchantOffering) => {
    if (!offering.offeringId) return;
    setSuccess(null);
    setError(null);

    const permission = await ImagePicker.requestMediaLibraryPermissionsAsync();
    if (!permission.granted) {
      Alert.alert('Photo access required', 'Allow photo access to choose a customer-visible catalog image.');
      return;
    }

    const result = await ImagePicker.launchImageLibraryAsync({
      mediaTypes: ['images'],
      quality: 0.9,
      allowsMultipleSelection: false,
    });
    if (result.canceled || result.assets.length === 0) return;

    const asset = result.assets[0];
    const filename = asset.fileName ?? `offering-${offering.offeringId}.jpg`;
    const mimeType = normalizedMimeType(asset.mimeType, filename);
    if (!mimeType) {
      Alert.alert('Unsupported image', 'Choose a JPEG, PNG or WebP image.');
      return;
    }
    if (asset.fileSize && asset.fileSize > MAX_IMAGE_BYTES) {
      Alert.alert('Image too large', 'Catalog images must be 5 MB or smaller.');
      return;
    }

    setUploadingId(offering.offeringId);
    try {
      const updated = await uploadMerchantOfferingImage(
        offering.offeringId,
        asset.uri,
        filename,
        mimeType,
      );
      setOfferings((current) => current.map((item) =>
        item.offeringId === updated.offeringId ? updated : item,
      ));
      setSuccess(`${updated.name} now has a customer-visible catalog image.`);
    } catch (uploadError) {
      setError(uploadError instanceof Error ? uploadError.message : 'Could not upload catalog image.');
    } finally {
      setUploadingId(null);
    }
  }, []);

  return (
    <ScreenShell
      header={
        <AppBar
          eyebrow="MERCHANT WORKSPACE"
          title="Catalog media"
          subtitle="Publish customer-visible product and service images"
          action={<RoleBadge role="merchant" />}
        />
      }
      testID="merchant-catalog-media"
    >
      <FeedbackBanner
        tone="info"
        title="Public catalog images only"
        message="Use this workspace for product/service photos. Verification, KYC and settlement documents stay in protected merchant-document storage and are never published here."
        icon="inventory"
      />

      {error ? (
        <FeedbackBanner tone="danger" title="Catalog media action failed" message={error} icon="dispute" />
      ) : null}
      {success ? (
        <FeedbackBanner tone="success" title="Catalog image updated" message={success} icon="inventory" />
      ) : null}

      {providers.length > 1 ? (
        <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.providerRow}>
          {providers.map((provider) => (
            <FilterChip
              key={provider.providerId}
              label={provider.name}
              selected={provider.providerId === selectedProviderId}
              onPress={() => setSelectedProviderId(provider.providerId)}
            />
          ))}
        </ScrollView>
      ) : null}

      <SectionHeader
        title={selectedProvider?.name ?? 'Catalog media'}
        subtitle="JPEG, PNG or WebP · maximum 5 MB · replacing an image removes the previous managed file"
      />

      {loading ? <StateView kind="loading" title="Loading catalog media" /> : null}
      {!loading && providers.length === 0 ? (
        <StateView
          kind="unauthorized"
          title="Approved business required"
          message="Complete merchant onboarding before publishing catalog media."
          actionLabel="Retry"
          onAction={() => void loadProviders()}
        />
      ) : null}
      {!loading && selectedProviderId && offerings.length === 0 ? (
        <StateView
          kind="empty"
          title="No offerings yet"
          message="Create products or services in Inventory before adding customer-visible images."
          actionLabel="Refresh"
          onAction={() => void loadOfferings()}
        />
      ) : null}

      <View style={styles.grid}>
        {offerings.map((offering) => (
          <AppCard key={offering.offeringId ?? `${offering.providerId}-${offering.name}`} style={styles.card}>
            {offering.imageUrl ? (
              <Image
                source={{ uri: offering.imageUrl }}
                style={styles.image}
                contentFit="cover"
                accessibilityLabel={`${offering.name} catalog image`}
              />
            ) : (
              <View style={styles.placeholder} accessibilityLabel={`${offering.name} has no catalog image`}>
                <ThemedText type="small" themeColor="textSecondary">No customer image</ThemedText>
              </View>
            )}
            <View style={styles.copy}>
              <ThemedText style={styles.title}>{offering.name}</ThemedText>
              <ThemedText type="small" themeColor="textSecondary">
                {offering.category || 'Uncategorised'} · {offering.status.replaceAll('_', ' ')}
              </ThemedText>
            </View>
            <ActionButton
              label={offering.imageUrl ? 'Replace image' : 'Add image'}
              icon="inventory"
              loading={uploadingId === offering.offeringId}
              disabled={!offering.offeringId || Boolean(uploadingId)}
              onPress={() => void pickAndUpload(offering)}
            />
            {offering.imageUrl ? (
              <View accessible accessibilityLabel={`Public customer image is active for ${offering.name}`} style={styles.urlHint}>
                <ThemedText type="small" themeColor="textSecondary" numberOfLines={1}>
                  Public URL active
                </ThemedText>
              </View>
            ) : null}
          </AppCard>
        ))}
      </View>
    </ScreenShell>
  );
}

const styles = StyleSheet.create({
  providerRow: { gap: spacing.x2, paddingVertical: spacing.x1 },
  grid: { gap: spacing.x4 },
  card: { gap: spacing.x3 },
  image: { width: '100%', aspectRatio: 16 / 9, borderRadius: radii.card },
  placeholder: {
    width: '100%',
    aspectRatio: 16 / 9,
    borderRadius: radii.card,
    alignItems: 'center',
    justifyContent: 'center',
    minHeight: touchTarget,
    opacity: 0.7,
  },
  copy: { gap: spacing.x1 },
  title: { fontWeight: '800' },
  urlHint: { minHeight: touchTarget, justifyContent: 'center' },
});
