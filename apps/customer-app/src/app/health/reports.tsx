import React, { useMemo, useState } from 'react';
import { Alert, Linking, Modal, Pressable, ScrollView, Share, StyleSheet, View } from 'react-native';

import { AppIcon } from '@/components/app-icon';
import { FilterChip, StateView } from '@/components/foundation/primitives';
import { ThemedText } from '@/components/themed-text';
import { PrimaryButton } from '@/components/ui/primary-button';
import { ScreenHeader } from '@/components/ui/screen-header';
import { StatusBadge } from '@/components/ui/status-badge';
import { useAuthIntent } from '@/context/AuthIntentContext';
import { radii, shadows, spacing, touchTarget, typography } from '@/design/tokens';
import { useAppointments } from '@/hooks/use-appointments';
import { useTheme } from '@/hooks/use-theme';

interface MedicalReportItem {
  id: string;
  petName: string;
  title: string;
  clinicName: string;
  createdAt: string;
  documentUrl: string;
}

type ReportFilter = 'ALL' | 'PRESCRIPTIONS';

function formatReportDate(value: string): string {
  return new Intl.DateTimeFormat(undefined, {
    day: 'numeric',
    month: 'short',
    year: 'numeric',
  }).format(new Date(value));
}

export default function MedicalReportsScreen() {
  const theme = useTheme();
  const { requireAuth } = useAuthIntent();
  const { user, session, appointments, state, reload } = useAppointments();
  const [activeFilter, setActiveFilter] = useState<ReportFilter>('ALL');
  const [uploadInfoVisible, setUploadInfoVisible] = useState(false);

  const reports = useMemo<MedicalReportItem[]>(() => {
    return appointments
      .filter((appointment) => Boolean(appointment.prescriptionDocUrl))
      .map((appointment) => ({
        id: appointment.id,
        petName: appointment.petName,
        title: `${appointment.serviceName} prescription`,
        clinicName: appointment.providerName,
        createdAt: appointment.slotStartsAt,
        documentUrl: appointment.prescriptionDocUrl as string,
      }))
      .sort((left, right) => right.createdAt.localeCompare(left.createdAt));
  }, [appointments]);

  const openReport = async (report: MedicalReportItem) => {
    try {
      const supported = await Linking.canOpenURL(report.documentUrl);
      if (!supported) throw new Error('This report link cannot be opened on this device.');
      await Linking.openURL(report.documentUrl);
    } catch (error: unknown) {
      Alert.alert('Could not open report', error instanceof Error ? error.message : 'Please request a fresh report link.');
    }
  };

  const shareReport = async (report: MedicalReportItem) => {
    try {
      await Share.share({
        title: report.title,
        message: `${report.title} for ${report.petName}\n${report.documentUrl}`,
        url: report.documentUrl,
      });
    } catch {
      Alert.alert('Could not share report', 'Please try again.');
    }
  };

  if (!user || !session) {
    return (
      <View style={[styles.container, { backgroundColor: theme.background }]}>
        <ScreenHeader title="Medical reports" subtitle="View health documents shared by verified providers" />
        <StateView
          kind="unauthenticated"
          title="Sign in to view reports"
          message="Medical documents are available only to the pet parent who booked the appointment."
          actionLabel="Sign in"
          onAction={() => void requireAuth({ action: 'ORDER_HISTORY', returnTo: '/health/reports' })}
        />
      </View>
    );
  }

  return (
    <View style={[styles.container, { backgroundColor: theme.background }]}>
      <ScreenHeader
        title="Medical reports"
        subtitle="View and manage health documents"
        trailing={
          <Pressable
            onPress={() => setUploadInfoVisible(true)}
            accessibilityRole="button"
            accessibilityLabel="About uploading medical reports"
            style={({ pressed }) => [
              styles.uploadButton,
              { backgroundColor: theme.primary },
              pressed && styles.pressed,
            ]}
          >
            <AppIcon name="upload" color="#FFFFFF" size={21} />
          </Pressable>
        }
      />

      <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.tabRow}>
        <FilterChip label={`All reports (${reports.length})`} selected={activeFilter === 'ALL'} onPress={() => setActiveFilter('ALL')} />
        <FilterChip
          label={`Prescriptions (${reports.length})`}
          selected={activeFilter === 'PRESCRIPTIONS'}
          onPress={() => setActiveFilter('PRESCRIPTIONS')}
        />
      </ScrollView>

      {state === 'idle' || state === 'loading' ? (
        <StateView kind="loading" title="Loading reports" message="Checking documents shared after your pet visits." />
      ) : state === 'offline' ? (
        <StateView
          kind="offline"
          title="You are offline"
          message="Reconnect to request current private report links."
          actionLabel="Retry"
          onAction={() => void reload()}
        />
      ) : state === 'error' ? (
        <StateView
          kind="error"
          title="Reports unavailable"
          message="We could not load your appointment documents."
          actionLabel="Retry"
          onAction={() => void reload()}
        />
      ) : reports.length === 0 ? (
        <StateView
          kind="empty"
          title="No medical reports yet"
          message="Prescriptions shared by your veterinarian after completed visits will appear here."
        />
      ) : (
        <ScrollView contentContainerStyle={styles.listContent} showsVerticalScrollIndicator={false}>
          {reports.map((report) => (
            <View
              key={report.id}
              style={[
                styles.reportCard,
                shadows.card,
                { backgroundColor: theme.backgroundElement, borderColor: theme.border },
              ]}
              accessible
              accessibilityLabel={`${report.title} for ${report.petName}, from ${report.clinicName}, ${formatReportDate(report.createdAt)}`}
            >
              <View style={[styles.documentIcon, { backgroundColor: theme.primarySoft }]}>
                <AppIcon name="document" color={theme.primary} size={28} />
              </View>

              <View style={styles.reportBody}>
                <View style={styles.reportHeader}>
                  <View style={styles.flex}>
                    <ThemedText style={styles.reportTitle} numberOfLines={2}>{report.title}</ThemedText>
                    <ThemedText type="small" themeColor="textSecondary" numberOfLines={1}>{report.clinicName}</ThemedText>
                  </View>
                  <ThemedText type="small" themeColor="textSecondary">{formatReportDate(report.createdAt)}</ThemedText>
                </View>

                <View style={styles.metaRow}>
                  <StatusBadge label={report.petName} color={theme.success} />
                  <StatusBadge label="Private document" color={theme.primary} />
                </View>

                <View style={styles.actionRow}>
                  <Pressable
                    onPress={() => void openReport(report)}
                    accessibilityRole="button"
                    accessibilityLabel={`View ${report.title}`}
                    style={({ pressed }) => [styles.textAction, pressed && styles.pressed]}
                  >
                    <AppIcon name="eye" color={theme.primary} size={20} />
                    <ThemedText type="smallBold" style={{ color: theme.primary }}>View</ThemedText>
                  </Pressable>
                  <Pressable
                    onPress={() => void shareReport(report)}
                    accessibilityRole="button"
                    accessibilityLabel={`Share ${report.title}`}
                    style={({ pressed }) => [styles.textAction, pressed && styles.pressed]}
                  >
                    <AppIcon name="share" color={theme.textSecondary} size={20} />
                    <ThemedText type="smallBold" themeColor="textSecondary">Share</ThemedText>
                  </Pressable>
                </View>
              </View>
            </View>
          ))}
        </ScrollView>
      )}

      <Modal
        visible={uploadInfoVisible}
        transparent
        animationType="slide"
        onRequestClose={() => setUploadInfoVisible(false)}
      >
        <View style={styles.modalOverlay}>
          <View
            style={[styles.modalCard, shadows.raised, { backgroundColor: theme.backgroundElement, borderColor: theme.border }]}
            accessibilityViewIsModal
          >
            <View style={[styles.modalIcon, { backgroundColor: theme.primarySoft }]}>
              <AppIcon name="shield" color={theme.primary} size={28} />
            </View>
            <ThemedText type="title">Secure upload is not connected yet</ThemedText>
            <ThemedText type="small" themeColor="textSecondary" style={styles.centerText}>
              MyPet will enable customer uploads only after encrypted storage, malware scanning, document ownership, and expiring-link APIs are available. Provider-issued prescriptions continue to appear automatically.
            </ThemedText>
            <PrimaryButton label="Understood" onPress={() => setUploadInfoVisible(false)} />
          </View>
        </View>
      </Modal>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, paddingHorizontal: spacing.x4, paddingTop: spacing.x2 },
  flex: { flex: 1 },
  uploadButton: {
    width: touchTarget,
    height: touchTarget,
    borderRadius: radii.compact,
    alignItems: 'center',
    justifyContent: 'center',
  },
  tabRow: { gap: spacing.x2, paddingRight: spacing.x4, paddingBottom: spacing.x3 },
  listContent: { gap: spacing.x3, paddingBottom: spacing.x8 },
  reportCard: {
    minHeight: 152,
    borderWidth: StyleSheet.hairlineWidth,
    borderRadius: radii.card,
    padding: spacing.x4,
    flexDirection: 'row',
    alignItems: 'flex-start',
    gap: spacing.x3,
  },
  documentIcon: {
    width: 56,
    height: 56,
    borderRadius: radii.compact,
    alignItems: 'center',
    justifyContent: 'center',
  },
  reportBody: { flex: 1, gap: spacing.x3 },
  reportHeader: { flexDirection: 'row', alignItems: 'flex-start', gap: spacing.x3 },
  reportTitle: { ...typography.title, fontSize: 17, lineHeight: 23 },
  metaRow: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing.x2 },
  actionRow: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing.x4 },
  textAction: {
    minHeight: touchTarget,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: spacing.x2,
    paddingHorizontal: spacing.x2,
  },
  modalOverlay: { flex: 1, backgroundColor: 'rgba(11,28,48,0.52)', justifyContent: 'center', padding: spacing.x4 },
  modalCard: {
    width: '100%',
    maxWidth: 520,
    alignSelf: 'center',
    borderWidth: StyleSheet.hairlineWidth,
    borderRadius: radii.feature,
    padding: spacing.x6,
    alignItems: 'center',
    gap: spacing.x3,
  },
  modalIcon: { width: 64, height: 64, borderRadius: 32, alignItems: 'center', justifyContent: 'center' },
  centerText: { textAlign: 'center' },
  pressed: { opacity: 0.82 },
});
