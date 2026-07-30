import React, { useState } from 'react';
import { Modal, Pressable, ScrollView, StyleSheet, TextInput, View } from 'react-native';

import { AppIcon } from '@/components/app-icon';
import { ThemedText } from '@/components/themed-text';
import { useLocation, type ActiveCity } from '@/context/LocationContext';
import { radii, shadows, spacing, typography } from '@/design/tokens';
import { useTheme } from '@/hooks/use-theme';

export function LocationModal() {
  const theme = useTheme();
  const {
    activeCity,
    enabledCities,
    isLocationModalOpen,
    closeLocationModal,
    selectCity,
    requestUnavailableCityLaunch,
  } = useLocation();

  const [query, setQuery] = useState('');

  const filteredCities = enabledCities.filter(
    (c) =>
      c.displayName.toLowerCase().includes(query.toLowerCase()) ||
      c.state.toLowerCase().includes(query.toLowerCase()) ||
      c.pincodes.some((p) => p.includes(query))
  );

  const isExactMatch = filteredCities.length > 0;

  return (
    <Modal
      visible={isLocationModalOpen}
      animationType="slide"
      transparent
      onRequestClose={closeLocationModal}
    >
      <View style={styles.overlay}>
        <View style={[styles.modalCard, { backgroundColor: theme.background, borderColor: theme.border }]}>
          <View style={styles.header}>
            <ThemedText style={styles.title}>Select Service Location</ThemedText>
            <Pressable onPress={closeLocationModal} style={styles.closeBtn}>
              <AppIcon name="warning" color={theme.textSecondary} size={20} />
            </Pressable>
          </View>

          <View style={[styles.searchBar, { backgroundColor: theme.backgroundElement, borderColor: theme.border }]}>
            <AppIcon name="search" color={theme.textSecondary} size={18} />
            <TextInput
              value={query}
              onChangeText={setQuery}
              placeholder="Search city, state, or pincode..."
              placeholderTextColor={theme.textSecondary}
              style={[styles.searchInput, { color: theme.text }]}
              autoCapitalize="none"
            />
          </View>

          <ScrollView style={styles.cityList} contentContainerStyle={styles.cityListContent}>
            <ThemedText style={styles.sectionHeading}>Available Cities</ThemedText>
            {filteredCities.map((city) => {
              const isSelected = city.cityIdentity === activeCity.cityIdentity;
              return (
                <Pressable
                  key={city.id}
                  onPress={() => void selectCity(city)}
                  style={[
                    styles.cityRow,
                    {
                      backgroundColor: isSelected ? theme.primarySoft : theme.backgroundElement,
                      borderColor: isSelected ? theme.primary : theme.border,
                    },
                  ]}
                >
                  <AppIcon name="location" color={isSelected ? theme.primary : theme.textSecondary} size={20} />
                  <View style={styles.cityInfo}>
                    <ThemedText style={[styles.cityName, { color: isSelected ? theme.primary : theme.text }]}>
                      {city.displayName}
                    </ThemedText>
                    <ThemedText style={styles.citySub}>{city.state}, {city.country}</ThemedText>
                  </View>
                  {isSelected && <AppIcon name="check" color={theme.primary} size={18} />}
                </Pressable>
              );
            })}

            {!isExactMatch && query.trim().length > 0 && (
              <View style={[styles.unsupportedBox, { backgroundColor: theme.muted }]}>
                <ThemedText style={styles.unsupportedText}>
                  {`No active service region found for "${query}".`}
                </ThemedText>
                <Pressable
                  onPress={() => requestUnavailableCityLaunch(query.trim())}
                  style={[styles.notifyBtn, { backgroundColor: theme.primary }]}
                >
                  <ThemedText style={{ color: '#FFFFFF', fontWeight: '700' }}>
                    Notify Me When Available
                  </ThemedText>
                </Pressable>
              </View>
            )}
          </ScrollView>
        </View>
      </View>
    </Modal>
  );
}

export function NotifyCityModal() {
  const theme = useTheme();
  const {
    isNotifyModalOpen,
    closeNotifyModal,
    requestedUnavailableCity,
    submitCityNotificationRequest,
  } = useLocation();

  const [contactInfo, setContactInfo] = useState('');

  return (
    <Modal
      visible={isNotifyModalOpen}
      animationType="fade"
      transparent
      onRequestClose={closeNotifyModal}
    >
      <View style={styles.overlay}>
        <View style={[styles.modalCard, { backgroundColor: theme.background, borderColor: theme.border }]}>
          <ThemedText style={styles.title}>{"We're Coming Soon!"}</ThemedText>

          <ThemedText style={styles.subtitle}>
            PawsNearMe is expanding rapidly. Leave your phone or email to get notified when we launch in{' '}
            <ThemedText style={{ fontWeight: '700', color: theme.primary }}>
              {requestedUnavailableCity ?? 'your area'}
            </ThemedText>.
          </ThemedText>

          <TextInput
            value={contactInfo}
            onChangeText={setContactInfo}
            placeholder="Enter phone number or email address"
            placeholderTextColor={theme.textSecondary}
            keyboardType="email-address"
            autoCapitalize="none"
            style={[styles.input, { color: theme.text, backgroundColor: theme.backgroundElement, borderColor: theme.border }]}
          />

          <View style={styles.btnRow}>
            <Pressable
              onPress={closeNotifyModal}
              style={[styles.modalBtn, { backgroundColor: theme.muted }]}
            >
              <ThemedText style={{ color: theme.text }}>Cancel</ThemedText>
            </Pressable>
            <Pressable
              onPress={() => void submitCityNotificationRequest(contactInfo)}
              style={[styles.modalBtn, { backgroundColor: theme.primary }]}
            >
              <ThemedText style={{ color: '#FFFFFF', fontWeight: '700' }}>Submit</ThemedText>
            </Pressable>
          </View>
        </View>
      </View>
    </Modal>
  );
}

const styles = StyleSheet.create({
  overlay: { flex: 1, backgroundColor: 'rgba(0,0,0,0.5)', justifyContent: 'flex-end' },
  modalCard: { borderTopLeftRadius: radii.feature, borderTopRightRadius: radii.feature, padding: spacing.x6, gap: spacing.x4, maxHeight: '80%' },

  header: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  title: { ...typography.headline, fontSize: 18 },
  subtitle: { ...typography.body, fontSize: 13, color: '#666666' },
  closeBtn: { padding: 4 },
  searchBar: { flexDirection: 'row', alignItems: 'center', gap: spacing.x2, borderWidth: 1, borderRadius: radii.compact, paddingHorizontal: spacing.x3, height: 44 },
  searchInput: { flex: 1, height: 44, ...typography.body },
  cityList: { flexGrow: 0 },
  cityListContent: { gap: spacing.x3, paddingVertical: spacing.x2 },
  sectionHeading: { ...typography.label, fontSize: 12, color: '#888888', textTransform: 'uppercase' },
  cityRow: { flexDirection: 'row', alignItems: 'center', gap: spacing.x3, padding: spacing.x3, borderRadius: radii.compact, borderWidth: 1 },
  cityInfo: { flex: 1 },
  cityName: { ...typography.headline, fontSize: 15 },
  citySub: { ...typography.caption, color: '#777777' },
  unsupportedBox: { padding: spacing.x4, borderRadius: radii.compact, gap: spacing.x3, alignItems: 'center' },
  unsupportedText: { ...typography.body, fontSize: 13, textAlign: 'center' },
  notifyBtn: { paddingHorizontal: spacing.x4, paddingVertical: spacing.x2, borderRadius: radii.compact },
  input: { borderWidth: 1, borderRadius: radii.compact, paddingHorizontal: spacing.x3, height: 44, ...typography.body },
  btnRow: { flexDirection: 'row', gap: spacing.x3, justifyContent: 'flex-end' },
  modalBtn: { paddingHorizontal: spacing.x4, paddingVertical: spacing.x2, borderRadius: radii.compact, minWidth: 80, alignItems: 'center' },
});
