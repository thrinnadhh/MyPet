import React, { useCallback, useMemo, useState } from 'react';
import {
  Alert,
  ScrollView,
  StyleSheet,
  Switch,
  TextInput,
  TouchableOpacity,
  useColorScheme,
  View,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { AppIcon } from '@/components/app-icon';
import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { Colors, Radius, Shadows, Spacing } from '@/constants/theme';
import { useAuth } from '@/context/AuthContext';
import { appConfig } from '@/utils/app-config';

interface PetProfile {
  id: string;
  name: string;
  breed: string;
  age: string;
  notes: string;
}

interface AddressDraft {
  label: string;
  line1: string;
  city: string;
  pincode: string;
}

const DEFAULT_PETS: PetProfile[] = [
  { id: 'pet-1', name: 'Bruno', breed: 'Golden Retriever', age: '3 years', notes: 'Chicken allergy' },
  { id: 'pet-2', name: 'Milo', breed: 'Indian Shorthair', age: '2 years', notes: 'Prefers evening grooming' },
];

const DOCUMENTS = [
  { id: 'doc-1', title: 'Vaccination record', status: 'Current', date: 'Valid until Aug 2026' },
  { id: 'doc-2', title: 'Prescription', status: 'Shared', date: 'VetCare Plus, Jun 2026' },
  { id: 'doc-3', title: 'Invoice archive', status: 'Ready', date: 'GST bills and receipts' },
];

function Section({
  title,
  children,
}: {
  title: string;
  children: React.ReactNode;
}) {
  const scheme = useColorScheme() === 'dark' ? 'dark' : 'light';
  const colors = Colors[scheme];

  return (
    <View style={styles.section}>
      <ThemedText style={[styles.sectionTitle, { color: colors.text }]}>{title}</ThemedText>
      {children}
    </View>
  );
}

export default function ProfileScreen() {
  const scheme = useColorScheme() === 'dark' ? 'dark' : 'light';
  const colors = Colors[scheme];
  const { user, session, signOut } = useAuth();

  const [pets, setPets] = useState(DEFAULT_PETS);
  const [address, setAddress] = useState<AddressDraft>({
    label: 'Home',
    line1: 'Indiranagar 12th Main',
    city: 'Bangalore',
    pincode: '560038',
  });
  const [remindersEnabled, setRemindersEnabled] = useState(true);
  const [savingAddress, setSavingAddress] = useState(false);

  const initials = useMemo(() => {
    const email = user?.email ?? 'customer@example.com';
    return email.slice(0, 1).toUpperCase();
  }, [user?.email]);

  const addPet = useCallback(() => {
    const nextNumber = pets.length + 1;
    setPets((current) => [
      ...current,
      {
        id: `pet-${Date.now()}`,
        name: `Pet ${nextNumber}`,
        breed: 'Add breed',
        age: 'Add age',
        notes: 'Add care notes',
      },
    ]);
  }, [pets.length]);

  const saveAddress = useCallback(async () => {
    if (!address.line1.trim() || !address.city.trim() || !address.pincode.trim()) {
      Alert.alert('Address incomplete', 'Line 1, city, and pincode are required.');
      return;
    }

    setSavingAddress(true);
    try {
      const headers: Record<string, string> = { 'Content-Type': 'application/json' };
      if (session?.access_token) headers.Authorization = `Bearer ${session.access_token}`;

      const response = await fetch(`${appConfig.apiBaseUrl}/api/v1/addresses`, {
        method: 'POST',
        headers,
        body: JSON.stringify({
          label: address.label,
          line1: address.line1,
          city: address.city,
          pincode: address.pincode,
          isDefault: true,
        }),
      });

      if (!response.ok && !appConfig.allowDemoMode) {
        throw new Error('Address service is unavailable.');
      }

      Alert.alert('Address saved', appConfig.allowDemoMode ? 'Saved locally for demo mode.' : 'Default delivery address updated.');
    } catch (error: any) {
      Alert.alert('Could not save address', error?.message ?? 'Please try again.');
    } finally {
      setSavingAddress(false);
    }
  }, [address, session]);

  return (
    <ThemedView style={[styles.container, { backgroundColor: colors.background }]}>
      <SafeAreaView style={styles.safeArea}>
        <ScrollView showsVerticalScrollIndicator={false} contentContainerStyle={styles.scrollContent}>
          <View style={[styles.hero, { backgroundColor: colors.backgroundElement, borderColor: colors.border }]}>
            <View style={[styles.avatar, { backgroundColor: colors.primary }]}>
              <ThemedText style={styles.avatarText}>{initials}</ThemedText>
            </View>
            <View style={styles.heroText}>
              <ThemedText style={[styles.name, { color: colors.text }]}>Pet parent account</ThemedText>
              <ThemedText type="small" style={{ color: colors.textSecondary }} numberOfLines={1}>
                {user?.email ?? 'Signed in customer'}
              </ThemedText>
            </View>
            <TouchableOpacity
              style={[styles.iconButton, { borderColor: colors.border }]}
              onPress={() => Alert.alert('Support', 'Support workflow opens from the launch support center.')}
              accessibilityRole="button"
              accessibilityLabel="Open support"
            >
              <AppIcon name="medical" color={colors.cta} size={20} />
            </TouchableOpacity>
          </View>

          <Section title="Pets">
            {pets.map((pet) => (
              <View key={pet.id} style={[styles.card, { backgroundColor: colors.backgroundElement, borderColor: colors.border }]}>
                <View style={styles.cardHeader}>
                  <View style={styles.inlineTitle}>
                    <AppIcon name="paw" color={colors.primary} size={18} />
                    <ThemedText style={[styles.cardTitle, { color: colors.text }]}>{pet.name}</ThemedText>
                  </View>
                  <ThemedText type="small" style={{ color: colors.textSecondary }}>{pet.age}</ThemedText>
                </View>
                <ThemedText type="small" style={{ color: colors.textSecondary }}>{pet.breed}</ThemedText>
                <ThemedText type="small" style={{ color: colors.text }}>{pet.notes}</ThemedText>
              </View>
            ))}
            <TouchableOpacity
              style={[styles.secondaryButton, { borderColor: colors.primary }]}
              onPress={addPet}
              accessibilityRole="button"
              accessibilityLabel="Add pet profile"
            >
              <ThemedText style={{ color: colors.primary, fontWeight: '800' }}>Add pet profile</ThemedText>
            </TouchableOpacity>
          </Section>

          <Section title="Default Address">
            <View style={[styles.formCard, { backgroundColor: colors.backgroundElement, borderColor: colors.border }]}>
              <TextInput
                value={address.label}
                onChangeText={(label) => setAddress((current) => ({ ...current, label }))}
                placeholder="Label"
                placeholderTextColor={colors.textSecondary}
                style={[styles.input, { color: colors.text, borderColor: colors.border }]}
              />
              <TextInput
                value={address.line1}
                onChangeText={(line1) => setAddress((current) => ({ ...current, line1 }))}
                placeholder="Address line"
                placeholderTextColor={colors.textSecondary}
                style={[styles.input, { color: colors.text, borderColor: colors.border }]}
              />
              <View style={styles.row}>
                <TextInput
                  value={address.city}
                  onChangeText={(city) => setAddress((current) => ({ ...current, city }))}
                  placeholder="City"
                  placeholderTextColor={colors.textSecondary}
                  style={[styles.input, styles.flexInput, { color: colors.text, borderColor: colors.border }]}
                />
                <TextInput
                  value={address.pincode}
                  onChangeText={(pincode) => setAddress((current) => ({ ...current, pincode }))}
                  placeholder="Pincode"
                  placeholderTextColor={colors.textSecondary}
                  keyboardType="number-pad"
                  style={[styles.input, styles.pinInput, { color: colors.text, borderColor: colors.border }]}
                />
              </View>
              <TouchableOpacity
                style={[styles.primaryButton, { backgroundColor: colors.cta }]}
                onPress={saveAddress}
                disabled={savingAddress}
                accessibilityRole="button"
                accessibilityLabel="Save default address"
              >
                <ThemedText style={styles.primaryButtonText}>{savingAddress ? 'Saving...' : 'Save default address'}</ThemedText>
              </TouchableOpacity>
            </View>
          </Section>

          <Section title="Documents">
            {DOCUMENTS.map((doc) => (
              <View key={doc.id} style={[styles.listRow, { backgroundColor: colors.backgroundElement, borderColor: colors.border }]}>
                <View style={styles.inlineTitle}>
                  <AppIcon name="calendar" color={colors.accent} size={18} />
                  <View>
                    <ThemedText style={[styles.cardTitle, { color: colors.text }]}>{doc.title}</ThemedText>
                    <ThemedText type="small" style={{ color: colors.textSecondary }}>{doc.date}</ThemedText>
                  </View>
                </View>
                <View style={[styles.statusPill, { backgroundColor: colors.muted }]}>
                  <ThemedText type="small" style={{ color: colors.text, fontWeight: '800' }}>{doc.status}</ThemedText>
                </View>
              </View>
            ))}
          </Section>

          <Section title="Preferences">
            <View style={[styles.listRow, { backgroundColor: colors.backgroundElement, borderColor: colors.border }]}>
              <View>
                <ThemedText style={[styles.cardTitle, { color: colors.text }]}>Appointment reminders</ThemedText>
                <ThemedText type="small" style={{ color: colors.textSecondary }}>Push reminders for visits and deliveries</ThemedText>
              </View>
              <Switch value={remindersEnabled} onValueChange={setRemindersEnabled} />
            </View>
            <View style={[styles.policyGrid, { borderColor: colors.border }]}>
              {['Terms', 'Privacy', 'Refunds', 'Disputes'].map((item) => (
                <TouchableOpacity key={item} style={[styles.policyButton, { borderColor: colors.border }]}>
                  <ThemedText type="small" style={{ color: colors.text, fontWeight: '800' }}>{item}</ThemedText>
                </TouchableOpacity>
              ))}
            </View>
          </Section>

          <TouchableOpacity
            style={[styles.signOutButton, { borderColor: colors.danger }]}
            onPress={signOut}
            accessibilityRole="button"
            accessibilityLabel="Sign out"
          >
            <ThemedText style={{ color: colors.danger, fontWeight: '800' }}>Sign out</ThemedText>
          </TouchableOpacity>
        </ScrollView>
      </SafeAreaView>
    </ThemedView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1 },
  safeArea: { flex: 1 },
  scrollContent: {
    padding: Spacing.four,
    paddingBottom: Spacing.six,
    gap: Spacing.four,
  },
  hero: {
    minHeight: 92,
    borderRadius: Radius.lg,
    borderWidth: 1,
    padding: Spacing.three,
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.three,
    ...Shadows.card,
  },
  avatar: {
    width: 56,
    height: 56,
    borderRadius: 28,
    alignItems: 'center',
    justifyContent: 'center',
  },
  avatarText: { color: '#ffffff', fontWeight: '900', fontSize: 22 },
  heroText: { flex: 1 },
  name: { fontSize: 18, fontWeight: '900' },
  iconButton: {
    width: 48,
    height: 48,
    borderRadius: 24,
    borderWidth: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
  section: { gap: Spacing.two },
  sectionTitle: { fontSize: 18, fontWeight: '900' },
  card: {
    borderWidth: 1,
    borderRadius: Radius.lg,
    padding: Spacing.three,
    gap: Spacing.one,
  },
  cardHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    gap: Spacing.two,
  },
  inlineTitle: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.two,
    flex: 1,
  },
  cardTitle: { fontWeight: '900' },
  secondaryButton: {
    minHeight: 48,
    borderRadius: Radius.md,
    borderWidth: 1.5,
    alignItems: 'center',
    justifyContent: 'center',
  },
  formCard: {
    borderWidth: 1,
    borderRadius: Radius.lg,
    padding: Spacing.three,
    gap: Spacing.two,
  },
  input: {
    minHeight: 48,
    borderWidth: 1,
    borderRadius: Radius.sm,
    paddingHorizontal: Spacing.three,
    fontSize: 14,
  },
  row: { flexDirection: 'row', gap: Spacing.two },
  flexInput: { flex: 1 },
  pinInput: { width: 116 },
  primaryButton: {
    minHeight: 48,
    borderRadius: Radius.md,
    alignItems: 'center',
    justifyContent: 'center',
  },
  primaryButtonText: { color: '#ffffff', fontWeight: '900' },
  listRow: {
    minHeight: 72,
    borderWidth: 1,
    borderRadius: Radius.lg,
    padding: Spacing.three,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    gap: Spacing.two,
  },
  statusPill: {
    minHeight: 32,
    borderRadius: 16,
    paddingHorizontal: Spacing.two,
    alignItems: 'center',
    justifyContent: 'center',
  },
  policyGrid: {
    borderWidth: 1,
    borderRadius: Radius.lg,
    padding: Spacing.two,
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: Spacing.two,
  },
  policyButton: {
    minHeight: 44,
    width: '48%',
    borderWidth: 1,
    borderRadius: Radius.sm,
    alignItems: 'center',
    justifyContent: 'center',
  },
  signOutButton: {
    minHeight: 48,
    borderRadius: Radius.md,
    borderWidth: 1.5,
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: Spacing.four,
  },
});
