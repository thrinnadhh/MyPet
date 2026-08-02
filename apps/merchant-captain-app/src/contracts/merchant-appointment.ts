import type {
  MerchantAppointmentAction,
  MerchantAppointmentStatus,
} from '../services/merchant-appointments';

export type MerchantAppointmentQueue =
  | 'TODAY'
  | 'UPCOMING'
  | 'COMPLETED'
  | 'NO_SHOW'
  | 'CANCELLED';

export function appointmentActions(status: MerchantAppointmentStatus): MerchantAppointmentAction[] {
  return status === 'CONFIRMED' ? ['COMPLETED', 'NO_SHOW', 'CANCELLED'] : [];
}

export function appointmentQueue(
  status: MerchantAppointmentStatus,
  slotStartsAt: string,
  now = new Date(),
): MerchantAppointmentQueue {
  if (status === 'COMPLETED') return 'COMPLETED';
  if (status === 'NO_SHOW') return 'NO_SHOW';
  if (status === 'CANCELLED' || status === 'EXPIRED') return 'CANCELLED';

  const slot = new Date(slotStartsAt);
  if (!Number.isNaN(slot.getTime()) && slot.toDateString() === now.toDateString()) return 'TODAY';
  return 'UPCOMING';
}

export function appointmentMatchesSearch(
  search: string,
  values: Array<string | null | undefined>,
): boolean {
  const normalized = search.trim().toLowerCase();
  if (!normalized) return true;
  return values.some((value) => value?.toLowerCase().includes(normalized));
}
