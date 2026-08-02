import React, { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Alert,
  Modal,
  Pressable,
  ScrollView,
  StyleSheet,
  TextInput,
  View,
} from 'react-native';
import { useRouter } from 'expo-router';

import { AppIcon } from '@/components/app-icon';
import {
  ActionButton,
  AppBar,
  FeedbackBanner,
  FilterChip,
  RoleBadge,
  StateView,
  StatusBadge,
} from '@/components/foundation/primitives';
import { ScreenShell } from '@/components/foundation/screen-shell';
import { ThemedText } from '@/components/themed-text';
import {
  appointmentActions,
  appointmentMatchesSearch,
  appointmentQueue,
  type MerchantAppointmentQueue,
} from '@/contracts/merchant-appointment';
import { useAuth } from '@/context/AuthContext';
import { radii, shadows, spacing, typography } from '@/design/tokens';
import { useTheme } from '@/hooks/use-theme';
import {
  fetchMerchantAppointmentHistory,
  fetchMerchantAppointmentInvoice,
  fetchMerchantBookings,
  fetchMerchantProviders,
  updateMerchantBookingStatus,
  type MerchantAppointmentAction,
  type MerchantBooking,
  type MerchantProvider,
} from '@/services/merchant-appointments';
import { appConfig } from '@/utils/app-config';
import {
  formatAppointmentStatus,
  formatCurrency,
  formatDateTime,
} from '@/utils/formatters';

const QUEUES: Array<{ id: MerchantAppointmentQueue; label: string }> = [
  { id: 'TODAY', label: 'Today' },
  { id: 'UPCOMING', label: 'Upcoming' },
  { id: 'COMPLETED', label: 'Completed' },
  { id: 'NO_SHOW', label: 'No-show' },
  { id: 'CANCELLED', label: 'Cancelled' },
];

const DEMO_PROVIDERS: MerchantProvider[] = [
  {
    providerId: 'demo-vet-provider',
    providerType: 'VET_HOSPITAL',
    fulfillmentType: 'APPOINTMENT',
    name: 'MyPet Demo Clinic',
  },
];

const DEMO_BOOKINGS: MerchantBooking[] = [
  {
    id: 'demo-appointment-1',
    customerId: 'demo-customer',
    customerName: 'Customer Demo',
    petName: 'Pet Bruno',
    serviceName: 'Veterinary consultation',
    slotStartsAt: new Date(Date.now() + 60 * 60 * 1000).toISOString(),
    status: 'CONFIRMED',
    providerId: 'demo-vet-provider',
    providerType: 'VET_HOSPITAL',
    offeringId: 'demo-offering',
    slotId: 'demo-slot',
    priceAmount: 500,
    payAtClinic: true,
    bookedAt: new Date().toISOString(),
  },
];

function statusTone(status: MerchantBooking['status']): 'success' | 'warning' | 'danger' | 'info' {
  if (status === 'COMPLETED') return 'success';
  if (status === 'CANCELLED' || status === 'NO_SHOW' || status === 'EXPIRED') return 'danger';
  if (status === 'CONFIRMED') return 'info';
  return 'warning';
}

export default function MerchantAppointmentsScreen() {
  const theme = useTheme();
  const router = useRouter();
  const { activeRole } = useAuth();
  const [providers, setProviders] = useState<MerchantProvider[]>([]);
  const [selectedProviderId, setSelectedProviderId] = useState<string | null>(null);
  const [bookings, setBookings] = useState<MerchantBooking[]>([]);
  const [queue, setQueue] = useState<MerchantAppointmentQueue>('TODAY');
  const [search, setSearch] = useState('');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [selectedBooking, setSelectedBooking] = useState<MerchantBooking | null>(null);
  const [selectedAction, setSelectedAction] = useState<MerchantAppointmentAction | null>(null);
  const [actionNote, setActionNote] = useState('');
  const [actionLoading, setActionLoading] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      if (appConfig.allowDemoMode) {
        setProviders(DEMO_PROVIDERS);
        setSelectedProviderId((current) => current ?? DEMO_PROVIDERS[0].providerId);
        setBookings(DEMO_BOOKINGS);
      } else {
        const [liveProviders, liveBookings] = await Promise.all([
          fetchMerchantProviders(),
          fetchMerchantBookings(),
        ]);
        setProviders(liveProviders);
        setSelectedProviderId((current) =>
          liveProviders.some((provider) => provider.providerId === current)
            ? current
            : liveProviders[0]?.providerId ?? null,
        );
        setBookings(liveBookings);
      }
      setError(null);
    } catch (cause: unknown) {
      setProviders([]);
      setBookings([]);
      setSelectedProviderId(null);
      setError(cause instanceof Error ? cause.message : 'Could not load appointments.');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const visibleBookings = useMemo(() => {
    const now = new Date();
    return bookings.filter((booking) =>
      (!selectedProviderId || booking.providerId === selectedProviderId)
      && appointmentQueue(booking.status, booking.slotStartsAt, now) === queue
      && appointmentMatchesSearch(search, [
        booking.id,
        booking.customerName,
        booking.petName,
        booking.serviceName,
      ]),
    );
  }, [bookings, queue, search, selectedProviderId]);

  const queueCounts = useMemo(() => {
    const now = new Date();
    return QUEUES.reduce<Record<MerchantAppointmentQueue, number>>((counts, item) => {
      counts[item.id] = bookings.filter((booking) =>
        (!selectedProviderId || booking.providerId === selectedProviderId)
        && appointmentQueue(booking.status, booking.slotStartsAt, now) === item.id,
      ).length;
      return counts;
    }, { TODAY: 0, UPCOMING: 0, COMPLETED: 0, NO_SHOW: 0, CANCELLED: 0 });
  }, [bookings, selectedProviderId]);

  const openAction = (booking: MerchantBooking, action: MerchantAppointmentAction) => {
    setSelectedBooking(booking);
    setSelectedAction(action);
    setActionNote('');
  };

  const closeAction = () => {
    if (actionLoading) return;
    setSelectedBooking(null);
    setSelectedAction(null);
    setActionNote('');
  };

  const submitAction = async () => {
    if (!selectedBooking || !selectedAction) return;
    setActionLoading(true);
    try {
      if (appConfig.allowDemoMode) {
        setBookings((current) => current.map((booking) =>
          booking.id === selectedBooking.id
            ? { ...booking, status: selectedAction, visitNotes: actionNote.trim() || null }
            : booking,
        ));
      } else {
        await updateMerchantBookingStatus(selectedBooking.id, selectedAction, actionNote);
        await load();
      }
      Alert.alert('Appointment updated', `Status changed to ${formatAppointmentStatus(selectedAction)}.`);
      closeAction();
    } catch (cause: unknown) {
      Alert.alert('Could not update appointment', cause instanceof Error ? cause.message : 'Try again.');
    } finally {
      setActionLoading(false);
    }
  };

  const showInvoice = async (booking: MerchantBooking) => {
    try {
      const invoice = await fetchMerchantAppointmentInvoice(booking.id);
      Alert.alert(
        `Invoice ${invoice.invoiceNumber}`,
        `Subtotal ${formatCurrency(invoice.subtotalAmount)}\nTax ${formatCurrency(invoice.taxAmount)}\nTotal ${formatCurrency(invoice.totalAmount)}\nGenerated ${formatDateTime(invoice.generatedAt)}`,
      );
    } catch (cause: unknown) {
      Alert.alert('Invoice unavailable', cause instanceof Error ? cause.message : 'Could not load invoice.');
    }
  };

  const showHistory = async (booking: MerchantBooking) => {
    try {
      const history = await fetchMerchantAppointmentHistory(booking.id);
      const message = history.length === 0
        ? 'No status changes recorded.'
        : history.slice(-6).map((entry) =>
            `${formatAppointmentStatus(entry.toStatus)} · ${formatDateTime(entry.changedAt)}${entry.note ? `\n${entry.note}` : ''}`,
          ).join('\n\n');
      Alert.alert('Appointment history', message);
    } catch (cause: unknown) {
      Alert.alert('History unavailable', cause instanceof Error ? cause.message : 'Could not load history.');
    }
  };

  if (activeRole !== 'PROVIDER') {
    return (
      <ScreenShell scroll={false} header={<AppBar title="Appointments" />}>
        <StateView kind="unauthorized" title="Merchant access required" message="Switch to a provider role to manage appointments." />
      </ScreenShell>
    );
  }

  return (
    <ScreenShell
      header={
        <AppBar
          eyebrow="Merchant operations"
          title="Appointments"
          subtitle="Server-backed queues, lifecycle actions and invoices"
          action={<RoleBadge role="merchant" />}
        />
      }
    >
      {loading ? (
        <StateView kind="loading" title="Loading appointments" message="Reading provider schedules and bookings…" />
      ) : error ? (
        <StateView kind="error" title="Appointments unavailable" message={error} actionLabel="Retry" onAction={() => void load()} />
      ) : providers.length === 0 ? (
        <StateView kind="empty" title="No appointment provider" message="Create or activate a vet or grooming provider first." />
      ) : (
        <>
          {appConfig.allowDemoMode ? (
            <FeedbackBanner title="Demo data" message="Live mode never falls back to fabricated appointments." tone="warning" />
          ) : null}

          <View style={styles.section}>
            <ThemedText style={styles.sectionTitle}>Provider</ThemedText>
            <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.horizontalRow}>
              {providers.map((provider) => (
                <FilterChip
                  key={provider.providerId}
                  label={provider.name}
                  selected={selectedProviderId === provider.providerId}
                  onPress={() => setSelectedProviderId(provider.providerId)}
                  icon={provider.providerType === 'VET_HOSPITAL' ? 'medical' : 'groom'}
                />
              ))}
            </ScrollView>
          </View>

          <View style={styles.section}>
            <ThemedText style={styles.sectionTitle}>Queue</ThemedText>
            <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.horizontalRow}>
              {QUEUES.map((item) => (
                <FilterChip
                  key={item.id}
                  label={`${item.label} ${queueCounts[item.id]}`}
                  selected={queue === item.id}
                  onPress={() => setQueue(item.id)}
                />
              ))}
            </ScrollView>
            <View style={[styles.searchBox, { backgroundColor: theme.backgroundElement, borderColor: theme.border }]}>
              <AppIcon name="search" color={theme.textSecondary} size={18} />
              <TextInput
                value={search}
                onChangeText={setSearch}
                placeholder="Search customer, pet, service or appointment"
                placeholderTextColor={theme.textSecondary}
                style={[styles.searchInput, { color: theme.text }]}
              />
            </View>
          </View>

          <View style={styles.section}>
            <View style={styles.sectionHeader}>
              <ThemedText style={styles.sectionTitle}>{QUEUES.find((item) => item.id === queue)?.label}</ThemedText>
              <ActionButton label="Refresh" variant="ghost" icon="history" onPress={() => void load()} />
            </View>

            {visibleBookings.length === 0 ? (
              <StateView kind="empty" title="No appointments in this queue" message="Change provider, queue, or search terms." />
            ) : visibleBookings.map((booking) => (
              <View
                key={booking.id}
                style={[
                  styles.card,
                  shadows.card,
                  { backgroundColor: theme.backgroundElement, borderColor: theme.border },
                ]}
              >
                <View style={styles.cardHeader}>
                  <View style={styles.flex}>
                    <ThemedText style={styles.cardTitle}>{booking.serviceName}</ThemedText>
                    <ThemedText type="small" themeColor="textSecondary">
                      #{booking.id.slice(0, 8)} · {formatDateTime(booking.slotStartsAt)}
                    </ThemedText>
                  </View>
                  <StatusBadge label={formatAppointmentStatus(booking.status)} tone={statusTone(booking.status)} />
                </View>

                <View style={styles.metaGrid}>
                  <MetaRow icon="paw" label={`${booking.customerName} · ${booking.petName}`} />
                  <MetaRow icon="wallet" label={`${formatCurrency(booking.priceAmount)} · ${booking.payAtClinic ? 'Pay at clinic' : 'Prepaid'}`} />
                </View>

                {booking.visitNotes ? (
                  <FeedbackBanner title="Visit note" message={booking.visitNotes} tone="info" icon="medical" />
                ) : null}
                {booking.cancellationReason ? (
                  <FeedbackBanner title="Cancellation reason" message={booking.cancellationReason} tone="danger" />
                ) : null}

                <View style={styles.actionRow}>
                  <ActionButton label="History" variant="ghost" icon="history" onPress={() => void showHistory(booking)} />
                  <ActionButton
                    label="Message"
                    variant="ghost"
                    icon="message"
                    onPress={() => router.push({
                      pathname: '/chat',
                      params: {
                        contextType: 'APPOINTMENT',
                        contextId: booking.id,
                        providerId: booking.providerId,
                        customerId: booking.customerId,
                        providerType: booking.providerType,
                        title: booking.customerName,
                      },
                    } as never)}
                  />
                  {booking.status === 'COMPLETED' ? (
                    <ActionButton label="Invoice" variant="secondary" icon="wallet" onPress={() => void showInvoice(booking)} />
                  ) : null}
                </View>

                {appointmentActions(booking.status).length > 0 ? (
                  <View style={styles.actionRow}>
                    <ActionButton label="Complete" icon="check" onPress={() => openAction(booking, 'COMPLETED')} />
                    <ActionButton label="No-show" variant="secondary" icon="clock" onPress={() => openAction(booking, 'NO_SHOW')} />
                    <ActionButton label="Cancel" variant="destructive" icon="xmark" onPress={() => openAction(booking, 'CANCELLED')} />
                  </View>
                ) : null}
              </View>
            ))}
          </View>
        </>
      )}

      <Modal visible={Boolean(selectedBooking && selectedAction)} transparent animationType="slide" onRequestClose={closeAction}>
        <Pressable style={styles.modalBackdrop} onPress={closeAction}>
          <Pressable style={[styles.modalCard, { backgroundColor: theme.background }]} onPress={() => undefined}>
            <ThemedText style={styles.modalTitle}>{formatAppointmentStatus(selectedAction)}</ThemedText>
            <ThemedText type="small" themeColor="textSecondary">
              {selectedBooking?.serviceName} · {selectedBooking?.customerName}
            </ThemedText>
            <TextInput
              value={actionNote}
              onChangeText={setActionNote}
              multiline
              numberOfLines={4}
              placeholder={selectedAction === 'CANCELLED' ? 'Cancellation reason' : 'Operational note (optional)'}
              placeholderTextColor={theme.textSecondary}
              style={[styles.noteInput, { color: theme.text, borderColor: theme.border, backgroundColor: theme.backgroundElement }]}
            />
            <View style={styles.actionRow}>
              <ActionButton label="Back" variant="ghost" onPress={closeAction} disabled={actionLoading} />
              <ActionButton
                label="Confirm update"
                variant={selectedAction === 'CANCELLED' ? 'destructive' : 'primary'}
                onPress={() => void submitAction()}
                loading={actionLoading}
              />
            </View>
          </Pressable>
        </Pressable>
      </Modal>
    </ScreenShell>
  );
}

function MetaRow({ icon, label }: { icon: 'paw' | 'wallet'; label: string }) {
  const theme = useTheme();
  return (
    <View style={styles.metaRow}>
      <AppIcon name={icon} color={theme.textSecondary} size={16} />
      <ThemedText type="small" themeColor="textSecondary" style={styles.flex}>{label}</ThemedText>
    </View>
  );
}

const styles = StyleSheet.create({
  flex: { flex: 1 },
  section: { gap: spacing.x3 },
  sectionTitle: { ...typography.title },
  sectionHeader: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', gap: spacing.x3 },
  horizontalRow: { gap: spacing.x2, paddingRight: spacing.x4 },
  searchBox: { minHeight: 48, borderWidth: 1, borderRadius: radii.compact, paddingHorizontal: spacing.x3, flexDirection: 'row', alignItems: 'center', gap: spacing.x2 },
  searchInput: { flex: 1, minHeight: 46 },
  card: { borderWidth: StyleSheet.hairlineWidth, borderRadius: radii.card, padding: spacing.x4, gap: spacing.x3 },
  cardHeader: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'flex-start', gap: spacing.x3 },
  cardTitle: { ...typography.label, fontWeight: '800' },
  metaGrid: { gap: spacing.x2 },
  metaRow: { flexDirection: 'row', alignItems: 'center', gap: spacing.x2 },
  actionRow: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing.x2 },
  modalBackdrop: { flex: 1, justifyContent: 'flex-end', backgroundColor: 'rgba(0,0,0,0.45)' },
  modalCard: { borderTopLeftRadius: radii.card, borderTopRightRadius: radii.card, padding: spacing.x5, gap: spacing.x4 },
  modalTitle: { ...typography.title },
  noteInput: { minHeight: 112, borderWidth: 1, borderRadius: radii.compact, padding: spacing.x3, textAlignVertical: 'top' },
});
