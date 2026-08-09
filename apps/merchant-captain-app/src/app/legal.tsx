import * as WebBrowser from 'expo-web-browser';
import { useRouter } from 'expo-router';
import React, { useCallback } from 'react';
import { Alert, ScrollView, StyleSheet, TouchableOpacity, useColorScheme, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { Colors, Radius, Spacing } from '@/constants/theme';
import { appConfig } from '@/utils/app-config';

const SECTIONS = [
  {
    title: 'Terms',
    body: 'Partners must maintain accurate provider, inventory, appointment, delivery, billing, tax, payout, and support information. Admin actions are audited.',
  },
  {
    title: 'Privacy',
    body: 'Partner data includes profile, provider, catalog, booking, POS, payout, location, support, notification-token, and audit activity needed for platform operations. The canonical privacy policy below explains collection, use, retention, sharing, and deletion practices.',
  },
  {
    title: 'Account and data deletion',
    body: 'Merchant accounts can request account deletion from this screen. The external deletion resource remains available even after the app is uninstalled. MyPet may retain limited records only where legitimate legal, fraud-prevention, financial, or security obligations require it, as described in the privacy policy.',
  },
  {
    title: 'Refunds',
    body: 'Refunds are handled through dispute mode and support escalation. Automated refunds require authorized admin configuration and payment-service proof.',
  },
  {
    title: 'Launch',
    body: 'Production promotion remains blocked when payment, stock, appointment, authorization, privacy, deletion, device-QA, or release evidence is incomplete.',
  },
];

async function openRequiredResource(url: string | undefined, label: string) {
  if (!url) {
    Alert.alert(
      `${label} unavailable`,
      'This build is missing the required production URL. Release builds are blocked until the resource is configured.',
    );
    return;
  }
  await WebBrowser.openBrowserAsync(url);
}

export default function LegalScreen() {
  const scheme = useColorScheme() === 'dark' ? 'dark' : 'light';
  const colors = Colors[scheme];
  const router = useRouter();

  const openPrivacyPolicy = useCallback(
    () => void openRequiredResource(appConfig.privacyPolicyUrl, 'Privacy policy'),
    [],
  );
  const openAccountDeletion = useCallback(
    () => void openRequiredResource(appConfig.accountDeletionUrl, 'Account deletion'),
    [],
  );

  return (
    <ThemedView style={[styles.container, { backgroundColor: colors.background }]}>
      <SafeAreaView style={styles.safeArea}>
        <ScrollView contentContainerStyle={styles.content}>
          <TouchableOpacity onPress={() => router.back()} style={[styles.backButton, { borderColor: colors.border }]}>
            <ThemedText style={{ color: colors.text, fontWeight: '900' }}>Back</ThemedText>
          </TouchableOpacity>
          <ThemedText style={[styles.title, { color: colors.text }]}>Privacy, Legal & Account</ThemedText>
          <ThemedText style={{ color: colors.textSecondary }}>
            Review the merchant terms and use the production resources below to manage privacy or request deletion of your MyPet Merchant account and associated data.
          </ThemedText>

          <View style={styles.actionStack}>
            <TouchableOpacity
              accessibilityRole="link"
              accessibilityLabel="Open MyPet Merchant privacy policy"
              onPress={openPrivacyPolicy}
              style={[styles.primaryAction, { backgroundColor: colors.text }]}
            >
              <ThemedText style={{ color: colors.background, fontWeight: '900' }}>Open Privacy Policy</ThemedText>
            </TouchableOpacity>
            <TouchableOpacity
              accessibilityRole="link"
              accessibilityLabel="Request deletion of MyPet Merchant account and data"
              onPress={openAccountDeletion}
              style={[styles.dangerAction, { borderColor: colors.border }]}
            >
              <ThemedText style={{ color: colors.text, fontWeight: '900' }}>Delete Account / Request Data Deletion</ThemedText>
            </TouchableOpacity>
          </View>

          {SECTIONS.map((section) => (
            <View key={section.title} style={[styles.card, { backgroundColor: colors.backgroundElement, borderColor: colors.border }]}>
              <ThemedText style={[styles.cardTitle, { color: colors.text }]}>{section.title}</ThemedText>
              <ThemedText style={{ color: colors.textSecondary }}>{section.body}</ThemedText>
            </View>
          ))}
        </ScrollView>
      </SafeAreaView>
    </ThemedView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1 },
  safeArea: { flex: 1 },
  content: { padding: Spacing.four, gap: Spacing.three },
  actionStack: { gap: Spacing.two },
  backButton: {
    alignSelf: 'flex-start',
    minHeight: 44,
    borderRadius: Radius.md,
    borderWidth: 1,
    paddingHorizontal: Spacing.three,
    justifyContent: 'center',
  },
  primaryAction: {
    minHeight: 52,
    borderRadius: Radius.md,
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: Spacing.three,
  },
  dangerAction: {
    minHeight: 52,
    borderRadius: Radius.md,
    borderWidth: 1,
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: Spacing.three,
  },
  title: { fontSize: 24, fontWeight: '900' },
  card: {
    borderWidth: 1,
    borderRadius: Radius.lg,
    padding: Spacing.three,
    gap: Spacing.one,
  },
  cardTitle: { fontSize: 18, fontWeight: '900' },
});
