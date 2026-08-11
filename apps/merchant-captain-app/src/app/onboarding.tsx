import React, { useCallback, useState } from 'react';
import { StyleSheet, View, TextInput, TouchableOpacity, ScrollView, Alert } from 'react-native';
import * as DocumentPicker from 'expo-document-picker';
import * as Location from 'expo-location';

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
  const [locating, setLocating] = useState(false);
  const [longitude, setLongitude] = useState('');
  const [latitude, setLatitude] = useState('');

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
      Alert.alert('Success', 'Business verification document uploaded successfully.');
    } catch (error) {
      Alert.alert('Upload Error', error instanceof Error ? error.message : 'Failed to upload document.');
    } finally {
      setUploadingDoc(false);
    }
  }, [session]);

  const handleUseCurrentLocation = useCallback(async () => {
    setLocating(true);
    try {
      const permission = await Location.requestForegroundPermissionsAsync();
      if (permission.status !== 'granted') {
        Alert.alert('Location permission required', 'Allow location access or enter your business coordinates manually.');
        return;
      }
      const current = await Location.getCurrentPositionAsync({ accuracy: Location.Accuracy.High });
      setLatitude(current.coords.latitude.toFixed(6));
      setLongitude(current.coords.longitude.toFixed(6));
    } catch (error) {
      Alert.alert('Location unavailable', error instanceof Error ? error.message : 'Could not read the current location.');
    } finally {
      setLocating(false);
    }
  }, []);

  const handleSubmit = useCallback(async () => {
    if (!user || !session?.access_token) {
      Alert.alert('Error', 'Please log in to submit your merchant application.');
      return;
    }

    if (!name.trim() || !addressLine.trim() || !city.trim() || !pincode.trim()) {
      Alert.alert('Error', 'Please fill all mandatory fields.');
      return;
    }

    if (!/^[1-9]\d{5}$/.test(pincode.trim())) {
      Alert.alert('Error', 'Enter a valid 6-digit Indian pincode.');
      return;
    }

    if (providerType === 'VET_HOSPITAL' && !licenseNumber.trim()) {
      Alert.alert('Error', 'Veterinary Council License number is required.');
      return;
    }

    if (!docUploaded || !licenseDocUrl) {
      Alert.alert('Error', 'Please upload the required business verification document.');
      return;
    }

    const parsedLng = Number(longitude);
    const parsedLat = Number(latitude);
    if (
      !Number.isFinite(parsedLng) ||
      !Number.isFinite(parsedLat) ||
      parsedLng < -180 ||
      parsedLng > 180 ||
      parsedLat < -90 ||
      parsedLat > 90
    ) {
      Alert.alert('Error', 'Capture or enter valid business coordinates before submitting.');
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
          licenseNumber: providerType === 'VET_HOSPITAL' ? licenseNumber.trim() : null,
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
      Alert.alert('Success', 'Provider application submitted successfully and is pending approval.');
      setName('');
      setDescription('');
      setAddressLine('');
      setCity('');
      setPincode('');
      setLicenseNumber('');
      setLicenseDocUrl('');
      setLongitude('');
      setLatitude('');
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
              Register your business on MyPet. Only fields shown here are submitted in this step.
            </ThemedText>
          </View>

          <ThemedText style={styles.sectionLabel}>Business Type</ThemedText>
          <View style={styles.typeSelectorRow}>
            {(['PET_STORE', 'VET_HOSPITAL', 'GROOMING_CENTER'] as ProviderType[]).map((type) => (
              <TouchableOpacity
                key={type}
                style={[
                  styles.typeCard,
                  { backgroundColor: colors.backgroundElement },
                  providerType === type && { borderColor: colors.text, borderWidth: 1 },
                ]}
                onPress={() => setProviderType(type)}
                activeOpacity={0.7}
              >
                <ThemedText type="small" style={styles.typeCardText}>
                  {type.replaceAll('_', ' ')}
                </ThemedText>
              </TouchableOpacity>
            ))}
          </View>

          <ThemedText style={styles.sectionLabel}>General Information</ThemedText>
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

          <ThemedText style={styles.sectionLabel}>Address Details</ThemedText>
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
              maxLength={6}
              style={[styles.input, { width: 120, backgroundColor: colors.backgroundElement, color: colors.text }]}
              value={pincode}
              onChangeText={setPincode}
            />
          </View>

          <View style={styles.locationHeading}>
            <ThemedText style={styles.sectionLabel}>Business Location</ThemedText>
            <TouchableOpacity
              accessibilityRole="button"
              accessibilityLabel="Use current business location"
              onPress={() => void handleUseCurrentLocation()}
              disabled={locating}
              style={[styles.locationButton, { backgroundColor: colors.backgroundElement }]}
            >
              <ThemedText type="small">{locating ? 'Locating…' : 'Use current location'}</ThemedText>
            </TouchableOpacity>
          </View>
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
          <ThemedText type="small" style={{ color: colors.textSecondary }}>
            MyPet does not prefill another city. Capture the actual storefront/clinic location or enter verified coordinates.
          </ThemedText>

          {providerType === 'VET_HOSPITAL' ? (
            <>
              <ThemedText style={styles.sectionLabel}>Medical License Details</ThemedText>
              <TextInput
                placeholder="Veterinary Council Reg No. *"
                placeholderTextColor="#888"
                style={[styles.input, { backgroundColor: colors.backgroundElement, color: colors.text }]}
                value={licenseNumber}
                onChangeText={setLicenseNumber}
              />
            </>
          ) : null}

          <ThemedText style={styles.sectionLabel}>Verification Document</ThemedText>
          <ThemedText type="small" style={{ color: colors.textSecondary, marginBottom: Spacing.two }}>
            Upload the business proof used for this application. Additional KYC, GST and settlement details are collected only in flows that persist them server-side.
          </ThemedText>
          <TouchableOpacity
            style={[styles.uploadButton, { backgroundColor: colors.backgroundElement }]}
            onPress={handleDocUpload}
            disabled={uploadingDoc}
            activeOpacity={0.7}
          >
            <ThemedText type="small">
              {uploadingDoc ? '⏳ Uploading...' : docUploaded ? '✅ Verification document uploaded' : '📤 Upload business proof *'}
            </ThemedText>
          </TouchableOpacity>

          <TouchableOpacity
            style={[styles.submitButton, { backgroundColor: colors.text }]}
            onPress={() => void handleSubmit()}
            disabled={submitting || locating || uploadingDoc}
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
  container: { flex: 1 },
  safeArea: { flex: 1 },
  scrollContent: {
    padding: Spacing.four,
    paddingBottom: Spacing.six,
  },
  header: { marginBottom: Spacing.four },
  sectionLabel: {
    marginTop: Spacing.four,
    marginBottom: Spacing.two,
    fontWeight: '700',
  },
  typeSelectorRow: { flexDirection: 'row', gap: Spacing.two },
  typeCard: {
    flex: 1,
    height: 50,
    borderRadius: Spacing.two,
    alignItems: 'center',
    justifyContent: 'center',
  },
  typeCardText: { fontSize: 12, textAlign: 'center' },
  input: {
    padding: Spacing.three,
    borderRadius: Spacing.two,
    marginBottom: Spacing.two,
    fontSize: 14,
  },
  row: { flexDirection: 'row', gap: Spacing.two },
  locationHeading: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    alignItems: 'flex-end',
    justifyContent: 'space-between',
    gap: Spacing.two,
  },
  locationButton: {
    minHeight: 44,
    borderRadius: Spacing.two,
    paddingHorizontal: Spacing.three,
    alignItems: 'center',
    justifyContent: 'center',
  },
  uploadButton: {
    minHeight: 55,
    borderRadius: Spacing.two,
    alignItems: 'center',
    justifyContent: 'center',
    borderStyle: 'dashed',
    borderWidth: 1,
    borderColor: '#666',
    marginBottom: Spacing.four,
    paddingHorizontal: Spacing.three,
  },
  submitButton: {
    height: 55,
    borderRadius: Spacing.two,
    alignItems: 'center',
    justifyContent: 'center',
    marginTop: Spacing.two,
  },
});
