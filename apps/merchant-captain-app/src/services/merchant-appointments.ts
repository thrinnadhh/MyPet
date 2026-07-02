import { appConfig } from '@/utils/app-config';

export type MerchantAppointmentStatus = 'SLOT_HELD' | 'CONFIRMED' | 'COMPLETED' | 'CANCELLED' | 'NO_SHOW' | 'EXPIRED';

export interface MerchantProvider {
  providerId: string;
  providerType: string;
  fulfillmentType?: string;
  name: string;
}

export interface MerchantBooking {
  id: string;
  customerName: string;
  petName: string;
  serviceName: string;
  slotStartsAt: string;
  status: MerchantAppointmentStatus;
  providerId: string;
  offeringId: string;
  slotId: string;
  visitNotes?: string | null;
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
  bookedAt?: string;
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

function authHeaders(accessToken: string | null | undefined): Record<string, string> {
  const headers: Record<string, string> = { Accept: 'application/json' };
  if (accessToken) headers.Authorization = `Bearer ${accessToken}`;
  return headers;
}

function jsonHeaders(accessToken: string | null | undefined): Record<string, string> {
  return {
    ...authHeaders(accessToken),
    'Content-Type': 'application/json',
  };
}

async function readJson<T>(response: Response, fallbackMessage: string): Promise<T> {
  if (!response.ok) {
    const body = (await response.json().catch(() => null)) as { error?: string; message?: string } | null;
    throw new Error(body?.error ?? body?.message ?? fallbackMessage);
  }
  return (await response.json()) as T;
}

export async function fetchMerchantProviders(ownerUserId: string, accessToken: string | null | undefined): Promise<MerchantProvider[]> {
  const response = await fetch(`${appConfig.apiBaseUrl}/api/v1/providers?ownerUserId=${ownerUserId}`, {
    headers: authHeaders(accessToken),
  });
  return readJson<MerchantProvider[]>(response, 'Could not load merchant providers.');
}

async function fetchOfferings(providerId: string, accessToken: string | null | undefined): Promise<Map<string, string>> {
  const response = await fetch(`${appConfig.apiBaseUrl}/api/v1/catalog/offerings?providerId=${providerId}`, {
    headers: authHeaders(accessToken),
  });
  if (!response.ok) return new Map();

  const offerings = (await response.json()) as OfferingDto[];
  return new Map(offerings.map((offering) => [offering.offeringId, offering.name]));
}

async function fetchSlotStart(slotId: string, accessToken: string | null | undefined): Promise<string | null> {
  const response = await fetch(`${appConfig.apiBaseUrl}/api/v1/catalog/slots/${slotId}`, {
    headers: authHeaders(accessToken),
  });
  if (!response.ok) return null;

  const slot = (await response.json()) as SlotDto;
  return slot.slotStart ?? slot.startTime ?? null;
}

function compactId(value: string): string {
  return value.length > 8 ? value.slice(0, 8) : value;
}

export async function fetchMerchantBookings(ownerUserId: string, accessToken: string | null | undefined): Promise<MerchantBooking[]> {
  const providers = await fetchMerchantProviders(ownerUserId, accessToken);
  const appointmentProviders = providers.filter((provider) => provider.fulfillmentType === 'APPOINTMENT');
  const visibleProviders = appointmentProviders.length > 0 ? appointmentProviders : providers;

  const providerBookings = await Promise.all(
    visibleProviders.map(async (provider) => {
      const [offerings, appointmentsResponse] = await Promise.all([
        fetchOfferings(provider.providerId, accessToken),
        fetch(`${appConfig.apiBaseUrl}/api/v1/appointments/provider/${provider.providerId}`, {
          headers: authHeaders(accessToken),
        }),
      ]);

      const appointments = await readJson<AppointmentDto[]>(appointmentsResponse, 'Could not load appointments.');
      const enriched = await Promise.all(
        appointments.map(async (appointment): Promise<MerchantBooking> => {
          const id = appointment.appointmentId ?? appointment.id;
          if (!id) throw new Error('Appointment response did not include an appointment ID.');
          const slotStart = await fetchSlotStart(appointment.slotId, accessToken);
          return {
            id,
            customerName: `Customer ${compactId(appointment.customerId)}`,
            petName: `Pet ${compactId(appointment.petId)}`,
            serviceName: offerings.get(appointment.offeringId) ?? provider.name,
            slotStartsAt: slotStart ?? appointment.bookedAt ?? new Date().toISOString(),
            status: appointment.status,
            providerId: appointment.providerId,
            offeringId: appointment.offeringId,
            slotId: appointment.slotId,
            visitNotes: appointment.visitNotes,
          };
        }),
      );
      return enriched;
    }),
  );

  return providerBookings.flat().sort((left, right) => left.slotStartsAt.localeCompare(right.slotStartsAt));
}

export async function completeMerchantBooking(
  bookingId: string,
  notes: string,
  accessToken: string | null | undefined,
): Promise<MerchantBooking | null> {
  const params = new URLSearchParams({ status: 'COMPLETED' });
  const trimmedNotes = notes.trim();
  if (trimmedNotes) params.set('note', trimmedNotes);

  const response = await fetch(`${appConfig.apiBaseUrl}/api/v1/appointments/${bookingId}/status?${params.toString()}`, {
    method: 'PUT',
    headers: jsonHeaders(accessToken),
  });
  const updated = await readJson<AppointmentDto>(response, 'Could not complete appointment.');
  const id = updated.appointmentId ?? updated.id;
  if (!id) return null;

  return {
    id,
    customerName: `Customer ${compactId(updated.customerId)}`,
    petName: `Pet ${compactId(updated.petId)}`,
    serviceName: updated.offeringId,
    slotStartsAt: updated.bookedAt ?? new Date().toISOString(),
    status: updated.status,
    providerId: updated.providerId,
    offeringId: updated.offeringId,
    slotId: updated.slotId,
    visitNotes: updated.visitNotes,
  };
}
