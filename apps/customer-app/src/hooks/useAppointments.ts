import { useCallback, useEffect, useState } from 'react';
import { fetchAppointmentsData, type Appointment, type AppointmentTab } from '@/services/appointments-data';

export function useAppointments(initialTab: AppointmentTab = 'upcoming') {
  const [tab, setTab] = useState<AppointmentTab>(initialTab);
  const [appointments, setAppointments] = useState<Appointment[]>([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);

  const loadAppointments = useCallback(async () => {
    setLoading(true);
    try {
      const data = await fetchAppointmentsData(tab);
      setAppointments(data);
    } finally {
      setLoading(false);
    }
  }, [tab]);

  useEffect(() => {
    void loadAppointments();
  }, [loadAppointments]);

  const onRefresh = async () => {
    setRefreshing(true);
    try {
      const data = await fetchAppointmentsData(tab);
      setAppointments(data);
    } finally {
      setRefreshing(false);
    }
  };

  const cancelAppointment = (aptId: string) => {
    setAppointments((prev) =>
      prev.map((a) => (a.id === aptId ? { ...a, status: 'CANCELLED', statusText: 'Cancelled', tab: 'cancelled', canCancel: false, canReschedule: false } : a))
    );
  };

  const rescheduleAppointment = (aptId: string, newDate: string, newSlot: string) => {
    setAppointments((prev) =>
      prev.map((a) =>
        a.id === aptId
          ? { ...a, status: 'RESCHEDULED', statusText: `Rescheduled to ${newDate}`, date: newDate, timeSlot: newSlot }
          : a
      )
    );
  };

  return {
    tab,
    setTab,
    appointments,
    loading,
    refreshing,
    onRefresh,
    cancelAppointment,
    rescheduleAppointment,
  };
}
