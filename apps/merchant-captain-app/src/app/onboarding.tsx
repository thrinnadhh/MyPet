import React, { useState, useCallback } from 'react';
import { StyleSheet, View, TextInput, TouchableOpacity, ScrollView, Alert } from 'react-native';
import * as DocumentPicker from 'expo-document-picker';

import { SafeAreaView } from 'react-native-safe-area-context';
import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { Spacing, Colors } from '@/constants/theme';
import { useColorScheme } from 'react-native';
import { useAuth } from '@/context/AuthContext';
import { submitMerchantOnboarding } from '@/services/merchant-onboarding';
import { uploadFileFromUri } from '@/utils/upload-file';
import { appConfig } from '@/utils/app-config';

type ProviderType = 'PET_STORE' | 'VET_HOSPITAL' | 'GROOMING_CENTER';

export default function OnboardingScreen() {
  const scheme = useColorScheme() === 'dark' ? 'dark' : 'light';
  const colors = Colors[scheme];
  const { user, session } = useAuth();

  const [providerType, setProviderType] = useState<ProviderType>('PET_STORE');
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [addressLine, setAddressLine] = useState('');
  const [city, setCity] = useState('');
  const [pincode, setPincode] = useState('');
  const [licenseNumber, setLicenseNumber] = useState('');
  const [docUploaded, setDocUploaded] = useState(false);
  const [licenseDocUrl, setLicenseDocUrl] = useState('');
  const [uploadingDoc, setUploadingDoc] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [longitude, setLongitude] = useState('77.5946');
  const [latitude, setLatitude] = useState('12.9716');

  const handleDocUpload = useCallback(async () => {
    if (!session?.access_token) {
      Alert.alert('Authentication required', 'Please sign in before uploading verification documents.');
      return;
    }
    setUploadingDoc(true);
    try {
      const result = await DocumentPicker.getDocumentAsync({
        type: ['application/pdf', 'image/jpeg', 'image/png', 'image/webp'],
        copyToCacheDirectory: true,
      });
      if (result.canceled || !result.assets[0]) return;
      const asset = result.assets[0];
      const fileUrl = await uploadFileFromUri(asset.uri, asset.name, session.access_token);
      setLicenseDocUrl(fileUrl);
      setDocUploaded(true);
      Alert.alert('Success', 'Document proof uploaded successfully!');
    } catch (error) {
      Alert.alert('Upload Error', error instanceof Error ? error.message : 'Failed to upload document.');
    } finally {
      setUploadingDoc(false);
    }
  }, [session]);

  const handleSubmit = useCallback(async () => {
    if (!user || !session?.access_token) {
      Alert.alert('Error', 'Please log in to submit your merchant application.');
      return;
    }

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

    const parsedLng = parseFloat(longitude);
    const parsedLat = parseFloat(latitude);
    if (isNaN(parsedLng) || isNaN(parsedLat)) {
      Alert.alert('Error', 'Please enter valid coordinate numbers.');
      return;
    }

    setSubmitting(true);

    try {
      await submitMerchantOnboarding(
        {
          ownerUserId: user.id,
          providerType,
          fulfillmentType: providerType === 'PET_STORE' ? 'DELIVERY' : 'APPOINTMENT',
          name: name.trim(),
          description: description.trim() || null,
          licenseNumber: providerType === 'VET_HOSPITAL' ? licenseNumber : null,
          licenseDocUrl,
          addressLine: addressLine.trim(),
          city: city.trim(),
          pincode: pincode.trim(),
          longitude: parsedLng,
          latitude: parsedLat,
        },
        session.access_token,
        appConfig.apiBaseUrl,
      );
      Alert.alert('Success', 'Provider application submitted successfully and is PENDING APPROVAL.');
      setName('');
      setDescription('');
      setAddressLine('');
      setCity('');
      setPincode('');
      setLicenseNumber('');
      setLicenseDocUrl('');
      setDocUploaded(false);
    } catch (error) {
      Alert.alert('Error', error instanceof Error ? error.message : 'Could not submit merchant application.');
    } finally {
      setSubmitting(false);
    }
  }, [providerType, name, description, addressLine, city, pincode, licenseNumber, docUploaded, licenseDocUrl, longitude, latitude, user, session]);

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

          <ThemedText style={styles.sectionLabel}>
            Location Coordinates (GPS)
          </ThemedText>
          <View style={styles.row}>
            <TextInput
              placeholder="Longitude *"
              placeholderTextColor="#888"
              keyboardType="numeric"
              style={[styles.input, { flex: 1, backgroundColor: colors.backgroundElement, color: colors.text }]}
              value={longitude}
              onChangeText={setLongitude}
            />
            <TextInput
              placeholder="Latitude *"
              placeholderTextColor="#888"
              keyboardType="numeric"
              style={[styles.input, { flex: 1, backgroundColor: colors.backgroundElement, color: colors.text }]}
              value={latitude}
              onChangeText={setLatitude}
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
                Tax & shop proof
              </ThemedText>
              <TextInput
                placeholder="GSTIN Number (Optional)"
                placeholderTextColor="#888"
                style={[styles.input, { backgroundColor: colors.backgroundElement, color: colors.text }]}
              />
              <TouchableOpacity
                style={[styles.uploadButton, { backgroundColor: colors.backgroundElement }]}
                onPress={handleDocUpload}
                activeOpacity={0.7}
              >
                <ThemedText type="small">Upload electricity bill or rental agreement</ThemedText>
              </TouchableOpacity>
              <TouchableOpacity
                style={[styles.uploadButton, { backgroundColor: colors.backgroundElement }]}
                onPress={handleDocUpload}
                activeOpacity={0.7}
              >
                <ThemedText type="small">Upload shopfront photo</ThemedText>
              </TouchableOpacity>
              <ThemedText style={styles.sectionLabel}>Banking details</ThemedText>
              <TextInput
                placeholder="Account number"
                placeholderTextColor="#888"
                keyboardType="number-pad"
                style={[styles.input, { backgroundColor: colors.backgroundElement, color: colors.text }]}
              />
              <TextInput
                placeholder="IFSC"
                placeholderTextColor="#888"
                autoCapitalize="characters"
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
            disabled={uploadingDoc}
            activeOpacity={0.7}
          >
            <ThemedText type="small">
              {uploadingDoc ? '⏳ Uploading...' : docUploaded ? '✅ Document Uploaded' : '📤 Upload Document Proof *'}
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
