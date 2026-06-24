import React, { useState, useCallback } from 'react';
import { StyleSheet, View, TextInput, TouchableOpacity, ScrollView, Alert, Platform } from 'react-native';

import { SafeAreaView } from 'react-native-safe-area-context';
import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { Spacing, Colors } from '@/constants/theme';
import { useColorScheme } from 'react-native';

type ProviderType = 'PET_STORE' | 'VET_HOSPITAL' | 'GROOMING_CENTER';

const API_BASE_URL = Platform.select({
  android: 'http://10.0.2.2:8080',
  ios: 'http://localhost:8080',
  default: 'http://localhost:8080',
});

export default function OnboardingScreen() {
  const scheme = useColorScheme() === 'dark' ? 'dark' : 'light';
  const colors = Colors[scheme];

  const [providerType, setProviderType] = useState<ProviderType>('PET_STORE');
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [addressLine, setAddressLine] = useState('');
  const [city, setCity] = useState('');
  const [pincode, setPincode] = useState('');
  const [licenseNumber, setLicenseNumber] = useState('');
  const [docUploaded, setDocUploaded] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  const handleDocUpload = useCallback(() => {
    Alert.alert('Upload Document', 'Select document from camera or gallery', [
      { text: 'Camera', onPress: () => setDocUploaded(true) },
      { text: 'Gallery', onPress: () => setDocUploaded(true) },
      { text: 'Cancel', style: 'cancel' }
    ]);
  }, []);

  const handleSubmit = useCallback(async () => {
    if (!name.trim() || !addressLine.trim() || !city.trim() || !pincode.trim()) {
      Alert.alert('Error', 'Please fill all mandatory fields.');
      return;
    }

    if (providerType === 'VET_HOSPITAL' && !licenseNumber.trim()) {
      Alert.alert('Error', 'Veterinary Council License number is required.');
      return;
    }

    if (!docUploaded) {
      Alert.alert('Error', 'Please upload required verification documents.');
      return;
    }

    setSubmitting(true);
    
    try {
      const response = await fetch(`${API_BASE_URL}/api/v1/providers`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          ownerUserId: 'd3b07384-d113-4e4e-9c8e-3d8e3d8e3d8e', // Mock Merchant User ID
          providerType,
          fulfillmentType: providerType === 'PET_STORE' ? 'DELIVERY' : 'APPOINTMENT',
          name,
          description,
          licenseNumber: providerType === 'VET_HOSPITAL' ? licenseNumber : null,
          licenseDocUrl: 'https://supabase.storage/pawsnearme/lic_' + Date.now() + '.pdf',
          addressLine,
          city,
          pincode,
          longitude: 77.5946, // Bangalore Center
          latitude: 12.9716,
        }),
      });

      const data = await response.json();
      
      if (response.ok) {
        // Trigger status transition to PENDING_APPROVAL
        const submitResponse = await fetch(`${API_BASE_URL}/api/v1/providers/${data.providerId}/submit`, {
          method: 'POST',
        });

        if (submitResponse.ok) {
          Alert.alert('Success', 'Provider application submitted successfully and is PENDING APPROVAL.');
          // Reset form
          setName('');
          setDescription('');
          setAddressLine('');
          setCity('');
          setPincode('');
          setLicenseNumber('');
          setDocUploaded(false);
        } else {
          Alert.alert('Error', 'Failed to submit application for approval.');
        }
      } else {
        Alert.alert('Error', data.error || 'Failed to create provider application.');
      }
    } catch (err) {
      Alert.alert('Error', 'Cannot connect to backend service. Please verify server status.');
    } finally {
      setSubmitting(false);
    }
  }, [providerType, name, description, addressLine, city, pincode, licenseNumber, docUploaded]);

  return (
    <ThemedView style={styles.container}>
      <SafeAreaView style={styles.safeArea}>
        <ScrollView showsVerticalScrollIndicator={false} contentContainerStyle={styles.scrollContent}>
          <View style={styles.header}>
            <ThemedText type="title">Merchant Onboarding</ThemedText>
            <ThemedText type="small" style={{ color: colors.textSecondary }}>
              Register your business on PawsNearMe
            </ThemedText>
          </View>

          {/* Provider Type Selector */}
          <ThemedText style={styles.sectionLabel}>
            Business Type
          </ThemedText>
          <View style={styles.typeSelectorRow}>
            {(['PET_STORE', 'VET_HOSPITAL', 'GROOMING_CENTER'] as ProviderType[]).map((t) => (
              <TouchableOpacity
                key={t}
                style={[
                  styles.typeCard,
                  { backgroundColor: colors.backgroundElement },
                  providerType === t && { borderColor: colors.text, borderWidth: 1 }
                ]}
                onPress={() => setProviderType(t)}
                activeOpacity={0.7}
              >
                <ThemedText type="small" style={styles.typeCardText}>
                  {t.replace('_', ' ')}
                </ThemedText>
              </TouchableOpacity>
            ))}
          </View>

          {/* Form Fields */}
          <ThemedText style={styles.sectionLabel}>
            General Information
          </ThemedText>
          <TextInput
            placeholder="Business Name *"
            placeholderTextColor="#888"
            style={[styles.input, { backgroundColor: colors.backgroundElement, color: colors.text }]}
            value={name}
            onChangeText={setName}
          />
          <TextInput
            placeholder="Description"
            placeholderTextColor="#888"
            style={[styles.input, { backgroundColor: colors.backgroundElement, color: colors.text }]}
            value={description}
            onChangeText={setDescription}
            multiline
          />

          <ThemedText style={styles.sectionLabel}>
            Address Details
          </ThemedText>
          <TextInput
            placeholder="Street Address *"
            placeholderTextColor="#888"
            style={[styles.input, { backgroundColor: colors.backgroundElement, color: colors.text }]}
            value={addressLine}
            onChangeText={setAddressLine}
          />
          <View style={styles.row}>
            <TextInput
              placeholder="City *"
              placeholderTextColor="#888"
              style={[styles.input, { flex: 1, backgroundColor: colors.backgroundElement, color: colors.text }]}
              value={city}
              onChangeText={setCity}
            />
            <TextInput
              placeholder="Pincode *"
              placeholderTextColor="#888"
              keyboardType="number-pad"
              style={[styles.input, { width: 120, backgroundColor: colors.backgroundElement, color: colors.text }]}
              value={pincode}
              onChangeText={setPincode}
            />
          </View>

          {/* Adapted Onboarding Fields */}
          {providerType === 'VET_HOSPITAL' && (
            <>
              <ThemedText style={styles.sectionLabel}>
                Medical License Details
              </ThemedText>
              <TextInput
                placeholder="Veterinary Council Reg No. *"
                placeholderTextColor="#888"
                style={[styles.input, { backgroundColor: colors.backgroundElement, color: colors.text }]}
                value={licenseNumber}
                onChangeText={setLicenseNumber}
              />
            </>
          )}

          {providerType === 'PET_STORE' && (
            <>
              <ThemedText style={styles.sectionLabel}>
                Tax details
              </ThemedText>
              <TextInput
                placeholder="GSTIN Number (Optional)"
                placeholderTextColor="#888"
                style={[styles.input, { backgroundColor: colors.backgroundElement, color: colors.text }]}
              />
            </>
          )}

          {/* Document Upload */}
          <ThemedText style={styles.sectionLabel}>
            Verification Document
          </ThemedText>
          <TouchableOpacity
            style={[styles.uploadButton, { backgroundColor: colors.backgroundElement }]}
            onPress={handleDocUpload}
            activeOpacity={0.7}
          >
            <ThemedText type="small">
              {docUploaded ? '✅ Document Uploaded' : '📤 Upload Document Proof *'}
            </ThemedText>
          </TouchableOpacity>

          {/* Submit Button */}
          <TouchableOpacity
            style={[styles.submitButton, { backgroundColor: colors.text }]}
            onPress={handleSubmit}
            disabled={submitting}
            activeOpacity={0.8}
          >
            <ThemedText style={{ color: colors.background, fontWeight: '700' }}>
              {submitting ? 'Submitting...' : 'Register Business'}
            </ThemedText>
          </TouchableOpacity>
        </ScrollView>
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
  scrollContent: {
    padding: Spacing.four,
    paddingBottom: Spacing.six,
  },
  header: {
    marginBottom: Spacing.four,
  },
  sectionLabel: {
    marginTop: Spacing.four,
    marginBottom: Spacing.two,
    fontWeight: '700',
  },
  typeSelectorRow: {
    flexDirection: 'row',
    gap: Spacing.two,
  },
  typeCard: {
    flex: 1,
    height: 50,
    borderRadius: Spacing.two,
    alignItems: 'center',
    justifyContent: 'center',
  },
  typeCardText: {
    fontSize: 12,
    textAlign: 'center',
  },
  input: {
    padding: Spacing.three,
    borderRadius: Spacing.two,
    marginBottom: Spacing.two,
    fontSize: 14,
  },
  row: {
    flexDirection: 'row',
    gap: Spacing.two,
  },
  uploadButton: {
    height: 55,
    borderRadius: Spacing.two,
    alignItems: 'center',
    justifyContent: 'center',
    borderStyle: 'dashed',
    borderWidth: 1,
    borderColor: '#666',
    marginBottom: Spacing.four,
  },
  submitButton: {
    height: 55,
    borderRadius: Spacing.two,
    alignItems: 'center',
    justifyContent: 'center',
    marginTop: Spacing.two,
  },
});
