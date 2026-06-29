import React, { useCallback, useState, useEffect, useRef } from 'react';
import {
  StyleSheet, View, FlatList, TouchableOpacity,
  ActivityIndicator, Modal, ScrollView, Alert, useColorScheme,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { AppIcon } from '@/components/app-icon';
import { Spacing, Colors, Radius, Shadows } from '@/constants/theme';
import { appConfig } from '@/utils/app-config';

const HOLD_DURATION_SECONDS = 300;
const MOCK_PET_ID = 'pet-001';
const MOCK_CUSTOMER_ID = 'customer-001';

const BACKUP_SALONS: Salon[] = [
  { id: 'g-001', name: 'Pet Spa & Bath (Demo)', services: 'Bath & Brush, Full Groom, Nail Trim', nextSlot: 'Today, 5:00 PM', distance: '1.4 km', rating: '4.9', ratingCount: '110' },
  { id: 'g-002', name: 'Grooming Tails Center (Demo)', services: 'Hair Trimming, Ear Cleaning, Massage', nextSlot: 'Tomorrow, 11:30 AM', distance: '1.9 km', rating: '4.7', ratingCount: '78' },
];

const BACKUP_SLOTS: Slot[] = [
  { id: 'sg-001', startTime: 'Today, 5:00 PM', endTime: 'Today, 5:30 PM', price: 349 },
  { id: 'sg-002', startTime: 'Today, 5:30 PM', endTime: 'Today, 6:00 PM', price: 349 },
  { id: 'sg-003', startTime: 'Today, 6:00 PM', endTime: 'Today, 6:30 PM', price: 449 },
  { id: 'sg-004', startTime: 'Tomorrow, 11:00 AM', endTime: 'Tomorrow, 11:30 AM', price: 349 },
];

// ─── Types ───────────────────────────────────────────────────────────────────

interface Slot {
  id: string;
  startTime: string;
  endTime: string;
  price: number;
}

type BookingPhase = 'idle' | 'selecting' | 'holding' | 'checkout' | 'confirming' | 'success';

function formatCountdown(secs: number): string {
  const m = Math.floor(secs / 60).toString().padStart(2, '0');
  const s = (secs % 60).toString().padStart(2, '0');
  return `${m}:${s}`;
}

interface Salon {
  id: string;
  name: string;
  services: string;
  nextSlot: string;
  distance: string;
  rating: string;
  ratingCount: string;
}

// ─── SalonCard ───────────────────────────────────────────────────────────────────

const SalonCard = React.memo(({
  item, colors, onBook,
}: { item: Salon; colors: any; onBook: (s: Salon) => void }) => (
  <TouchableOpacity
    style={[styles.salonCard, { backgroundColor: colors.backgroundElement, borderColor: colors.textSecondary }]}
    activeOpacity={0.7}
  >
    <View style={styles.salonHeader}>
      <View style={{ flex: 1 }}>
        <ThemedText style={[styles.name, { color: colors.text }]}>{item.name}</ThemedText>
        <ThemedText type="small" style={{ color: colors.textSecondary, marginTop: Spacing.half }}>
          {item.services}
        </ThemedText>
      </View>
      <View style={[styles.ratingBadge, { backgroundColor: colors.background, borderColor: colors.primary }]}>
        <AppIcon name="star" color={colors.primary} size={14} />
        <ThemedText type="small" style={{ color: colors.text, fontWeight: '700' }}>{item.rating}</ThemedText>
      </View>
    </View>

    <View style={[styles.slotContainer, { backgroundColor: colors.backgroundSelected }]}>
      <ThemedText type="small" style={{ color: colors.text, fontWeight: '500' }}>Next Available Slot:</ThemedText>
      <View style={styles.inlineIconText}>
        <AppIcon name="calendar" color={colors.cta} size={16} />
        <ThemedText style={{ color: colors.cta, fontWeight: '800' }}>{item.nextSlot}</ThemedText>
      </View>
    </View>

    <View style={styles.metaRow}>
      <View style={styles.inlineIconText}>
        <AppIcon name="location" color={colors.textSecondary} size={14} />
        <ThemedText type="small" style={{ color: colors.textSecondary }}>{item.distance} away</ThemedText>
      </View>
      <TouchableOpacity
        id={`book-groom-${item.id}`}
        style={[styles.bookButton, { backgroundColor: colors.cta, borderColor: colors.text }]}
        activeOpacity={0.8}
        onPress={() => onBook(item)}
        accessibilityRole="button"
        accessibilityLabel={`Book spa at ${item.name}`}
      >
        <ThemedText type="small" style={{ color: '#ffffff', fontWeight: '800' }}>Book Spa</ThemedText>
      </TouchableOpacity>
    </View>
  </TouchableOpacity>
));
SalonCard.displayName = 'SalonCard';

// ─── SlotPickerModal ───────────────────────────────────────────────────────────────────

const SlotPickerModal = ({
  visible, salon, slots, loadingSlots, colors, onSelect, onClose,
}: {
  visible: boolean;
  salon: Salon | null;
  slots: Slot[];
  loadingSlots: boolean;
  colors: any;
  onSelect: (s: Slot) => void;
  onClose: () => void;
}) => (
  <Modal visible={visible} transparent animationType="slide" onRequestClose={onClose}>
    <View style={styles.modalOverlay}>
      <View style={[styles.bottomSheet, { backgroundColor: colors.backgroundElement }]}>
        <View style={[styles.sheetHandle, { backgroundColor: colors.textSecondary }]} />
        <ThemedText style={[styles.sheetTitle, { color: colors.text }]}>Pick a Slot</ThemedText>
        <ThemedText type="small" style={{ color: colors.textSecondary, marginBottom: Spacing.three }}>
          {salon?.name}
        </ThemedText>

        {loadingSlots ? (
          <ActivityIndicator size="large" color={colors.primary} style={{ marginVertical: Spacing.six }} />
        ) : (
          <ScrollView showsVerticalScrollIndicator={false}>
            {slots.map(slot => (
              <TouchableOpacity
                key={slot.id}
                id={`slot-groom-${slot.id}`}
                style={[styles.slotRow, { borderColor: colors.backgroundSelected, backgroundColor: colors.background }]}
                activeOpacity={0.75}
                onPress={() => onSelect(slot)}
                accessibilityRole="button"
                accessibilityLabel={`Select slot ${slot.startTime}`}
              >
                <View>
                  <ThemedText style={{ color: colors.text, fontWeight: '700' }}>{slot.startTime}</ThemedText>
                  <ThemedText type="small" style={{ color: colors.textSecondary }}>until {slot.endTime}</ThemedText>
                </View>
                <View style={[styles.priceBadge, { backgroundColor: colors.cta }]}>
                  <ThemedText type="small" style={{ color: '#fff', fontWeight: '800' }}>₹{slot.price}</ThemedText>
                </View>
              </TouchableOpacity>
            ))}
          </ScrollView>
        )}

        <TouchableOpacity
          style={[styles.cancelButton, { borderColor: colors.textSecondary }]}
          onPress={onClose}
          accessibilityRole="button"
          accessibilityLabel="Cancel"
        >
          <ThemedText style={{ color: colors.textSecondary, fontWeight: '600' }}>Cancel</ThemedText>
        </TouchableOpacity>
      </View>
    </View>
  </Modal>
);

// ─── SummaryRow + CheckoutOverlay ────────────────────────────────────────────────────────────

const SummaryRow = ({ label, value, colors, bold = false }: {
  label: string; value: string; colors: any; bold?: boolean;
}) => (
  <View style={styles.summaryRow}>
    <ThemedText type="small" style={{ color: colors.textSecondary }}>{label}</ThemedText>
    <ThemedText type="small" style={{ color: colors.text, fontWeight: bold ? '800' : '500' }}>{value}</ThemedText>
  </View>
);

const CheckoutOverlay = ({
  visible, salon, slot, countdown, phase, colors, onConfirm, onCancel,
}: {
  visible: boolean;
  salon: Salon | null;
  slot: Slot | null;
  countdown: number;
  phase: BookingPhase;
  colors: any;
  onConfirm: () => void;
  onCancel: () => void;
}) => {
  const urgency = countdown < 60;
  return (
    <Modal visible={visible} transparent animationType="fade" onRequestClose={onCancel}>
      <View style={[styles.modalOverlay, { justifyContent: 'center' }]}>
        <View style={[styles.checkoutCard, { backgroundColor: colors.backgroundElement }]}>
          <View style={[styles.timerBadge, { backgroundColor: urgency ? '#FF3B30' : colors.backgroundSelected }]}>
            <ThemedText style={[styles.timerText, { color: urgency ? '#fff' : colors.text }]}>
              ⏱ {formatCountdown(countdown)}
            </ThemedText>
            <ThemedText type="small" style={{ color: urgency ? '#ffd5d5' : colors.textSecondary, textAlign: 'center' }}>
              Slot reserved — complete payment before time runs out
            </ThemedText>
          </View>

          <ThemedText style={[styles.sheetTitle, { color: colors.text, marginTop: Spacing.three }]}>
            Confirm Booking
          </ThemedText>

          <View style={[styles.summaryBox, { backgroundColor: colors.background, borderColor: colors.backgroundSelected }]}>
            <SummaryRow label="Salon" value={salon?.name ?? ''} colors={colors} />
            <SummaryRow label="Slot" value={slot?.startTime ?? ''} colors={colors} />
            <SummaryRow label="Duration" value={`until ${slot?.endTime ?? ''}`} colors={colors} />
            <SummaryRow label="Amount" value={`₹${slot?.price ?? 0}`} colors={colors} bold />
          </View>

          {phase === 'confirming' ? (
            <ActivityIndicator size="large" color={colors.primary} style={{ marginVertical: Spacing.four }} />
          ) : phase === 'success' ? (
            <View style={styles.successBanner}>
              <ThemedText style={{ fontSize: 40 }}>✅</ThemedText>
              <ThemedText style={{ color: colors.text, fontWeight: '800', fontSize: 16, marginTop: Spacing.one }}>
                Booking Confirmed!
              </ThemedText>
              <ThemedText type="small" style={{ color: colors.textSecondary, textAlign: 'center', marginTop: Spacing.half }}>
                Your grooming appointment is secured.
              </ThemedText>
            </View>
          ) : (
            <>
              <TouchableOpacity
                id="pay-confirm-groom"
                style={[styles.payButton, { backgroundColor: colors.cta }]}
                activeOpacity={0.8}
                onPress={onConfirm}
                accessibilityRole="button"
                accessibilityLabel="Pay and confirm grooming appointment"
              >
                <ThemedText style={{ color: '#fff', fontWeight: '800', fontSize: 16 }}>
                  💳  Pay ₹{slot?.price ?? 0} &amp; Confirm
                </ThemedText>
              </TouchableOpacity>
              <TouchableOpacity
                style={[styles.cancelButton, { borderColor: colors.textSecondary, marginTop: Spacing.two }]}
                onPress={onCancel}
                accessibilityRole="button"
                accessibilityLabel="Cancel booking"
              >
                <ThemedText style={{ color: colors.textSecondary }}>Cancel</ThemedText>
              </TouchableOpacity>
            </>
          )}
        </View>
      </View>
    </Modal>
  );
};

// ─── GroomScreen ──────────────────────────────────────────────────────────────

export default function GroomScreen() {
  const scheme = useColorScheme() === 'dark' ? 'dark' : 'light';
  const colors = Colors[scheme];

  const [salons, setSalons] = useState<Salon[]>([]);
  const [isLoading, setIsLoading] = useState(true);

  // Booking flow
  const [selectedSalon, setSelectedSalon] = useState<Salon | null>(null);
  const [slots, setSlots] = useState<Slot[]>([]);
  const [loadingSlots, setLoadingSlots] = useState(false);
  const [selectedSlot, setSelectedSlot] = useState<Slot | null>(null);
  const [phase, setPhase] = useState<BookingPhase>('idle');
  const [heldAppointmentId, setHeldAppointmentId] = useState<string | null>(null);
  const [countdown, setCountdown] = useState(HOLD_DURATION_SECONDS);
  const countdownRef = useRef<ReturnType<typeof setInterval> | null>(null);

  useEffect(() => {
    if (phase === 'checkout') {
      setCountdown(HOLD_DURATION_SECONDS);
      countdownRef.current = setInterval(() => {
        setCountdown(prev => {
          if (prev <= 1) {
            clearInterval(countdownRef.current!);
            Alert.alert('Time Expired', 'Your slot hold has expired. Please try again.');
            setPhase('idle');
            return 0;
          }
          return prev - 1;
        });
      }, 1000);
    } else {
      if (countdownRef.current) clearInterval(countdownRef.current);
    }
    return () => { if (countdownRef.current) clearInterval(countdownRef.current); };
  }, [phase]);

  const [coords, setCoords] = useState({ longitude: 77.6404, latitude: 12.9719 });

  const fetchSalons = async () => {
    try {
      const response = await fetch(
        `${appConfig.apiBaseUrl}/api/v1/discovery/providers?longitude=${coords.longitude}&latitude=${coords.latitude}&radius=10.0&type=GROOMING_CENTER`,
        { headers: { Accept: 'application/json' } },
      );
      if (!response.ok) throw new Error();
      const data = await response.json();
      setSalons(data.map((p: any) => ({
        id: p.providerId,
        name: p.name,
        services: p.description ? p.description.substring(0, 35) : 'General Styling, Bathing, Nail Care',
        nextSlot: 'Today, 6:00 PM',
        distance: `${p.distanceKm.toFixed(1)} km`,
        rating: p.ratingAvg ? p.ratingAvg.toFixed(1) : '0.0',
        ratingCount: p.ratingCount ? p.ratingCount.toString() : '0',
      })));
    } catch {
      setSalons(appConfig.allowDemoMode ? BACKUP_SALONS : []);
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    if (typeof navigator !== 'undefined' && navigator.geolocation) {
      navigator.geolocation.getCurrentPosition(
        (position) => {
          setCoords({
            longitude: position.coords.longitude,
            latitude: position.coords.latitude
          });
        },
        (error) => {
          console.warn("Failed to get geolocation, using Indiranagar default:", error.message);
        }
      );
    }
  }, []);

  useEffect(() => {
    fetchSalons();
  }, [coords]);

  const fetchSlots = async (providerId: string) => {
    setLoadingSlots(true);
    try {
      const res = await fetch(
        `${appConfig.apiBaseUrl}/api/v1/catalog/providers/${providerId}/slots?status=AVAILABLE`,
        { headers: { Accept: 'application/json' } },
      );
      if (!res.ok) throw new Error();
      const data = await res.json();
      const mapped: Slot[] = data.map((s: any) => ({
        id: s.slotId,
        startTime: new Date(s.startTime).toLocaleString('en-IN', { weekday: 'short', hour: '2-digit', minute: '2-digit' }),
        endTime: new Date(s.endTime).toLocaleString('en-IN', { hour: '2-digit', minute: '2-digit' }),
        price: s.price ?? 349,
      }));
      setSlots(mapped.length > 0 ? mapped : appConfig.allowDemoMode ? BACKUP_SLOTS : []);
    } catch {
      setSlots(appConfig.allowDemoMode ? BACKUP_SLOTS : []);
    } finally {
      setLoadingSlots(false);
    }
  };

  const handleBookPress = useCallback((salon: Salon) => {
    setSelectedSalon(salon);
    setSlots([]);
    setPhase('selecting');
    fetchSlots(salon.id);
  }, []);

  const handleSlotSelect = useCallback(async (slot: Slot) => {
    setSelectedSlot(slot);
    setPhase('holding');
    try {
      const res = await fetch(`${appConfig.apiBaseUrl}/api/v1/appointments/hold`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          slotId: slot.id,
          petId: MOCK_PET_ID,
          customerId: MOCK_CUSTOMER_ID,
          notes: 'Grooming via PawsNearMe',
        }),
      });
      if (!res.ok) {
        const err = await res.json().catch(() => ({}));
        Alert.alert('Slot Unavailable', err.message ?? 'This slot was just taken. Please choose another.');
        setPhase('selecting');
        return;
      }
      const data = await res.json();
      setHeldAppointmentId(data.appointmentId ?? data.id);
      setPhase('checkout');
    } catch {
      if (appConfig.allowDemoMode) {
        setHeldAppointmentId('demo-groom-001');
        setPhase('checkout');
      } else {
        Alert.alert('Booking Unavailable', 'Could not hold this slot. Please try again when the service is reachable.');
        setPhase('selecting');
      }
    }
  }, []);

  const handleConfirmPayment = useCallback(async () => {
    setPhase('confirming');
    try {
      const res = await fetch(`${appConfig.apiBaseUrl}/api/v1/appointments/${heldAppointmentId}/confirm`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ paymentId: `pay_${Date.now()}` }),
      });
      if (!res.ok) throw new Error();
    } catch {
      if (!appConfig.allowDemoMode) {
        Alert.alert('Payment Confirmation Failed', 'The appointment was not confirmed. Please retry.');
        setPhase('checkout');
        return;
      }
    }
    setPhase('success');
    setTimeout(() => setPhase('idle'), 2500);
  }, [heldAppointmentId]);

  const handleCancelSlots = useCallback(() => setPhase('idle'), []);

  const handleCancelCheckout = useCallback(() => {
    setPhase('idle');
    setHeldAppointmentId(null);
    setSelectedSlot(null);
  }, []);

  const renderSalon = useCallback(({ item }: { item: Salon }) => (
    <SalonCard item={item} colors={colors} onBook={handleBookPress} />
  ), [colors, handleBookPress]);

  const keyExtractor = useCallback((item: Salon) => item.id, []);

  const showSlotPicker = phase === 'selecting';
  const showCheckout = phase === 'holding' || phase === 'checkout' || phase === 'confirming' || phase === 'success';

  return (
    <ThemedView style={[styles.container, { backgroundColor: colors.background }]}>
      <SafeAreaView style={styles.safeArea}>
        <View style={[styles.header, { borderBottomColor: colors.backgroundSelected }]}>
          <ThemedText type="subtitle" style={{ color: colors.text, fontWeight: '800' }}>
            Book a Groomer
          </ThemedText>
          <View style={styles.headerSub}>
            <ThemedText type="small" style={{ color: colors.textSecondary, flex: 1 }}>
              Professional styling, bath, and hygiene treatments
            </ThemedText>
            {isLoading && <ActivityIndicator size="small" color={colors.primary} />}
          </View>
        </View>

        <FlatList
          data={salons}
          renderItem={renderSalon}
          keyExtractor={keyExtractor}
          contentContainerStyle={styles.listContent}
        />
      </SafeAreaView>

      <SlotPickerModal
        visible={showSlotPicker}
        salon={selectedSalon}
        slots={slots}
        loadingSlots={loadingSlots}
        colors={colors}
        onSelect={handleSlotSelect}
        onClose={handleCancelSlots}
      />

      <CheckoutOverlay
        visible={showCheckout}
        salon={selectedSalon}
        slot={selectedSlot}
        countdown={countdown}
        phase={phase}
        colors={colors}
        onConfirm={handleConfirmPayment}
        onCancel={handleCancelCheckout}
      />
    </ThemedView>
  );
}

// ─── Styles ───────────────────────────────────────────────────────────────────

const styles = StyleSheet.create({
  container: { flex: 1 },
  safeArea: { flex: 1 },
  header: { paddingHorizontal: Spacing.four, paddingVertical: Spacing.three, borderBottomWidth: 1 },
  headerSub: { flexDirection: 'row', alignItems: 'center', marginTop: Spacing.one },
  listContent: { padding: Spacing.four, gap: Spacing.four, paddingBottom: Spacing.six },
  salonCard: {
    padding: Spacing.four,
    borderRadius: Radius.xl,
    borderWidth: 1,
    gap: Spacing.three,
    ...Shadows.card,
  },
  salonHeader: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'flex-start' },
  name: { fontSize: 16, fontWeight: '800' },
  ratingBadge: { flexDirection: 'row', alignItems: 'center', gap: Spacing.half, paddingHorizontal: Spacing.two, paddingVertical: Spacing.half, borderRadius: 12, borderWidth: 1 },
  slotContainer: { padding: Spacing.three, borderRadius: 16, gap: Spacing.one },
  inlineIconText: { flexDirection: 'row', alignItems: 'center', gap: Spacing.one },
  metaRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginTop: Spacing.one },
  bookButton: {
    paddingHorizontal: Spacing.four,
    paddingVertical: Spacing.two,
    borderRadius: 16,
    borderWidth: 0,
    alignItems: 'center',
    justifyContent: 'center',
    minWidth: 100,
    minHeight: 44,
  },
  // ── Modal shared ──
  modalOverlay: { flex: 1, justifyContent: 'flex-end', backgroundColor: 'rgba(0,0,0,0.55)' },
  bottomSheet: { borderTopLeftRadius: 28, borderTopRightRadius: 28, padding: Spacing.four, paddingBottom: Spacing.six, maxHeight: '80%' },
  sheetHandle: { width: 40, height: 5, borderRadius: 3, alignSelf: 'center', marginBottom: Spacing.three },
  sheetTitle: { fontSize: 20, fontWeight: '800' },
  slotRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    padding: Spacing.three,
    borderRadius: 16,
    borderWidth: 2,
    marginBottom: Spacing.two,
  },
  priceBadge: { paddingHorizontal: Spacing.three, paddingVertical: Spacing.one, borderRadius: 12 },
  cancelButton: { marginTop: Spacing.two, paddingVertical: Spacing.two, borderRadius: 14, borderWidth: 1, alignItems: 'center' },
  // ── Checkout ──
  checkoutCard: {
    borderRadius: 28,
    padding: Spacing.four,
    width: '92%',
    alignSelf: 'center',
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 8 },
    shadowOpacity: 0.25,
    shadowRadius: 16,
    elevation: 12,
  },
  timerBadge: { borderRadius: 16, padding: Spacing.three, alignItems: 'center', gap: Spacing.one },
  timerText: { fontSize: 28, fontWeight: '900', letterSpacing: 2 },
  summaryBox: { borderRadius: 16, borderWidth: 1, padding: Spacing.three, marginTop: Spacing.three, gap: Spacing.two },
  summaryRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  payButton: { marginTop: Spacing.four, paddingVertical: Spacing.three, borderRadius: 18, alignItems: 'center', minHeight: 54, justifyContent: 'center' },
  successBanner: { alignItems: 'center', paddingVertical: Spacing.four, gap: Spacing.one },
});
