import { useCallback, useState } from 'react';

export interface BookingState {
  providerId: string;
  providerName: string;
  providerType: 'VET_HOSPITAL' | 'GROOMING_CENTER';
  serviceName: string;
  serviceFee: number;
  selectedDate: string; // YYYY-MM-DD
  selectedTimeSlot: string; // e.g. "10:30 AM"
  selectedPetId: string;
  selectedPetName: string;
  notes?: string;
  status: 'idle' | 'locking' | 'locked' | 'submitting' | 'confirmed' | 'error';
  bookingId?: string;
  errorMessage?: string;
}

export function useAppointmentBooking() {
  const [modalVisible, setModalVisible] = useState(false);
  const [booking, setBooking] = useState<BookingState>({
    providerId: '',
    providerName: '',
    providerType: 'VET_HOSPITAL',
    serviceName: 'General Consultation',
    serviceFee: 499,
    selectedDate: new Date().toISOString().split('T')[0],
    selectedTimeSlot: '10:30 AM',
    selectedPetId: 'pet-bruno',
    selectedPetName: 'Bruno (Golden Retriever)',
    status: 'idle',
  });

  const openBookingModal = useCallback((config: {
    providerId: string;
    providerName: string;
    providerType: 'VET_HOSPITAL' | 'GROOMING_CENTER';
    serviceName?: string;
    serviceFee?: number;
  }) => {
    setBooking({
      providerId: config.providerId,
      providerName: config.providerName,
      providerType: config.providerType,
      serviceName: config.serviceName ?? (config.providerType === 'VET_HOSPITAL' ? 'General OPD Consultation' : 'Full Spa Grooming'),
      serviceFee: config.serviceFee ?? (config.providerType === 'VET_HOSPITAL' ? 499 : 1299),
      selectedDate: new Date().toISOString().split('T')[0],
      selectedTimeSlot: '10:30 AM',
      selectedPetId: 'pet-bruno',
      selectedPetName: 'Bruno (Golden Retriever)',
      status: 'idle',
    });
    setModalVisible(true);
  }, []);

  const closeBookingModal = useCallback(() => {
    setModalVisible(false);
  }, []);

  const selectDate = useCallback((date: string) => {
    setBooking((prev) => ({ ...prev, selectedDate: date }));
  }, []);

  const selectSlot = useCallback((slot: string) => {
    setBooking((prev) => ({ ...prev, selectedTimeSlot: slot, status: 'locked' }));
  }, []);

  const selectPet = useCallback((petId: string, petName: string) => {
    setBooking((prev) => ({ ...prev, selectedPetId: petId, selectedPetName: petName }));
  }, []);

  const submitBooking = useCallback(async () => {
    setBooking((prev) => ({ ...prev, status: 'submitting' }));
    try {
      // Simulate API call to appointment-service
      await new Promise((resolve) => setTimeout(resolve, 800));
      const newBookingId = `APT-${Math.floor(100000 + Math.random() * 900000)}`;
      setBooking((prev) => ({ ...prev, status: 'confirmed', bookingId: newBookingId }));
      return true;
    } catch (err) {
      setBooking((prev) => ({ ...prev, status: 'error', errorMessage: 'Slot no longer available' }));
      return false;
    }
  }, []);

  return {
    modalVisible,
    booking,
    openBookingModal,
    closeBookingModal,
    selectDate,
    selectSlot,
    selectPet,
    submitBooking,
  };
}
