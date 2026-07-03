import React, { useCallback, useState } from 'react';
import { Alert, ScrollView, StyleSheet, TouchableOpacity, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import * as ImagePicker from 'expo-image-picker';
import { useRouter } from 'expo-router';

import { AppIcon } from '@/components/app-icon';
import { AppCard } from '@/components/ui/app-card';
import { PrimaryButton } from '@/components/ui/primary-button';
import { TextField } from '@/components/ui/text-field';
import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { Radius, Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';
import { appConfig } from '@/utils/app-config';
import { submitCaptainOnboarding } from '@/services/captain-onboarding';
import { useAuth } from '@/context/AuthContext';

type DocKey = 'aadharFront' | 'aadharBack' | 'license' | 'rc' | 'selfie';

const DOC_FIELDS: { key: DocKey; label: string }[] = [
  { key: 'aadharFront', label: 'Aadhar (front)' },
  { key: 'aadharBack', label: 'Aadhar (back)' },
  { key: 'license', label: 'Driving license' },
  { key: 'rc', label: 'Vehicle RC' },
  { key: 'selfie', label: 'Selfie verification' },
];

export default function CaptainOnboardingScreen() {
  const theme = useTheme();
  const router = useRouter();
  const { session } = useAuth();
  const [vehicleNumber, setVehicleNumber] = useState('');
  const [bankAccount, setBankAccount] = useState('');
  const [ifsc, setIfsc] = useState('');
  const [docs, setDocs] = useState<Partial<Record<DocKey, string>>>({});
  const [submitting, setSubmitting] = useState(false);

  const pickDoc = useCallback(async (key: DocKey) => {
    const permission = await ImagePicker.requestMediaLibraryPermissionsAsync();
    if (!permission.granted) {
      Alert.alert('Permission needed', 'Allow photo access to upload documents.');
      return;
    }
    const result = await ImagePicker.launchImageLibraryAsync({ mediaTypes: ['images'], quality: 0.8 });
    if (result.canceled || !result.assets[0]) return;
    setDocs((current) => ({ ...current, [key]: result.assets[0].uri }));
  }, []);

  const handleSubmit = useCallback(async () => {
    if (!vehicleNumber.trim() || !bankAccount.trim() || !ifsc.trim()) {
      Alert.alert('Missing details', 'Vehicle number and bank details are required.');
      return;
    }
    const missing = DOC_FIELDS.filter((field) => !docs[field.key]);
    if (missing.length > 0) {
      Alert.alert('Documents needed', `Upload: ${missing.map((m) => m.label).join(', ')}`);
      return;
    }

    setSubmitting(true);
    try {
      const documentEntries = Object.entries(docs).map(([key, url]) => ({
        docType: key,
        docUrl: url,
      }));
      await submitCaptainOnboarding(
        {
          vehicleType: 'BIKE',
          vehicleNumber: vehicleNumber.trim(),
          bankAccount: bankAccount.trim(),
          bankIfsc: ifsc.trim(),
          licenseDocUrl: docs.license,
          selfieDocUrl: docs.selfie,
          documents: documentEntries,
        },
        session?.access_token,
      );
      Alert.alert('Submitted', 'Captain profile sent for Super Admin approval.');
      router.back();
    } catch (error) {
      Alert.alert('Could not submit', error instanceof Error ? error.message : 'Try again.');
    } finally {
      setSubmitting(false);
    }
  }, [bankAccount, docs, ifsc, router, vehicleNumber]);

  return (
    <ThemedView style={styles.container}>
      <SafeAreaView style={styles.safeArea}>
        <ScrollView contentContainerStyle={styles.content} showsVerticalScrollIndicator={false}>
          <View style={[styles.hero, { backgroundColor: theme.ctaSoft, borderColor: theme.border }]}>
            <View style={[styles.iconWrap, { backgroundColor: theme.cta }]}>
              <AppIcon name="truck" color="#FFFFFF" size={28} />
            </View>
            <ThemedText style={styles.title}>Captain onboarding</ThemedText>
            <ThemedText type="small" themeColor="textSecondary" style={styles.subtitle}>
              Upload vehicle, license, and bank documents for delivery partner approval.
            </ThemedText>
          </View>

          <AppCard>
            <View style={styles.form}>
              <TextField label="Vehicle number" placeholder="KA 01 AB 1234" value={vehicleNumber} onChangeText={setVehicleNumber} autoCapitalize="characters" />
              <TextField label="Bank account" placeholder="Account number" value={bankAccount} onChangeText={setBankAccount} keyboardType="number-pad" />
              <TextField label="IFSC" placeholder="HDFC0001234" value={ifsc} onChangeText={setIfsc} autoCapitalize="characters" />
            </View>
          </AppCard>

          <ThemedText style={styles.sectionTitle}>Required documents</ThemedText>
          {DOC_FIELDS.map((field) => (
            <TouchableOpacity
              key={field.key}
              style={[styles.docRow, { backgroundColor: theme.backgroundElement, borderColor: theme.border }]}
              onPress={() => void pickDoc(field.key)}
              accessibilityRole="button"
              accessibilityLabel={`Upload ${field.label}`}
            >
              <AppIcon name="shield" color={docs[field.key] ? theme.success : theme.textSecondary} size={20} />
              <View style={{ flex: 1 }}>
                <ThemedText style={{ fontWeight: '800' }}>{field.label}</ThemedText>
                <ThemedText type="small" themeColor="textSecondary">
                  {docs[field.key] ? 'Uploaded' : 'Tap to upload image'}
                </ThemedText>
              </View>
            </TouchableOpacity>
          ))}

          <PrimaryButton label="Submit for approval" onPress={() => void handleSubmit()} loading={submitting} />
          <PrimaryButton label="Back" onPress={() => router.back()} variant="ghost" />
        </ScrollView>
      </SafeAreaView>
    </ThemedView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1 },
  safeArea: { flex: 1 },
  content: { padding: Spacing.four, gap: Spacing.three, paddingBottom: Spacing.six },
  hero: {
    borderWidth: 1,
    borderRadius: Radius.xl,
    padding: Spacing.four,
    alignItems: 'center',
    gap: Spacing.two,
  },
  iconWrap: {
    width: 64,
    height: 64,
    borderRadius: 32,
    alignItems: 'center',
    justifyContent: 'center',
  },
  title: { fontSize: 24, fontWeight: '900' },
  subtitle: { textAlign: 'center' },
  form: { gap: Spacing.two },
  sectionTitle: { fontSize: 18, fontWeight: '900' },
  docRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.three,
    borderWidth: 1,
    borderRadius: Radius.lg,
    padding: Spacing.three,
    minHeight: 64,
  },
});
