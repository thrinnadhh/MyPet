import { appConfig } from '@/utils/app-config';

export type HistoryAppointmentStatus = 'SLOT_HELD' | 'CONFIRMED' | 'COMPLETED' | 'CANCELLED' | 'NO_SHOW' | 'EXPIRED';

export interface CustomerAppointmentRecord {
  id: string;
  providerName: string;
  providerId: string;
  serviceName: string;
  petName: string;
  slotStartsAt: string;
  status: HistoryAppointmentStatus;
  hasReview: boolean;
}

interface AppointmentDto {
  appointmentId?: string;
  id?: string;
  customerId: string;
  providerId: string;
  offeringId: string;
  slotId: string;
  petId: string;
  status: HistoryAppointmentStatus;
  bookedAt?: string;
}

interface ProviderDto {
  providerId: string;
  name: string;
}

interface OfferingDto {
  offeringId: string;
  name: string;
}

interface SlotDto {
  slotStart?: string;
  startTime?: string;
}

interface ReviewDto {
  id: string;
  targetType: 'APPOINTMENT' | 'ORDER' | 'PROVIDER';
  targetId: string;
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

async function fetchProviderName(providerId: string, accessToken: string | null | undefined): Promise<string> {
  const response = await fetch(`${appConfig.apiBaseUrl}/api/v1/providers/${providerId}`, {
    headers: authHeaders(accessToken),
  });
  if (!response.ok) return `Provider ${providerId.slice(0, 8)}`;

  const provider = (await response.json()) as ProviderDto;
  return provider.name;
}

async function fetchOfferingName(providerId: string, offeringId: string, accessToken: string | null | undefined): Promise<string> {
  const response = await fetch(`${appConfig.apiBaseUrl}/api/v1/catalog/offerings?providerId=${providerId}`, {
    headers: authHeaders(accessToken),
  });
  if (!response.ok) return `Service ${offeringId.slice(0, 8)}`;

  const offerings = (await response.json()) as OfferingDto[];
  return offerings.find((offering) => offering.offeringId === offeringId)?.name ?? `Service ${offeringId.slice(0, 8)}`;
}

async function fetchSlotStart(slotId: string, accessToken: string | null | undefined): Promise<string | null> {
  const response = await fetch(`${appConfig.apiBaseUrl}/api/v1/catalog/slots/${slotId}`, {
    headers: authHeaders(accessToken),
  });
  if (!response.ok) return null;

  const slot = (await response.json()) as SlotDto;
  return slot.slotStart ?? slot.startTime ?? null;
}

export async function fetchCustomerAppointments(
  customerId: string,
  accessToken: string | null | undefined,
): Promise<CustomerAppointmentRecord[]> {
  const [appointmentsResponse, reviewsResponse] = await Promise.all([
    fetch(`${appConfig.apiBaseUrl}/api/v1/appointments/customer/${customerId}`, {
      headers: authHeaders(accessToken),
    }),
    fetch(`${appConfig.apiBaseUrl}/api/v1/reviews/customer/${customerId}`, {
      headers: authHeaders(accessToken),
    }),
  ]);

  const appointments = await readJson<AppointmentDto[]>(appointmentsResponse, 'Could not load appointment history.');
  const reviews = reviewsResponse.ok ? ((await reviewsResponse.json()) as ReviewDto[]) : [];
  const reviewedAppointmentIds = new Set(
    reviews.filter((review) => review.targetType === 'APPOINTMENT').map((review) => review.targetId),
  );

  const enriched = await Promise.all(
    appointments.map(async (appointment): Promise<CustomerAppointmentRecord> => {
      const id = appointment.appointmentId ?? appointment.id;
      if (!id) throw new Error('Appointment response did not include an appointment ID.');
      const [providerName, serviceName, slotStart] = await Promise.all([
        fetchProviderName(appointment.providerId, accessToken),
        fetchOfferingName(appointment.providerId, appointment.offeringId, accessToken),
        fetchSlotStart(appointment.slotId, accessToken),
      ]);

      return {
        id,
        providerName,
        providerId: appointment.providerId,
        serviceName,
        petName: `Pet ${appointment.petId.slice(0, 8)}`,
        slotStartsAt: slotStart ?? appointment.bookedAt ?? new Date().toISOString(),
        status: appointment.status,
        hasReview: reviewedAppointmentIds.has(id),
      };
    }),
  );

  return enriched.sort((left, right) => right.slotStartsAt.localeCompare(left.slotStartsAt));
}

export async function submitAppointmentReview(input: {
  customerId: string;
  providerId: string;
  targetId: string;
  rating: number;
  comment: string;
  accessToken: string | null | undefined;
}): Promise<'created' | 'duplicate'> {
  const response = await fetch(`${appConfig.apiBaseUrl}/api/v1/reviews`, {
    method: 'POST',
    headers: jsonHeaders(input.accessToken),
    body: JSON.stringify({
      customerId: input.customerId,
      providerId: input.providerId,
      targetType: 'APPOINTMENT',
      targetId: input.targetId,
      rating: input.rating,
      comment: input.comment.trim() || null,
    }),
  });

  if (response.status === 409) return 'duplicate';
  await readJson<unknown>(response, 'Could not submit review.');
  return 'created';
}
