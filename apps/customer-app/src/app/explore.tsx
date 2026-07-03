import React, { useState, useCallback, useEffect } from 'react';
import {
  FlatList,
  Modal,
  Platform,
  Pressable,
  StyleSheet,
  TextInput,
  TouchableOpacity,
  View,
  ActivityIndicator,
  Alert,
} from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useRouter } from 'expo-router';

import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { AppIcon } from '@/components/app-icon';
import { BottomTabInset, Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';
import { useAuth } from '@/context/AuthContext';
import { appConfig } from '@/utils/app-config';
import {
  fetchCustomerAppointments,
  submitAppointmentReview,
  type CustomerAppointmentRecord,
  type HistoryAppointmentStatus,
} from '@/services/customer-history';

// ─── Types ──────────────────────────────────────────────────────────────────

type HistoryTab = 'APPOINTMENTS' | 'ORDERS';
type AppointmentStatus = HistoryAppointmentStatus;

type AppointmentRecord = CustomerAppointmentRecord;

interface OrderRecord {
  id: string;
  providerName: string;
  items: string[];
  total: string;
  orderedAt: string;
  hasReview: boolean;
}

// ─── Mock data ───────────────────────────────────────────────────────────────

const MOCK_APPOINTMENTS: AppointmentRecord[] = [
  {
    id: 'a1',
    providerName: 'Happy Paws Clinic',
    serviceName: 'Annual Vaccination',
    petName: 'Bruno',
    slotStartsAt: new Date(Date.now() - 3 * 86400_000).toISOString(),
    status: 'COMPLETED',
    hasReview: false,
    providerId: 'demo-provider-1',
  },
  {
    id: 'a2',
    providerName: 'PetGroom Studio',
    serviceName: 'Full Grooming',
    petName: 'Milo',
    slotStartsAt: new Date(Date.now() + 2 * 86400_000).toISOString(),
    status: 'CONFIRMED',
    hasReview: false,
    providerId: 'demo-provider-2',
  },
  {
    id: 'a3',
    providerName: 'VetCare Plus',
    serviceName: 'Dental Cleaning',
    petName: 'Bruno',
    slotStartsAt: new Date(Date.now() - 10 * 86400_000).toISOString(),
    status: 'COMPLETED',
    hasReview: true,
    providerId: 'demo-provider-3',
  },
];

const MOCK_ORDERS: OrderRecord[] = [
  {
    id: 'o1',
    providerName: 'PetMart Store',
    items: ['Royal Canin 2kg', 'Dental Chews'],
    total: '₹1,240',
    orderedAt: new Date(Date.now() - 5 * 86400_000).toISOString(),
    hasReview: false,
  },
  {
    id: 'o2',
    providerName: 'Whiskers & Tails',
    items: ['Cat Litter 5kg'],
    total: '₹680',
    orderedAt: new Date(Date.now() - 12 * 86400_000).toISOString(),
    hasReview: true,
  },
];

// ─── Star Rating ──────────────────────────────────────────────────────────────

function StarRating({ rating, onRate }: { rating: number; onRate: (n: number) => void }) {
  return (
    <View style={styles.starRow} accessibilityLabel={`Rating: ${rating} of 5`}>
      {[1, 2, 3, 4, 5].map(n => (
        <TouchableOpacity
          key={n}
          onPress={() => onRate(n)}
          style={styles.starBtn}
          accessibilityLabel={`Rate ${n} star${n > 1 ? 's' : ''}`}
          accessibilityRole="button"
        >
          <ThemedText style={[styles.star, n <= rating && styles.starFilled]}>
            {n <= rating ? '★' : '☆'}
          </ThemedText>
        </TouchableOpacity>
      ))}
    </View>
  );
}

// ─── Review Modal ─────────────────────────────────────────────────────────────

interface ReviewTarget {
  id: string;
  type: 'APPOINTMENT' | 'ORDER';
  providerName: string;
  providerId: string;
}

interface ReviewModalProps {
  target: ReviewTarget | null;
  visible: boolean;
  onClose: () => void;
  onSubmit: (targetId: string, rating: number, comment: string) => Promise<void>;
}

function ReviewModal({ target, visible, onClose, onSubmit }: ReviewModalProps) {
  const [rating, setRating] = useState(0);
  const [comment, setComment] = useState('');
  const [loading, setLoading] = useState(false);
  const theme = useTheme();

  const handleSubmit = useCallback(async () => {
    if (!target || rating === 0) {
      Alert.alert('Please select a rating');
      return;
    }
    setLoading(true);
    await new Promise(r => setTimeout(r, 600));
    try {
      await onSubmit(target.id, rating, comment);
      setRating(0);
      setComment('');
      onClose();
    } finally {
      setLoading(false);
    }
  }, [target, rating, comment, onSubmit, onClose]);

  if (!target) return null;

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
          <View style={styles.modalHandle} />

          <ThemedText type="subtitle" style={styles.modalTitle}>
            Leave a Review
          </ThemedText>
          <ThemedText themeColor="textSecondary">
            {target.providerName}
          </ThemedText>

          <ThemedText style={styles.label}>Your Rating</ThemedText>
          <StarRating rating={rating} onRate={setRating} />

          <ThemedText style={styles.label}>Comment (optional)</ThemedText>
          <TextInput
            style={[styles.textArea, { borderColor: '#ccc', color: theme.text, backgroundColor: theme.backgroundElement }]}
            multiline
            numberOfLines={3}
            placeholder="Share your experience..."
            placeholderTextColor={theme.textSecondary}
            value={comment}
            onChangeText={setComment}
            accessibilityLabel="Review comment input"
          />

          <TouchableOpacity
            style={[styles.submitBtn, { backgroundColor: theme.primary }, rating === 0 && { opacity: 0.5 }]}
            onPress={handleSubmit}
            disabled={loading || rating === 0}
            accessibilityLabel="Submit review"
            accessibilityRole="button"
          >
            {loading ? (
              <ActivityIndicator color="#fff" />
            ) : (
              <ThemedText style={styles.submitBtnText}>Submit Review</ThemedText>
            )}
          </TouchableOpacity>

          <TouchableOpacity style={styles.cancelBtn} onPress={onClose} accessibilityLabel="Cancel" accessibilityRole="button">
            <ThemedText themeColor="textSecondary">Cancel</ThemedText>
          </TouchableOpacity>
        </Pressable>
      </Pressable>
    </Modal>
  );
}

// ─── Appointment Card ────────────────────────────────────────────────────────

const STATUS_COLORS: Record<AppointmentStatus, string> = {
  SLOT_HELD: '#a855f7',
  CONFIRMED:  '#22c55e',
  COMPLETED:  '#3b82f6',
  CANCELLED:  '#ef4444',
  NO_SHOW:    '#f97316',
  EXPIRED: '#64748b',
};

interface ApptCardProps {
  item: AppointmentRecord;
  onReview: (target: ReviewTarget) => void;
  onMessage: (item: AppointmentRecord) => void;
  theme: ReturnType<typeof useTheme>;
}

const AppointmentCard = React.memo(function AppointmentCard({ item, onReview, onMessage, theme }: ApptCardProps) {
  const date = new Date(item.slotStartsAt).toLocaleDateString([], { weekday: 'short', month: 'short', day: 'numeric' });
  const time = new Date(item.slotStartsAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });

  return (
    <ThemedView type="backgroundElement" style={styles.card}>
      <View style={styles.cardHeader}>
        <View style={styles.cardHeaderLeft}>
          <ThemedText style={{ fontWeight: '700' }}>{item.providerName}</ThemedText>
          <View style={styles.inlineMeta}>
            <AppIcon name="paw" color={theme.textSecondary} size={14} />
            <ThemedText type="small" themeColor="textSecondary">{item.petName} · {item.serviceName}</ThemedText>
          </View>
        </View>
        <View style={[styles.badge, { backgroundColor: STATUS_COLORS[item.status] + '22' }]}>
          <ThemedText style={[styles.badgeText, { color: STATUS_COLORS[item.status] }]}>
            {item.status}
          </ThemedText>
        </View>
      </View>

      <View style={styles.inlineMeta}>
        <AppIcon name="calendar" color={theme.textSecondary} size={14} />
        <ThemedText type="small" themeColor="textSecondary">{date} · {time}</ThemedText>
      </View>

      <TouchableOpacity
        style={[styles.reviewBtn, { borderColor: theme.cta }]}
        onPress={() => onMessage(item)}
        accessibilityLabel={`Message ${item.providerName}`}
        accessibilityRole="button"
      >
        <AppIcon name="support" color={theme.cta} size={16} />
        <ThemedText style={{ color: theme.cta, fontWeight: '600' }}>Message</ThemedText>
      </TouchableOpacity>

      {item.status === 'COMPLETED' && !item.hasReview && (
        <TouchableOpacity
          style={[styles.reviewBtn, { borderColor: theme.primary }]}
          onPress={() => onReview({ id: item.id, type: 'APPOINTMENT', providerName: item.providerName, providerId: item.providerId })}
          accessibilityLabel={`Review ${item.providerName}`}
          accessibilityRole="button"
        >
          <AppIcon name="star" color={theme.primary} size={16} />
          <ThemedText style={{ color: theme.primary, fontWeight: '600' }}>Leave a Review</ThemedText>
        </TouchableOpacity>
      )}
      {item.hasReview && (
        <ThemedText type="small" themeColor="textSecondary">Reviewed</ThemedText>
      )}
    </ThemedView>
  );
});

// ─── Order Card ──────────────────────────────────────────────────────────────

interface OrderCardProps {
  item: OrderRecord;
  onReview: (target: ReviewTarget) => void;
  theme: ReturnType<typeof useTheme>;
}

const OrderCard = React.memo(function OrderCard({ item, onReview, theme }: OrderCardProps) {
  const date = new Date(item.orderedAt).toLocaleDateString([], { weekday: 'short', month: 'short', day: 'numeric' });

  return (
    <ThemedView type="backgroundElement" style={styles.card}>
      <View style={styles.cardHeader}>
        <View style={styles.cardHeaderLeft}>
          <ThemedText style={{ fontWeight: '700' }}>{item.providerName}</ThemedText>
          <ThemedText type="small" themeColor="textSecondary">{item.items.join(', ')}</ThemedText>
        </View>
        <ThemedText style={{ fontWeight: '700' }}>{item.total}</ThemedText>
      </View>
      <View style={styles.inlineMeta}>
        <AppIcon name="calendar" color={theme.textSecondary} size={14} />
        <ThemedText type="small" themeColor="textSecondary">{date}</ThemedText>
      </View>

      {!item.hasReview && (
        <TouchableOpacity
          style={[styles.reviewBtn, { borderColor: theme.primary }]}
          onPress={() => onReview({ id: item.id, type: 'ORDER', providerName: item.providerName, providerId: 'mock-provider-id' })}
          accessibilityLabel={`Review ${item.providerName}`}
          accessibilityRole="button"
        >
          <AppIcon name="star" color={theme.primary} size={16} />
          <ThemedText style={{ color: theme.primary, fontWeight: '600' }}>Leave a Review</ThemedText>
        </TouchableOpacity>
      )}
      {item.hasReview && (
        <ThemedText type="small" themeColor="textSecondary">Reviewed</ThemedText>
      )}
    </ThemedView>
  );
});

// ─── Main Screen ──────────────────────────────────────────────────────────────

export default function HistoryScreen() {
  const theme = useTheme();
  const router = useRouter();
  const safeAreaInsets = useSafeAreaInsets();
  const { user, session } = useAuth();
  const userId = user?.id;
  const accessToken = session?.access_token;
  const [activeTab, setActiveTab] = useState<HistoryTab>('APPOINTMENTS');
  const [appointments, setAppointments] = useState<AppointmentRecord[]>(appConfig.allowDemoMode ? MOCK_APPOINTMENTS : []);
  const [orders, setOrders] = useState<OrderRecord[]>(appConfig.allowDemoMode ? MOCK_ORDERS : []);
  const [reviewTarget, setReviewTarget] = useState<ReviewTarget | null>(null);
  const [modalVisible, setModalVisible] = useState(false);
  const [loadingAppointments, setLoadingAppointments] = useState(!appConfig.allowDemoMode);
  const [loadError, setLoadError] = useState<string | null>(null);

  const insets = {
    ...safeAreaInsets,
    bottom: safeAreaInsets.bottom + BottomTabInset + Spacing.three,
  };

  const contentPlatformStyle = Platform.select({
    android: { paddingTop: insets.top + Spacing.two },
    web: { paddingTop: Spacing.six },
  });

  const loadAppointments = useCallback(async () => {
    if (appConfig.allowDemoMode) {
      setAppointments(MOCK_APPOINTMENTS);
      setOrders(MOCK_ORDERS);
      setLoadError(null);
      setLoadingAppointments(false);
      return;
    }

    if (!userId) {
      setAppointments([]);
      setOrders([]);
      setLoadError('Sign in to view appointment history.');
      setLoadingAppointments(false);
      return;
    }

    setLoadingAppointments(true);
    try {
      const liveAppointments = await fetchCustomerAppointments(userId, accessToken);
      setAppointments(liveAppointments);
      setOrders([]);
      setLoadError(null);
    } catch (error) {
      const message = error instanceof Error ? error.message : 'Could not load appointment history.';
      setAppointments([]);
      setOrders([]);
      setLoadError(message);
    } finally {
      setLoadingAppointments(false);
    }
  }, [accessToken, userId]);

  useEffect(() => {
    void loadAppointments();
  }, [loadAppointments]);

  const handleReview = useCallback((target: ReviewTarget) => {
    setReviewTarget(target);
    setModalVisible(true);
  }, []);

  const handleMessage = useCallback((item: AppointmentRecord) => {
    router.push({
      pathname: '/chat',
      params: {
        contextType: 'APPOINTMENT',
        contextId: item.id,
        providerId: item.providerId,
        title: item.providerName,
      },
    } as never);
  }, [router]);

  const handleSubmitReview = useCallback(async (targetId: string, rating: number, comment: string) => {
    if (!reviewTarget) return;

    try {
      if (appConfig.allowDemoMode) {
        if (reviewTarget.type === 'APPOINTMENT') {
          setAppointments(prev => prev.map(a => a.id === targetId ? { ...a, hasReview: true } : a));
        } else {
          setOrders(prev => prev.map(o => o.id === targetId ? { ...o, hasReview: true } : o));
        }
      } else {
        if (!userId) throw new Error('Please sign in before reviewing.');
        if (reviewTarget.type !== 'APPOINTMENT') throw new Error('Order reviews are not available yet.');
        const result = await submitAppointmentReview({
          customerId: userId,
          providerId: reviewTarget.providerId,
          targetId,
          rating,
          comment,
          accessToken,
        });
        setAppointments(prev => prev.map(a => a.id === targetId ? { ...a, hasReview: true } : a));
        if (result === 'duplicate') {
          Alert.alert('Already Reviewed', 'This appointment already has a review.');
          return;
        }
      }
      Alert.alert('Thank you!', 'Your review has been submitted.');
    } catch (error) {
      const message = error instanceof Error ? error.message : 'Review was not submitted.';
      Alert.alert('Could Not Submit Review', message);
      throw error;
    }
  }, [accessToken, reviewTarget, userId]);

  const renderAppointment = useCallback(
    ({ item }: { item: AppointmentRecord }) => (
      <AppointmentCard item={item} onReview={handleReview} onMessage={handleMessage} theme={theme} />
    ),
    [handleMessage, handleReview, theme]
  );

  const renderOrder = useCallback(
    ({ item }: { item: OrderRecord }) => (
      <OrderCard item={item} onReview={handleReview} theme={theme} />
    ),
    [handleReview, theme]
  );

  const apptKeyExtractor = useCallback((item: AppointmentRecord) => item.id, []);
  const orderKeyExtractor = useCallback((item: OrderRecord) => item.id, []);

  return (
    <ThemedView style={[styles.screen, { backgroundColor: theme.background }]}>
      {/* Header */}
      <ThemedView style={[styles.header, contentPlatformStyle]}>
        <ThemedText type="title">History</ThemedText>
        <ThemedText themeColor="textSecondary" type="small">Your appointments &amp; orders</ThemedText>

        {/* Toggle */}
        <View style={[styles.toggle, { backgroundColor: theme.backgroundElement }]}>
          {(['APPOINTMENTS', 'ORDERS'] as HistoryTab[]).map(tab => (
            <TouchableOpacity
              key={tab}
              style={[styles.toggleBtn, activeTab === tab && { backgroundColor: theme.primary }]}
              onPress={() => setActiveTab(tab)}
              accessibilityLabel={tab === 'APPOINTMENTS' ? 'View Appointments' : 'View Orders'}
              accessibilityRole="button"
            >
              <ThemedText style={[styles.toggleBtnText, activeTab === tab && { color: '#fff' }]}>
                {tab === 'APPOINTMENTS' ? 'Appointments' : 'Orders'}
              </ThemedText>
            </TouchableOpacity>
          ))}
        </View>
      </ThemedView>

      {activeTab === 'APPOINTMENTS' ? (
        loadingAppointments ? (
          <View style={styles.centred}>
            <ActivityIndicator size="large" color={theme.primary} />
          </View>
        ) : loadError ? (
          <View style={styles.centred}>
            <AppIcon name="paw" color={theme.primary} size={34} />
            <ThemedText themeColor="textSecondary">{loadError}</ThemedText>
            <TouchableOpacity
              style={[styles.reviewBtn, { borderColor: theme.primary, paddingHorizontal: Spacing.four }]}
              onPress={() => void loadAppointments()}
              accessibilityLabel="Retry loading appointment history"
              accessibilityRole="button"
            >
              <ThemedText style={{ color: theme.primary, fontWeight: '600' }}>Retry</ThemedText>
            </TouchableOpacity>
          </View>
        ) : appointments.length === 0 ? (
          <View style={styles.centred}>
            <AppIcon name="paw" color={theme.primary} size={34} />
            <ThemedText themeColor="textSecondary">No appointments yet</ThemedText>
          </View>
        ) : (
          <FlatList
            data={appointments}
            renderItem={renderAppointment}
            keyExtractor={apptKeyExtractor}
            contentContainerStyle={[styles.listContent, { paddingBottom: insets.bottom }]}
            showsVerticalScrollIndicator={false}
          />
        )
      ) : (
        orders.length === 0 ? (
          <View style={styles.centred}>
            <AppIcon name="cart" color={theme.primary} size={34} />
            <ThemedText themeColor="textSecondary">No orders yet</ThemedText>
          </View>
        ) : (
          <FlatList
            data={orders}
            renderItem={renderOrder}
            keyExtractor={orderKeyExtractor}
            contentContainerStyle={[styles.listContent, { paddingBottom: insets.bottom }]}
            showsVerticalScrollIndicator={false}
          />
        )
      )}

      <ReviewModal
        target={reviewTarget}
        visible={modalVisible}
        onClose={() => setModalVisible(false)}
        onSubmit={handleSubmitReview}
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

  toggle: {
    flexDirection: 'row',
    borderRadius: Spacing.two,
    marginTop: Spacing.three,
    padding: 3,
  },
  toggleBtn: {
    flex: 1,
    paddingVertical: Spacing.two,
    borderRadius: Spacing.two - 2,
    alignItems: 'center',
    minHeight: 44,
    justifyContent: 'center',
  },
  toggleBtnText: { fontSize: 13, fontWeight: '600' },

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
  cardHeaderLeft: { flex: 1, gap: 2 },
  inlineMeta: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.one,
  },

  badge: {
    paddingHorizontal: Spacing.two,
    paddingVertical: 2,
    borderRadius: Spacing.five,
  },
  badgeText: { fontSize: 11, fontWeight: '700', textTransform: 'uppercase' },

  reviewBtn: {
    marginTop: Spacing.two,
    paddingVertical: Spacing.two,
    borderRadius: Spacing.two,
    borderWidth: 1.5,
    alignItems: 'center',
    minHeight: 44,
    justifyContent: 'center',
    flexDirection: 'row',
    gap: Spacing.one,
  },

  centred: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    gap: Spacing.two,
  },

  // Star Rating
  starRow: {
    flexDirection: 'row',
    gap: Spacing.two,
    marginVertical: Spacing.two,
  },
  starBtn: { minWidth: 44, minHeight: 44, alignItems: 'center', justifyContent: 'center' },
  star: { fontSize: 32, color: '#ccc' },
  starFilled: { color: '#f59e0b' },

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
  label: { fontSize: 13, fontWeight: '600', marginTop: Spacing.two },

  textArea: {
    borderWidth: 1,
    borderRadius: Spacing.two,
    padding: Spacing.three,
    minHeight: 80,
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
