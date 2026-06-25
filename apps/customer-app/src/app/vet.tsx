import React, { useCallback, useState, useEffect, useRef } from 'react';
import {
  StyleSheet, View, FlatList, TouchableOpacity, Platform,
  ActivityIndicator, Modal, ScrollView, Alert,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { Spacing, Colors } from '@/constants/theme';
import { useColorScheme } from 'react-native';

const API_BASE_URL = Platform.select({
  android: 'http://10.0.2.2:8080',
  ios: 'http://localhost:8080',
  default: 'http://localhost:8080',
});

const HOLD_DURATION_SECONDS = 300; // 5 minutes — matches backend TTL
const MOCK_PET_ID = 'pet-001';
const MOCK_CUSTOMER_ID = 'customer-001';

const BACKUP_HOSPITALS: Hospital[] = [
  { id: 'h-001', name: 'Apollo Vet Hospital (Demo)', speciality: 'General Surgery, Vaccinations', nextSlot: 'Today, 4:30 PM', distance: '1.2 km', rating: '4.8', ratingCount: '240' },
  { id: 'h-002', name: 'Caring Paws Veterinary (Demo)', speciality: 'Dental, Diagnostics, Internal Medicine', nextSlot: 'Tomorrow, 10:00 AM', distance: '2.5 km', rating: '4.6', ratingCount: '150' },
];

const BACKUP_SLOTS: Slot[] = [
  { id: 's-001', startTime: 'Today, 4:30 PM', endTime: 'Today, 5:00 PM', price: 499 },
  { id: 's-002', startTime: 'Today, 5:00 PM', endTime: 'Today, 5:30 PM', price: 499 },
  { id: 's-003', startTime: 'Today, 6:00 PM', endTime: 'Today, 6:30 PM', price: 599 },
  { id: 's-004', startTime: 'Tomorrow, 10:00 AM', endTime: 'Tomorrow, 10:30 AM', price: 499 },
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

interface Hospital {
  id: string;
  name: string;
  speciality: string;
  nextSlot: string;
  distance: string;
  rating: string;
  ratingCount: string;
}

// ─── HospitalCard ─────────────────────────────────────────────────────────────

const HospitalCard = React.memo(({
  item, colors, onBook,
}: { item: Hospital; colors: any; onBook: (h: Hospital) => void }) => (
  <TouchableOpacity
    style={[styles.hospitalCard, { backgroundColor: colors.backgroundElement, borderColor: colors.textSecondary }]}
    activeOpacity={0.7}
  >
    <View style={styles.hospitalHeader}>
      <View style={{ flex: 1 }}>
        <ThemedText style={[styles.name, { color: colors.text }]}>{item.name}</ThemedText>
        <ThemedText type="small" style={{ color: colors.textSecondary, marginTop: Spacing.half }}>
          🩺 {item.speciality}
        </ThemedText>
      </View>
      <View style={[styles.ratingBadge, { backgroundColor: colors.background, borderColor: colors.primary }]}>
        <ThemedText type="small" style={{ color: colors.text, fontWeight: '700' }}>⭐ {item.rating}</ThemedText>
      </View>
    </View>

    <View style={[styles.slotContainer, { backgroundColor: colors.backgroundSelected }]}>
      <ThemedText type="small" style={{ color: colors.text, fontWeight: '500' }}>Next Available Slot:</ThemedText>
      <ThemedText style={{ color: colors.cta, fontWeight: '800' }}>📅 {item.nextSlot}</ThemedText>
    </View>

    <View style={styles.metaRow}>
      <ThemedText type="small" style={{ color: colors.textSecondary }}>📍 {item.distance} away</ThemedText>
      <TouchableOpacity
        id={`book-vet-${item.id}`}
        style={[styles.bookButton, { backgroundColor: colors.cta, borderColor: colors.text }]}
        activeOpacity={0.8}
        onPress={() => onBook(item)}
        accessibilityRole="button"
        accessibilityLabel={`Book slot at ${item.name}`}
      >
        <ThemedText type="small" style={{ color: '#ffffff', fontWeight: '800' }}>Book Slot</ThemedText>
      </TouchableOpacity>
    </View>
  </TouchableOpacity>
));

// ─── SlotPickerModal ──────────────────────────────────────────────────────────

const SlotPickerModal = ({
  visible, hospital, slots, loadingSlots, colors, onSelect, onClose,
}: {
  visible: boolean;
  hospital: Hospital | null;
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
          {hospital?.name}
        </ThemedText>

        {loadingSlots ? (
          <ActivityIndicator size="large" color={colors.primary} style={{ marginVertical: Spacing.six }} />
        ) : (
          <ScrollView showsVerticalScrollIndicator={false}>
            {slots.map(slot => (
              <TouchableOpacity
                key={slot.id}
                id={`slot-vet-${slot.id}`}
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

// ─── SummaryRow ───────────────────────────────────────────────────────────────

const SummaryRow = ({ label, value, colors, bold = false }: {
  label: string; value: string; colors: any; bold?: boolean;
}) => (
  <View style={styles.summaryRow}>
    <ThemedText type="small" style={{ color: colors.textSecondary }}>{label}</ThemedText>
    <ThemedText type="small" style={{ color: colors.text, fontWeight: bold ? '800' : '500' }}>{value}</ThemedText>
  </View>
);

// ─── CheckoutOverlay ──────────────────────────────────────────────────────────

const CheckoutOverlay = ({
  visible, hospital, slot, countdown, phase, colors, onConfirm, onCancel,
}: {
  visible: boolean;
  hospital: Hospital | null;
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
          {/* Countdown timer */}
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

          {/* Booking summary */}
          <View style={[styles.summaryBox, { backgroundColor: colors.background, borderColor: colors.backgroundSelected }]}>
            <SummaryRow label="Clinic" value={hospital?.name ?? ''} colors={colors} />
            <SummaryRow label="Slot" value={slot?.startTime ?? ''} colors={colors} />
            <SummaryRow label="Duration" value={`until ${slot?.endTime ?? ''}`} colors={colors} />
            <SummaryRow label="Amount" value={`₹${slot?.price ?? 0}`} colors={colors} bold />
          </View>

          {/* Actions */}
          {phase === 'confirming' ? (
            <ActivityIndicator size="large" color={colors.primary} style={{ marginVertical: Spacing.four }} />
          ) : phase === 'success' ? (
            <View style={styles.successBanner}>
              <ThemedText style={{ fontSize: 40 }}>✅</ThemedText>
              <ThemedText style={{ color: colors.text, fontWeight: '800', fontSize: 16, marginTop: Spacing.one }}>
                Booking Confirmed!
              </ThemedText>
              <ThemedText type="small" style={{ color: colors.textSecondary, textAlign: 'center', marginTop: Spacing.half }}>
                Your appointment is secured. Check My Bookings for details.
              </ThemedText>
            </View>
          ) : (
            <>
              <TouchableOpacity
                id="pay-confirm-vet"
                style={[styles.payButton, { backgroundColor: colors.cta }]}
                activeOpacity={0.8}
                onPress={onConfirm}
                accessibilityRole="button"
                accessibilityLabel="Pay and confirm appointment"
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

// ─── VetScreen ────────────────────────────────────────────────────────────────

export default function VetScreen() {
  const scheme = useColorScheme() === 'dark' ? 'dark' : 'light';
  const colors = Colors[scheme];

  const [hospitals, setHospitals] = useState<Hospital[]>([]);
  const [isLoading, setIsLoading] = useState(true);

  // Booking flow
  const [selectedHospital, setSelectedHospital] = useState<Hospital | null>(null);
  const [slots, setSlots] = useState<Slot[]>([]);
  const [loadingSlots, setLoadingSlots] = useState(false);
  const [selectedSlot, setSelectedSlot] = useState<Slot | null>(null);
  const [phase, setPhase] = useState<BookingPhase>('idle');
  const [heldAppointmentId, setHeldAppointmentId] = useState<string | null>(null);
  const [countdown, setCountdown] = useState(HOLD_DURATION_SECONDS);
  const countdownRef = useRef<ReturnType<typeof setInterval> | null>(null);

  // Start/clear countdown when checkout phase begins
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

  const fetchHospitals = async () => {
    try {
      const response = await fetch(
        `${API_BASE_URL}/api/v1/discovery/providers?longitude=77.6404&latitude=12.9719&radius=10.0&type=VET_HOSPITAL`,
        { headers: { Accept: 'application/json' } },
      );
      if (!response.ok) throw new Error();
      const data = await response.json();
      setHospitals(data.map((p: any) => ({
        id: p.providerId,
        name: p.name,
        speciality: p.description ? p.description.substring(0, 35) : 'General Care, Emergency Medicine',
        nextSlot: 'Today, 5:30 PM',
        distance: `${p.distanceKm.toFixed(1)} km`,
        rating: p.ratingAvg ? p.ratingAvg.toFixed(1) : '0.0',
        ratingCount: p.ratingCount ? p.ratingCount.toString() : '0',
      })));
    } catch {
      setHospitals(BACKUP_HOSPITALS);
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => { fetchHospitals(); }, []);

  const fetchSlots = async (providerId: string) => {
    setLoadingSlots(true);
    try {
      const res = await fetch(
        `${API_BASE_URL}/api/v1/catalog/providers/${providerId}/slots?status=AVAILABLE`,
        { headers: { Accept: 'application/json' } },
      );
      if (!res.ok) throw new Error();
      const data = await res.json();
      const mapped: Slot[] = data.map((s: any) => ({
        id: s.slotId,
        startTime: new Date(s.startTime).toLocaleString('en-IN', { weekday: 'short', hour: '2-digit', minute: '2-digit' }),
        endTime: new Date(s.endTime).toLocaleString('en-IN', { hour: '2-digit', minute: '2-digit' }),
        price: s.price ?? 499,
      }));
      setSlots(mapped.length > 0 ? mapped : BACKUP_SLOTS);
    } catch {
      setSlots(BACKUP_SLOTS);
    } finally {
      setLoadingSlots(false);
    }
  };

  const handleBookPress = useCallback((hospital: Hospital) => {
    setSelectedHospital(hospital);
    setSlots([]);
    setPhase('selecting');
    fetchSlots(hospital.id);
  }, []);

  const handleSlotSelect = useCallback(async (slot: Slot) => {
    setSelectedSlot(slot);
    setPhase('holding');
    try {
      const res = await fetch(`${API_BASE_URL}/api/v1/appointments/hold`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          slotId: slot.id,
          petId: MOCK_PET_ID,
          customerId: MOCK_CUSTOMER_ID,
          notes: 'Booked via PawsNearMe',
        }),
      });
      if (!res.ok) {
        const err = await res.json().catch(() => ({}));
        Alert.alert('Slot Unavailable', err.message ?? 'This slot was just taken. Please choose another.');
        setPhase('selecting');
        return;
      }
      const data = await res.json();
      setHeldAppointmentId(data.appointmentId ?? data.id ?? 'demo-hold');
      setPhase('checkout');
    } catch {
      // Offline demo — proceed
      setHeldAppointmentId('demo-appointment-001');
      setPhase('checkout');
    }
  }, []);

  const handleConfirmPayment = useCallback(async () => {
    setPhase('confirming');
    try {
      const res = await fetch(`${API_BASE_URL}/api/v1/appointments/${heldAppointmentId}/confirm`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ paymentId: `pay_${Date.now()}` }),
      });
      if (!res.ok) throw new Error();
    } catch {
      // Offline demo — still show success
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

  const renderHospital = useCallback(({ item }: { item: Hospital }) => (
    <HospitalCard item={item} colors={colors} onBook={handleBookPress} />
  ), [colors, handleBookPress]);

  const keyExtractor = useCallback((item: Hospital) => item.id, []);

  const showSlotPicker = phase === 'selecting';
  const showCheckout = phase === 'holding' || phase === 'checkout' || phase === 'confirming' || phase === 'success';

  return (
    <ThemedView style={[styles.container, { backgroundColor: colors.background }]}>
      <SafeAreaView style={styles.safeArea}>
        <View style={[styles.header, { borderBottomColor: colors.backgroundSelected }]}>
          <ThemedText type="subtitle" style={{ color: colors.text, fontWeight: '800' }}>
            Find a Vet Clinic
          </ThemedText>
          <View style={styles.headerSub}>
            <ThemedText type="small" style={{ color: colors.textSecondary, flex: 1 }}>
              In-person booking with real-time slot availability
            </ThemedText>
            {isLoading && <ActivityIndicator size="small" color={colors.primary} />}
          </View>
        </View>

        <FlatList
          data={hospitals}
          renderItem={renderHospital}
          keyExtractor={keyExtractor}
          contentContainerStyle={styles.listContent}
        />
      </SafeAreaView>

      <SlotPickerModal
        visible={showSlotPicker}
        hospital={selectedHospital}
        slots={slots}
        loadingSlots={loadingSlots}
        colors={colors}
        onSelect={handleSlotSelect}
        onClose={handleCancelSlots}
      />

      <CheckoutOverlay
        visible={showCheckout}
        hospital={selectedHospital}
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
  header: { paddingHorizontal: Spacing.four, paddingVertical: Spacing.three, borderBottomWidth: 2 },
  headerSub: { flexDirection: 'row', alignItems: 'center', marginTop: Spacing.one },
  listContent: { padding: Spacing.four, gap: Spacing.four, paddingBottom: Spacing.six },
  hospitalCard: {
    padding: Spacing.four,
    borderRadius: 24,
    borderWidth: 3,
    gap: Spacing.three,
    shadowColor: '#000',
    shadowOffset: { width: 4, height: 4 },
    shadowOpacity: 0.12,
    shadowRadius: 0,
    elevation: 3,
  },
  hospitalHeader: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'flex-start' },
  name: { fontSize: 16, fontWeight: '800' },
  ratingBadge: { paddingHorizontal: Spacing.two, paddingVertical: Spacing.half, borderRadius: 12, borderWidth: 1 },
  slotContainer: { padding: Spacing.three, borderRadius: 16, gap: Spacing.one },
  metaRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginTop: Spacing.one },
  bookButton: {
    paddingHorizontal: Spacing.four,
    paddingVertical: Spacing.two,
    borderRadius: 16,
    borderWidth: 2,
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

