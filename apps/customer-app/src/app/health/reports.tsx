import { useLocalSearchParams } from 'expo-router';
import React, { useMemo, useState } from 'react';
import { Modal, Pressable, ScrollView, Share, StyleSheet, View } from 'react-native';

import { AppIcon } from '@/components/app-icon';
import { FilterChip } from '@/components/foundation/primitives';
import { ThemedText } from '@/components/themed-text';
import { PrimaryButton } from '@/components/ui/primary-button';
import { ScreenHeader } from '@/components/ui/screen-header';
import { StatusBadge } from '@/components/ui/status-badge';
import { radii, shadows, spacing, typography } from '@/design/tokens';
import { useTheme } from '@/hooks/use-theme';

interface MedicalReportItem {
  id: string;
  petId: string;
  petName: string;
  title: string;
  category: 'BLOOD_TEST' | 'VACCINATION' | 'PRESCRIPTION' | 'GENERAL';
  labOrClinicName: string;
  doctorName: string;
  createdAt: string;
  signedUrl: string;
}

const MEDICAL_REPORTS_DATA: MedicalReportItem[] = [
  {
    id: 'rep-1',
    petId: 'pet-bruno',
    petName: 'Bruno',
    title: 'Comprehensive Annual Blood Profile & CBC Test',
    category: 'BLOOD_TEST',
    labOrClinicName: 'City Vet Pathology Labs, Tirupati',
    doctorName: 'Dr. K. Srinivas',
    createdAt: '2026-06-12',
    signedUrl: 'https://s3.amazonaws.com/pawsnearme-private/reports/cbc_bruno_2026.pdf?sig=sec_8f91a2&expires=1785412000',
  },
  {
    id: 'rep-2',
    petId: 'pet-bruno',
    petName: 'Bruno',
    title: 'Rabies Immunization Official Certificate',
    category: 'VACCINATION',
    labOrClinicName: 'City Pet Hospital Tirupati',
    doctorName: 'Dr. K. Srinivas',
    createdAt: '2026-01-10',
    signedUrl: 'https://s3.amazonaws.com/pawsnearme-private/reports/rabies_cert_bruno.pdf?sig=sec_3e71b9&expires=1785412000',
  },
  {
    id: 'rep-3',
    petId: 'pet-luna',
    petName: 'Luna',
    title: 'Feline Cardiac & Kidney Health Diagnostics',
    category: 'BLOOD_TEST',
    labOrClinicName: 'PetCare Diagnostics Center',
    doctorName: 'Dr. Priya Sharma',
    createdAt: '2026-05-04',
    signedUrl: 'https://s3.amazonaws.com/pawsnearme-private/reports/luna_kidney_test.pdf?sig=sec_1a98c4&expires=1785412000',
  },
];

export default function MedicalReportsScreen() {
  const theme = useTheme();
  const [activeTab, setActiveTab] = useState<'ALL' | 'BLOOD_TEST' | 'VACCINATION'>('ALL');
  const [selectedReport, setSelectedReport] = useState<MedicalReportItem | null>(null);
  const [uploadModalVisible, setUploadModalVisible] = useState(false);
  const [uploadSuccess, setUploadSuccess] = useState(false);

  const filteredReports = useMemo(() => {
    if (activeTab === 'ALL') return MEDICAL_REPORTS_DATA;
    return MEDICAL_REPORTS_DATA.filter((r) => r.category === activeTab);
  }, [activeTab]);

  const handleShare = async (report: MedicalReportItem) => {
    try {
      await Share.share({
        message: `Medical Record for ${report.petName}: ${report.title} (${report.signedUrl})`,
        title: report.title,
      });
    } catch (err) {
      // Ignored
    }
  };

  return (
    <View style={[styles.container, { backgroundColor: theme.background }]}>
      <ScreenHeader
        title="Medical Reports & Labs"
        subtitle="Private encrypted medical records"
        trailing={
          <Pressable onPress={() => setUploadModalVisible(true)} style={{ padding: 4 }}>
            <ThemedText style={{ color: theme.primary, fontWeight: '800', fontSize: 13 }}>+ Upload</ThemedText>
          </Pressable>
        }
      />

      {/* Tabs */}
      <View style={styles.tabRow}>
        <FilterChip label="All Reports" selected={activeTab === 'ALL'} onPress={() => setActiveTab('ALL')} />
        <FilterChip label="Blood & Labs" selected={activeTab === 'BLOOD_TEST'} onPress={() => setActiveTab('BLOOD_TEST')} />
        <FilterChip label="Vaccine Certs" selected={activeTab === 'VACCINATION'} onPress={() => setActiveTab('VACCINATION')} />
      </View>

      <ScrollView contentContainerStyle={styles.listContent} showsVerticalScrollIndicator={false}>
        {filteredReports.map((item) => (
          <View
            key={item.id}
            style={[styles.reportCard, shadows.raised, { backgroundColor: theme.backgroundElement, borderColor: theme.border }]}
          >
            <View style={styles.cardHeader}>
              <StatusBadge
                label={item.category === 'BLOOD_TEST' ? 'Blood Test' : 'Vaccine Cert'}
                color={theme.primary}
              />
              <ThemedText style={{ fontSize: 12, color: theme.textSecondary }}>{item.createdAt}</ThemedText>
            </View>

            <ThemedText style={[styles.reportTitle, { color: theme.text }]}>{item.title}</ThemedText>
            <ThemedText style={{ fontSize: 12, color: theme.textSecondary }}>
              🏥 {item.labOrClinicName} • 👨‍⚕️ {item.doctorName}
            </ThemedText>
            <StatusBadge label={`🔒 Private Record (${item.petName})`} color={theme.success} />

            <View style={styles.actionRow}>
              <Pressable
                onPress={() => setSelectedReport(item)}
                style={[styles.btn, { backgroundColor: theme.primary }]}
              >
                <ThemedText style={{ color: '#FFFFFF', fontWeight: '700', fontSize: 13 }}>View Report</ThemedText>
              </Pressable>

              <Pressable
                onPress={() => void handleShare(item)}
                style={[styles.btn, { backgroundColor: theme.primarySoft }]}
              >
                <ThemedText style={{ color: theme.primary, fontWeight: '700', fontSize: 13 }}>Share Link</ThemedText>
              </Pressable>
            </View>
          </View>
        ))}
      </ScrollView>

      {/* View PDF / Document Modal */}
      <Modal visible={!!selectedReport} animationType="slide" transparent onRequestClose={() => setSelectedReport(null)}>
        <View style={styles.modalOverlay}>
          <View style={[styles.documentViewerCard, { backgroundColor: theme.backgroundElement }]}>
            <View style={styles.modalHeader}>
              <ThemedText style={{ fontWeight: '800', fontSize: 16, color: theme.text, flex: 1 }} numberOfLines={1}>
                {selectedReport?.title}
              </ThemedText>
              <Pressable onPress={() => setSelectedReport(null)} style={{ padding: 4 }}>
                <AppIcon name="warning" color={theme.textSecondary} size={20} />
              </Pressable>
            </View>

            <View style={[styles.docPreviewBox, { backgroundColor: theme.muted }]}>
              <AppIcon name="sparkle" color={theme.primary} size={48} />
              <ThemedText style={{ fontWeight: '700', fontSize: 14, color: theme.text }}>Private Signed Object URL Active</ThemedText>
              <ThemedText style={{ fontSize: 12, color: theme.textSecondary, textAlign: 'center', paddingHorizontal: 16 }}>
                {selectedReport?.signedUrl}
              </ThemedText>
              <StatusBadge label="Expires in 60 mins • AES-256 Encrypted" color={theme.success} />
            </View>

            <PrimaryButton label="Close Preview" onPress={() => setSelectedReport(null)} />
          </View>
        </View>
      </Modal>

      {/* Upload Modal */}
      <Modal visible={uploadModalVisible} animationType="slide" transparent onRequestClose={() => setUploadModalVisible(false)}>
        <View style={styles.modalOverlay}>
          <View style={[styles.documentViewerCard, { backgroundColor: theme.backgroundElement }]}>
            <View style={styles.modalHeader}>
              <ThemedText style={{ fontWeight: '800', fontSize: 16, color: theme.text }}>Upload New Medical Record</ThemedText>
              <Pressable onPress={() => setUploadModalVisible(false)} style={{ padding: 4 }}>
                <AppIcon name="warning" color={theme.textSecondary} size={20} />
              </Pressable>
            </View>

            {uploadSuccess ? (
              <View style={{ alignItems: 'center', gap: 12, paddingVertical: 24 }}>
                <AppIcon name="sparkle" color={theme.success} size={48} />
                <ThemedText style={{ fontWeight: '800', fontSize: 16, color: theme.success }}>Upload Successful!</ThemedText>
                <ThemedText style={{ fontSize: 13, color: theme.textSecondary }}>Encrypted and linked to Bruno&apos;s profile.</ThemedText>
                <PrimaryButton label="Done" onPress={() => { setUploadSuccess(false); setUploadModalVisible(false); }} />
              </View>
            ) : (
              <View style={{ gap: 16 }}>
                <ThemedText style={{ fontSize: 13, color: theme.textSecondary }}>Select a PDF or image report file from your device:</ThemedText>
                <View style={[styles.dropZone, { borderColor: theme.primary, backgroundColor: theme.primarySoft }]}>
                  <AppIcon name="location" color={theme.primary} size={32} />
                  <ThemedText style={{ color: theme.primary, fontWeight: '700', fontSize: 14 }}>Tap to Select File (PDF, PNG, JPG)</ThemedText>
                </View>
                <PrimaryButton label="Upload Encrypted Report" onPress={() => setUploadSuccess(true)} />
              </View>
            )}
          </View>
        </View>
      </Modal>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, padding: spacing.x3 },
  tabRow: { flexDirection: 'row', gap: spacing.x2, marginBottom: spacing.x3 },
  listContent: { gap: spacing.x3, paddingBottom: spacing.x6 },
  reportCard: { padding: spacing.x4, borderRadius: radii.card, borderWidth: 1, gap: spacing.x2 },
  cardHeader: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  reportTitle: { ...typography.headline, fontSize: 15, fontWeight: '700' },
  actionRow: { flexDirection: 'row', gap: spacing.x2, marginTop: 4 },
  btn: { flex: 1, alignItems: 'center', justifyContent: 'center', paddingVertical: 10, borderRadius: radii.compact },
  modalOverlay: { flex: 1, backgroundColor: 'rgba(0,0,0,0.5)', justifyContent: 'center', padding: spacing.x4 },
  documentViewerCard: { borderRadius: radii.card, padding: spacing.x4, gap: spacing.x4 },
  modalHeader: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  docPreviewBox: { padding: spacing.x4, borderRadius: radii.compact, alignItems: 'center', gap: spacing.x3 },
  dropZone: { borderStyle: 'dashed', borderWidth: 2, borderRadius: radii.compact, padding: spacing.x6, alignItems: 'center', gap: spacing.x2 },
});
