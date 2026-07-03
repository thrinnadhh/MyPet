import { appConfig } from '@/utils/app-config';

const DEMO_USER_ID = 'd3b07384-d113-4e4e-9c8e-3d8e3d8e3d8e';

export interface AppointmentSlotOption {
  id: string;
  providerId: string;
  offeringId: string;
  serviceName: string;
  startTime: string;
  endTime: string;
  price: number;
}

interface CatalogOffering {
  offeringId: string;
  providerId: string;
  name: string;
  price: number | string;
  status?: string;
}

interface CatalogSlot {
  slotId: string;
  offeringId: string;
  slotStart?: string;
  slotEnd?: string;
  startTime?: string;
  endTime?: string;
  status?: string;
}

interface AppointmentResponse {
  appointmentId?: string;
  id?: string;
}

interface HoldAppointmentInput {
  slot: AppointmentSlotOption;
  userId: string | null | undefined;
  accessToken: string | null | undefined;
}

function authHeaders(accessToken: string | null | undefined): Record<string, string> {
  const headers: Record<string, string> = { Accept: 'application/json' };
  if (accessToken) {
    headers.Authorization = `Bearer ${accessToken}`;
  }
  return headers;
}

function jsonHeaders(accessToken: string | null | undefined): Record<string, string> {
  return {
    ...authHeaders(accessToken),
    'Content-Type': 'application/json',
  };
}

function resolveBookingUserId(userId: string | null | undefined): string {
  if (userId) return userId;
  if (appConfig.allowDemoMode) return DEMO_USER_ID;
  throw new Error('Please sign in before booking an appointment.');
}

function toPrice(value: number | string): number {
  return typeof value === 'number' ? value : Number.parseFloat(value);
}

function formatSlotTime(value: string | undefined, options: Intl.DateTimeFormatOptions): string {
  if (!value) return 'Slot time unavailable';
  return new Date(value).toLocaleString('en-IN', options);
}

export async function fetchAvailableAppointmentSlots(providerId: string): Promise<AppointmentSlotOption[]> {
  const offeringsResponse = await fetch(`${appConfig.apiBaseUrl}/api/v1/catalog/offerings?providerId=${providerId}`, {
    headers: authHeaders(undefined),
  });
  if (!offeringsResponse.ok) {
    throw new Error('Could not load appointment services.');
  }

  const offerings = ((await offeringsResponse.json()) as CatalogOffering[]).filter(
    (offering) => !offering.status || offering.status === 'ACTIVE',
  );

  const slotGroups = await Promise.all(
    offerings.map(async (offering) => {
      const slotsResponse = await fetch(`${appConfig.apiBaseUrl}/api/v1/catalog/slots?offeringId=${offering.offeringId}`, {
        headers: authHeaders(undefined),
      });
      if (!slotsResponse.ok) return [];

      const slots = (await slotsResponse.json()) as CatalogSlot[];
      return slots
        .filter((slot) => !slot.status || slot.status === 'AVAILABLE')
        .map((slot): AppointmentSlotOption => {
          const slotStart = slot.slotStart ?? slot.startTime;
          const slotEnd = slot.slotEnd ?? slot.endTime;
          return {
            id: slot.slotId,
            providerId: offering.providerId,
            offeringId: offering.offeringId,
            serviceName: offering.name,
            startTime: formatSlotTime(slotStart, { weekday: 'short', hour: '2-digit', minute: '2-digit' }),
            endTime: formatSlotTime(slotEnd, { hour: '2-digit', minute: '2-digit' }),
            price: toPrice(offering.price),
          };
        });
    }),
  );

  return slotGroups.flat();
}

export async function holdAppointmentSlot(input: HoldAppointmentInput): Promise<string> {
  const bookingUserId = resolveBookingUserId(input.userId);
  const response = await fetch(`${appConfig.apiBaseUrl}/api/v1/appointments/hold`, {
    method: 'POST',
    headers: jsonHeaders(input.accessToken),
    body: JSON.stringify({
      customerId: bookingUserId,
      providerId: input.slot.providerId,
      offeringId: input.slot.offeringId,
      slotId: input.slot.id,
      petId: bookingUserId,
      priceAmount: input.slot.price,
      payAtClinic: true,
    }),
  });

  if (!response.ok) {
    const errorBody = (await response.json().catch(() => null)) as { error?: string; message?: string } | null;
    throw new Error(errorBody?.error ?? errorBody?.message ?? 'This slot was just taken. Please choose another.');
  }

  const data = (await response.json()) as AppointmentResponse;
  const appointmentId = data.appointmentId ?? data.id;
  if (!appointmentId) {
    throw new Error('Appointment hold succeeded but no appointment ID was returned.');
  }
  return appointmentId;
}

export async function confirmAppointmentHold(appointmentId: string, accessToken: string | null | undefined): Promise<void> {
  const response = await fetch(`${appConfig.apiBaseUrl}/api/v1/appointments/${appointmentId}/confirm`, {
    method: 'POST',
    headers: authHeaders(accessToken),
  });

  if (!response.ok) {
    const errorBody = (await response.json().catch(() => null)) as { error?: string; message?: string } | null;
    throw new Error(errorBody?.error ?? errorBody?.message ?? 'The appointment was not confirmed. Please retry.');
  }
}
