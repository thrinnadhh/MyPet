import { apiClient } from './api-client';

export type MerchantAppointmentStatus =
  | 'SLOT_HELD'
  | 'CONFIRMED'
  | 'COMPLETED'
  | 'CANCELLED'
  | 'NO_SHOW'
  | 'EXPIRED';

export type MerchantAppointmentAction = 'COMPLETED' | 'CANCELLED' | 'NO_SHOW';

export interface MerchantProvider {
  providerId: string;
  providerType: string;
  fulfillmentType?: string;
  name: string;
}

export interface MerchantBooking {
  id: string;
  customerId: string;
  customerName: string;
  petName: string;
  serviceName: string;
  slotStartsAt: string;
  slotEndsAt?: string | null;
  status: MerchantAppointmentStatus;
  providerId: string;
  providerType: string;
  offeringId: string;
  slotId: string;
  priceAmount: number;
  paymentId?: string | null;
  payAtClinic: boolean;
  bookedAt: string;
  completedAt?: string | null;
  cancelledAt?: string | null;
  cancellationReason?: string | null;
  visitNotes?: string | null;
  prescriptionDocUrl?: string | null;
  identityResolved: boolean;
}

export interface MerchantAppointmentHistoryEntry {
  historyId: string;
  appointmentId: string;
  fromStatus: MerchantAppointmentStatus | null;
  toStatus: MerchantAppointmentStatus;
  changedAt: string;
  changedByUserId?: string | null;
  note?: string | null;
}

export interface MerchantAppointmentInvoice {
  invoiceId: string;
  appointmentId: string;
  invoiceNumber: string;
  subtotalAmount: number;
  taxAmount: number;
  totalAmount: number;
  generatedAt: string;
}

interface MerchantAppointmentDto {
  appointmentId: string;
  customerId: string;
  customerName?: string | null;
  providerId: string;
  offeringId: string;
  offeringName?: string | null;
  slotId: string;
  slotStartsAt?: string | null;
  slotEndsAt?: string | null;
  petId: string;
  petName?: string | null;
  status: MerchantAppointmentStatus;
  priceAmount: number | string;
  paymentId?: string | null;
  payAtClinic?: boolean;
  bookedAt: string;
  completedAt?: string | null;
  cancelledAt?: string | null;
  cancellationReason?: string | null;
  visitNotes?: string | null;
  prescriptionDocUrl?: string | null;
}

export async function fetchMerchantOwnedProviders(): Promise<MerchantProvider[]> {
  return apiClient.get<MerchantProvider[]>('/api/v1/providers/me');
}

export async function fetchMerchantProviders(_ownerUserId?: string): Promise<MerchantProvider[]> {
  const providers = await fetchMerchantOwnedProviders();
  return providers.filter((provider) => provider.fulfillmentType === 'APPOINTMENT');
}

function toBooking(
  appointment: MerchantAppointmentDto,
  provider: MerchantProvider,
): MerchantBooking {
  const customerName = appointment.customerName?.trim() || 'Customer identity unavailable';
  const petName = appointment.petName?.trim() || 'Pet identity unavailable';
  const slotStartsAt = appointment.slotStartsAt ?? appointment.bookedAt;
  return {
    id: appointment.appointmentId,
    customerId: appointment.customerId,
    customerName,
    petName,
    serviceName: appointment.offeringName?.trim() || 'Service details unavailable',
    slotStartsAt,
    slotEndsAt: appointment.slotEndsAt,
    status: appointment.status,
    providerId: appointment.providerId,
    providerType: provider.providerType,
    offeringId: appointment.offeringId,
    slotId: appointment.slotId,
    priceAmount: Number(appointment.priceAmount) || 0,
    paymentId: appointment.paymentId,
    payAtClinic: Boolean(appointment.payAtClinic),
    bookedAt: appointment.bookedAt,
    completedAt: appointment.completedAt,
    cancelledAt: appointment.cancelledAt,
    cancellationReason: appointment.cancellationReason,
    visitNotes: appointment.visitNotes,
    prescriptionDocUrl: appointment.prescriptionDocUrl,
    identityResolved: Boolean(appointment.customerName?.trim() && appointment.petName?.trim()),
  };
}

export async function fetchMerchantBookings(
  _ownerUserId?: string,
  _accessToken?: string | null,
): Promise<MerchantBooking[]> {
  const providers = await fetchMerchantProviders();
  const providerBookings = await Promise.all(
    providers.map(async (provider) => {
      const appointments = await apiClient.get<MerchantAppointmentDto[]>(
        `/api/v1/appointments/provider/${encodeURIComponent(provider.providerId)}`,
      );
      return appointments.map((appointment) => toBooking(appointment, provider));
    }),
  );
  return providerBookings.flat().sort((left, right) => left.slotStartsAt.localeCompare(right.slotStartsAt));
}

export async function updateMerchantBookingStatus(
  bookingId: string,
  status: MerchantAppointmentAction,
  note: string,
): Promise<void> {
  const params = new URLSearchParams({ status });
  const trimmedNote = note.trim();
  if (trimmedNote) params.set('note', trimmedNote);
  await apiClient.put<unknown>(
    `/api/v1/appointments/${encodeURIComponent(bookingId)}/status?${params.toString()}`,
  );
}

export async function completeMerchantBooking(
  bookingId: string,
  notes: string,
  _accessToken?: string | null,
): Promise<MerchantBooking | null> {
  await updateMerchantBookingStatus(bookingId, 'COMPLETED', notes);
  return null;
}

export async function fetchMerchantAppointmentHistory(
  bookingId: string,
): Promise<MerchantAppointmentHistoryEntry[]> {
  return apiClient.get<MerchantAppointmentHistoryEntry[]>(
    `/api/v1/appointments/${encodeURIComponent(bookingId)}/history`,
  );
}

export async function fetchMerchantAppointmentInvoice(
  bookingId: string,
): Promise<MerchantAppointmentInvoice> {
  return apiClient.get<MerchantAppointmentInvoice>(
    `/api/v1/appointments/${encodeURIComponent(bookingId)}/invoice`,
  );
}
