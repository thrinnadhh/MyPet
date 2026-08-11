import { apiClient } from './api-client';

export type MerchantAppointmentStatus =
  | 'SLOT_HELD'
  | 'PAID'
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
  status: MerchantAppointmentStatus;
  providerId: string;
  providerType: string;
  offeringId: string;
  slotId: string;
  priceAmount: number;
  payAtClinic: boolean;
  bookedAt: string;
  completedAt?: string | null;
  cancelledAt?: string | null;
  cancellationReason?: string | null;
  visitNotes?: string | null;
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

interface AppointmentDto {
  appointmentId?: string;
  id?: string;
  customerId: string;
  providerId: string;
  offeringId: string;
  slotId: string;
  petId: string;
  status: MerchantAppointmentStatus;
  priceAmount: number | string;
  payAtClinic?: boolean;
  bookedAt?: string;
  completedAt?: string | null;
  cancelledAt?: string | null;
  cancellationReason?: string | null;
  visitNotes?: string | null;
}

interface OfferingDto {
  offeringId: string;
  name: string;
}

interface SlotDto {
  slotStart?: string;
  startTime?: string;
}

function compactId(value: string): string {
  return value.length > 8 ? value.slice(0, 8) : value;
}

export async function fetchMerchantOwnedProviders(): Promise<MerchantProvider[]> {
  return apiClient.get<MerchantProvider[]>('/api/v1/providers/me');
}

export async function fetchMerchantProviders(_ownerUserId?: string): Promise<MerchantProvider[]> {
  const providers = await fetchMerchantOwnedProviders();
  return providers.filter((provider) => provider.fulfillmentType === 'APPOINTMENT');
}

async function fetchOfferings(providerId: string): Promise<Map<string, string>> {
  try {
    const offerings = await apiClient.get<OfferingDto[]>(
      `/api/v1/catalog/offerings?providerId=${encodeURIComponent(providerId)}`,
    );
    return new Map(offerings.map((offering) => [offering.offeringId, offering.name]));
  } catch {
    return new Map();
  }
}

async function fetchSlotStart(slotId: string): Promise<string | null> {
  try {
    const slot = await apiClient.get<SlotDto>(`/api/v1/catalog/slots/${encodeURIComponent(slotId)}`);
    return slot.slotStart ?? slot.startTime ?? null;
  } catch {
    return null;
  }
}

async function enrichAppointment(
  appointment: AppointmentDto,
  provider: MerchantProvider,
  offerings: Map<string, string>,
): Promise<MerchantBooking> {
  const id = appointment.appointmentId ?? appointment.id;
  if (!id) throw new Error('Appointment response did not include an appointment ID.');
  const slotStart = await fetchSlotStart(appointment.slotId);
  return {
    id,
    customerId: appointment.customerId,
    customerName: `Customer ${compactId(appointment.customerId)}`,
    petName: `Pet ${compactId(appointment.petId)}`,
    serviceName: offerings.get(appointment.offeringId) ?? provider.name,
    slotStartsAt: slotStart ?? appointment.bookedAt ?? new Date().toISOString(),
    status: appointment.status,
    providerId: appointment.providerId,
    providerType: provider.providerType,
    offeringId: appointment.offeringId,
    slotId: appointment.slotId,
    priceAmount: Number(appointment.priceAmount) || 0,
    payAtClinic: Boolean(appointment.payAtClinic),
    bookedAt: appointment.bookedAt ?? new Date().toISOString(),
    completedAt: appointment.completedAt,
    cancelledAt: appointment.cancelledAt,
    cancellationReason: appointment.cancellationReason,
    visitNotes: appointment.visitNotes,
  };
}

export async function fetchMerchantBookings(
  _ownerUserId?: string,
  _accessToken?: string | null,
): Promise<MerchantBooking[]> {
  const providers = await fetchMerchantProviders();
  const providerBookings = await Promise.all(
    providers.map(async (provider) => {
      const [offerings, appointments] = await Promise.all([
        fetchOfferings(provider.providerId),
        apiClient.get<AppointmentDto[]>(
          `/api/v1/appointments/provider/${encodeURIComponent(provider.providerId)}`,
        ),
      ]);
      return Promise.all(
        appointments.map((appointment) => enrichAppointment(appointment, provider, offerings)),
      );
    }),
  );
  return providerBookings.flat().sort((left, right) => left.slotStartsAt.localeCompare(right.slotStartsAt));
}

export async function updateMerchantBookingStatus(
  bookingId: string,
  status: MerchantAppointmentAction,
  note: string,
): Promise<AppointmentDto> {
  const params = new URLSearchParams({ status });
  const trimmedNote = note.trim();
  if (trimmedNote) params.set('note', trimmedNote);
  return apiClient.put<AppointmentDto>(
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
