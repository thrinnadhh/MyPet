import React, { useState, useCallback, useEffect } from 'react';
import {
  FlatList,
  Modal,
  Platform,
  Pressable,
  ScrollView,
  StyleSheet,
  TextInput,
  TouchableOpacity,
  View,
  ActivityIndicator,
  Alert,
} from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { AppIcon } from '@/components/app-icon';
import { BottomTabInset, Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';
import { useAuth } from '@/context/AuthContext';

// ─── Types ──────────────────────────────────────────────────────────────────

type AppointmentStatus = 'CONFIRMED' | 'COMPLETED' | 'CANCELLED' | 'NO_SHOW';

interface Booking {
  id: string;
  customerName: string;
  petName: string;
  serviceName: string;
  slotStartsAt: string;
  status: AppointmentStatus;
}

// ─── Mock data (replace with API call) ───────────────────────────────────────

const MOCK_BOOKINGS: Booking[] = [
  {
    id: '1',
    customerName: 'Priya Sharma',
    petName: 'Bruno',
    serviceName: 'Full Grooming',
    slotStartsAt: new Date(Date.now() + 2 * 3600_000).toISOString(),
    status: 'CONFIRMED',
  },
  {
    id: '2',
    customerName: 'Raj Kumar',
    petName: 'Milo',
    serviceName: 'Vet Checkup',
    slotStartsAt: new Date(Date.now() + 5 * 3600_000).toISOString(),
    status: 'CONFIRMED',
  },
  {
    id: '3',
    customerName: 'Anita Reddy',
    petName: 'Biscuit',
    serviceName: 'Vaccination',
    slotStartsAt: new Date(Date.now() - 1 * 3600_000).toISOString(),
    status: 'COMPLETED',
  },
];

// ─── Status badge ─────────────────────────────────────────────────────────────

const STATUS_COLORS: Record<AppointmentStatus, string> = {
  CONFIRMED:  '#22c55e',
  COMPLETED:  '#3b82f6',
  CANCELLED:  '#ef4444',
  NO_SHOW:    '#f97316',
};

function StatusBadge({ status }: { status: AppointmentStatus }) {
  return (
    <View style={[styles.badge, { backgroundColor: STATUS_COLORS[status] + '22' }]}>
      <ThemedText style={[styles.badgeText, { color: STATUS_COLORS[status] }]}>
        {status}
      </ThemedText>
    </View>
  );
}

// ─── Complete Modal ───────────────────────────────────────────────────────────

interface CompleteModalProps {
  booking: Booking | null;
  visible: boolean;
  onClose: () => void;
  onSubmit: (bookingId: string, notes: string) => void;
}

function CompleteModal({ booking, visible, onClose, onSubmit }: CompleteModalProps) {
  const [notes, setNotes] = useState('');
  const [loading, setLoading] = useState(false);
  const theme = useTheme();

  const handleSubmit = useCallback(async () => {
    if (!booking) return;
    setLoading(true);
    // Simulate API call
    await new Promise(r => setTimeout(r, 600));
    onSubmit(booking.id, notes);
    setNotes('');
    setLoading(false);
    onClose();
  }, [booking, notes, onSubmit, onClose]);

  if (!booking) return null;

  return (
    <Modal
      animationType="slide"
      transparent
      visible={visible}
      onRequestClose={onClose}
      accessibilityViewIsModal
    >
      <Pressable style={styles.modalOverlay} onPress={onClose} accessibilityLabel="Close modal">
        <Pressable style={[styles.modalSheet, { backgroundColor: theme.background }]}>
          {/* Handle */}
          <View style={styles.modalHandle} />

          <ThemedText type="subtitle" style={styles.modalTitle}>
            Mark as Completed
          </ThemedText>
          <ThemedText themeColor="textSecondary" style={styles.modalSubtitle}>
            {booking.customerName} · {booking.petName} · {booking.serviceName}
          </ThemedText>

          <ThemedText style={styles.label}>Visit Notes</ThemedText>
          <TextInput
            style={[styles.textArea, { borderColor: '#ccc', color: theme.text, backgroundColor: theme.backgroundElement }]}
            multiline
            numberOfLines={4}
            placeholder="Enter visit notes, observations, or follow-up instructions..."
            placeholderTextColor={theme.textSecondary}
            value={notes}
            onChangeText={setNotes}
            accessibilityLabel="Visit notes input"
          />

          <ThemedText style={[styles.label, { marginTop: Spacing.three }]} themeColor="textSecondary">
            📎 Document upload available in full app build
          </ThemedText>

          <TouchableOpacity
            style={[styles.submitBtn, { backgroundColor: theme.primary }]}
            onPress={handleSubmit}
            disabled={loading}
            accessibilityLabel="Confirm completion"
            accessibilityRole="button"
          >
            {loading ? (
              <ActivityIndicator color="#fff" />
            ) : (
              <ThemedText style={styles.submitBtnText}>✓ Confirm Completed</ThemedText>
            )}
          </TouchableOpacity>

          <TouchableOpacity
            style={styles.cancelBtn}
            onPress={onClose}
            accessibilityLabel="Cancel"
            accessibilityRole="button"
          >
            <ThemedText themeColor="textSecondary">Cancel</ThemedText>
          </TouchableOpacity>
        </Pressable>
      </Pressable>
    </Modal>
  );
}

// ─── Booking card ─────────────────────────────────────────────────────────────

interface BookingCardProps {
  item: Booking;
  onComplete: (booking: Booking) => void;
  theme: ReturnType<typeof useTheme>;
}

const BookingCard = React.memo(function BookingCard({ item, onComplete, theme }: BookingCardProps) {
  const time = new Date(item.slotStartsAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
  const date = new Date(item.slotStartsAt).toLocaleDateString([], { weekday: 'short', month: 'short', day: 'numeric' });

  return (
    <ThemedView type="backgroundElement" style={styles.card}>
      <View style={styles.cardHeader}>
        <View style={styles.cardHeaderLeft}>
          <ThemedText style={{ fontWeight: '700' }}>{item.customerName}</ThemedText>
          <View style={styles.inlineMeta}>
            <AppIcon name="paw" color={theme.textSecondary} size={14} />
            <ThemedText themeColor="textSecondary" type="small">{item.petName}</ThemedText>
          </View>
        </View>
        <StatusBadge status={item.status} />
      </View>

      <View style={styles.cardDetails}>
        <ThemedText type="small">{item.serviceName}</ThemedText>
        <View style={styles.inlineMeta}>
          <AppIcon name="calendar" color={theme.textSecondary} size={14} />
          <ThemedText type="small" themeColor="textSecondary">{date} · {time}</ThemedText>
        </View>
      </View>

      {item.status === 'CONFIRMED' && (
        <TouchableOpacity
          style={[styles.completeBtn, { borderColor: theme.primary }]}
          onPress={() => onComplete(item)}
          accessibilityLabel={`Mark ${item.customerName}'s appointment as completed`}
          accessibilityRole="button"
          activeOpacity={0.7}
        >
          <ThemedText style={{ color: theme.primary, fontWeight: '600' }}>
            ✓ Mark Completed
          </ThemedText>
        </TouchableOpacity>
      )}
    </ThemedView>
  );
});

// ─── Main screen ─────────────────────────────────────────────────────────────

type FilterType = 'ALL' | 'CONFIRMED' | 'COMPLETED';

export default function BookingsScreen() {
  const theme = useTheme();
  const safeAreaInsets = useSafeAreaInsets();
  const { user } = useAuth();

  const [bookings, setBookings] = useState<Booking[]>(MOCK_BOOKINGS);
  const [filter, setFilter] = useState<FilterType>('ALL');
  const [selectedBooking, setSelectedBooking] = useState<Booking | null>(null);
  const [modalVisible, setModalVisible] = useState(false);
  const [loading] = useState(false);

  const insets = {
    ...safeAreaInsets,
    bottom: safeAreaInsets.bottom + BottomTabInset + Spacing.three,
  };

  const contentPlatformStyle = Platform.select({
    android: { paddingTop: insets.top + Spacing.two },
    web: { paddingTop: Spacing.six },
  });

  // Filter logic
  const filtered = bookings.filter(b => filter === 'ALL' || b.status === filter);

  const handleComplete = useCallback((booking: Booking) => {
    setSelectedBooking(booking);
    setModalVisible(true);
  }, []);

  const handleSubmitComplete = useCallback((bookingId: string, notes: string) => {
    setBookings(prev =>
      prev.map(b => b.id === bookingId ? { ...b, status: 'COMPLETED' as AppointmentStatus } : b)
    );
    Alert.alert('Done!', 'Appointment marked as completed.');
  }, []);

  const renderItem = useCallback(
    ({ item }: { item: Booking }) => (
      <BookingCard item={item} onComplete={handleComplete} theme={theme} />
    ),
    [handleComplete, theme]
  );

  const keyExtractor = useCallback((item: Booking) => item.id, []);

  const today = new Date().toLocaleDateString([], { weekday: 'long', month: 'long', day: 'numeric' });

  return (
    <ThemedView style={[styles.screen, { backgroundColor: theme.background }]}>
      {/* Header */}
      <ThemedView style={[styles.header, contentPlatformStyle]}>
        <ThemedText type="title">📋 Bookings</ThemedText>
        <ThemedText themeColor="textSecondary" type="small">{today}</ThemedText>

        {/* Filter Pills */}
        <ScrollView horizontal showsHorizontalScrollIndicator={false} style={styles.filterRow}>
          {(['ALL', 'CONFIRMED', 'COMPLETED'] as FilterType[]).map(f => (
            <TouchableOpacity
              key={f}
              style={[
                styles.filterPill,
                filter === f && { backgroundColor: theme.primary },
              ]}
              onPress={() => setFilter(f)}
              accessibilityLabel={`Filter: ${f}`}
              accessibilityRole="button"
            >
              <ThemedText
                style={[styles.filterPillText, filter === f && { color: '#fff' }]}
              >
                {f}
              </ThemedText>
            </TouchableOpacity>
          ))}
        </ScrollView>
      </ThemedView>

      {/* Booking list */}
      {loading ? (
        <View style={styles.centred}>
          <ActivityIndicator size="large" color={theme.primary} />
        </View>
      ) : filtered.length === 0 ? (
        <View style={styles.centred}>
          <AppIcon name="calendar" color={theme.primary} size={34} />
          <ThemedText themeColor="textSecondary">No {filter !== 'ALL' ? filter.toLowerCase() : ''} bookings</ThemedText>
        </View>
      ) : (
        <FlatList
          data={filtered}
          renderItem={renderItem}
          keyExtractor={keyExtractor}
          contentContainerStyle={[styles.listContent, { paddingBottom: insets.bottom }]}
          contentInsetAdjustmentBehavior="automatic"
          showsVerticalScrollIndicator={false}
        />
      )}

      <CompleteModal
        booking={selectedBooking}
        visible={modalVisible}
        onClose={() => setModalVisible(false)}
        onSubmit={handleSubmitComplete}
      />
    </ThemedView>
  );
}

// ─── Styles ──────────────────────────────────────────────────────────────────

const styles = StyleSheet.create({
  screen: { flex: 1 },

  header: {
    paddingHorizontal: Spacing.four,
    paddingBottom: Spacing.three,
    gap: Spacing.one,
  },

  filterRow: {
    marginTop: Spacing.two,
    flexDirection: 'row',
  },

  filterPill: {
    paddingHorizontal: Spacing.three,
    paddingVertical: Spacing.one + 4,
    borderRadius: 20,
    marginRight: Spacing.two,
    backgroundColor: 'transparent',
    borderWidth: 1,
    borderColor: '#ccc',
    minHeight: 36,
    alignItems: 'center',
    justifyContent: 'center',
  },
  filterPillText: {
    fontSize: 13,
    fontWeight: '600',
  },

  listContent: {
    paddingHorizontal: Spacing.four,
    gap: Spacing.three,
    paddingTop: Spacing.two,
  },

  card: {
    borderRadius: Spacing.three,
    padding: Spacing.four,
    gap: Spacing.two,
  },

  cardHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'flex-start',
  },
  cardHeaderLeft: { gap: 2 },

  cardDetails: { gap: Spacing.one },
  inlineMeta: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.one,
  },

  completeBtn: {
    marginTop: Spacing.two,
    paddingVertical: Spacing.two,
    borderRadius: Spacing.two,
    borderWidth: 1.5,
    alignItems: 'center',
    minHeight: 44,
    justifyContent: 'center',
  },

  badge: {
    paddingHorizontal: Spacing.two,
    paddingVertical: 2,
    borderRadius: Spacing.five,
  },
  badgeText: {
    fontSize: 11,
    fontWeight: '700',
    textTransform: 'uppercase',
  },

  centred: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    gap: Spacing.two,
  },

  // Modal
  modalOverlay: {
    flex: 1,
    backgroundColor: 'rgba(0,0,0,0.5)',
    justifyContent: 'flex-end',
  },
  modalSheet: {
    borderTopLeftRadius: 24,
    borderTopRightRadius: 24,
    padding: Spacing.five,
    gap: Spacing.two,
    paddingBottom: Spacing.six,
  },
  modalHandle: {
    width: 40,
    height: 4,
    borderRadius: 2,
    backgroundColor: '#ccc',
    alignSelf: 'center',
    marginBottom: Spacing.two,
  },
  modalTitle: { fontWeight: '700' },
  modalSubtitle: { fontSize: 13 },

  label: { fontSize: 13, fontWeight: '600', marginTop: Spacing.two },

  textArea: {
    borderWidth: 1,
    borderRadius: Spacing.two,
    padding: Spacing.three,
    minHeight: 100,
    textAlignVertical: 'top',
    fontSize: 14,
  },

  submitBtn: {
    marginTop: Spacing.three,
    paddingVertical: Spacing.three,
    borderRadius: Spacing.two,
    alignItems: 'center',
    minHeight: 48,
    justifyContent: 'center',
  },
  submitBtnText: { color: '#fff', fontWeight: '700', fontSize: 15 },

  cancelBtn: {
    alignItems: 'center',
    paddingVertical: Spacing.two,
    minHeight: 44,
    justifyContent: 'center',
  },
});
