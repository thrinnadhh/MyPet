export type AppointmentStatus = 'UPCOMING' | 'COMPLETED' | 'CANCELLED' | 'RESCHEDULED';

export type AppointmentTab = 'upcoming' | 'past' | 'cancelled';

export interface Appointment {
  id: string;
  appointmentNumber: string;
  providerId: string;
  providerName: string;
  providerType: 'VET_HOSPITAL' | 'GROOMING_CENTER';
  providerLogoUrl: string;
  serviceName: string;
  petId: string;
  petName: string;
  petSpecies: string;
  petBreed: string;
  petAvatarUrl: string;
  date: string;
  timeSlot: string;
  status: AppointmentStatus;
  statusText: string;
  tab: AppointmentTab;
  clinicAddress: string;
  clinicPhone: string;
  doctorOrGroomerName: string;
  totalPrice: number;
  paymentStatus: 'PAID' | 'PAY_AT_CLINIC';
  cancellationPolicy: string;
  canCancel: boolean;
  canReschedule: boolean;
  invoiceUrl?: string;
}

export const MOCK_APPOINTMENTS: Appointment[] = [
  {
    id: 'apt-501',
    appointmentNumber: '#APT-501',
    providerId: 'city-pet-hospital',
    providerName: 'City Pet Multispecialty Hospital',
    providerType: 'VET_HOSPITAL',
    providerLogoUrl: 'https://images.unsplash.com/photo-1629909613654-28e377c37b09?w=150',
    serviceName: 'General OPD Health Checkup & Vaccination',
    petId: 'pet-bruno',
    petName: 'Bruno',
    petSpecies: 'Dog',
    petBreed: 'Golden Retriever',
    petAvatarUrl: 'https://images.unsplash.com/photo-1552053831-71594a27632d?w=150',
    date: 'Tomorrow, Aug 1',
    timeSlot: '10:30 AM - 11:00 AM',
    status: 'UPCOMING',
    statusText: 'Confirmed for Tomorrow',
    tab: 'upcoming',
    clinicAddress: '12-3 Air Bypass Road, Opp. Reliance Mart, Tirupati',
    clinicPhone: '+91 877 225 9900',
    doctorOrGroomerName: 'Dr. K. Srinivas (Chief Vet Surgeon)',
    totalPrice: 499,
    paymentStatus: 'PAID',
    cancellationPolicy: 'Free cancellation up to 2 hours before slot',
    canCancel: true,
    canReschedule: true,
  },
  {
    id: 'apt-402',
    appointmentNumber: '#APT-402',
    providerId: 'paws-bubbles-spa',
    providerName: 'Paws & Bubbles Pet Grooming Spa',
    providerType: 'GROOMING_CENTER',
    providerLogoUrl: 'https://images.unsplash.com/photo-1516734212186-a967f81ad0d7?w=150',
    serviceName: 'Full Royal Spa & De-shedding Bath',
    petId: 'pet-coco',
    petName: 'Coco',
    petSpecies: 'Cat',
    petBreed: 'Persian Longhair',
    petAvatarUrl: 'https://images.unsplash.com/photo-1514888286974-6c03e2ca1dba?w=150',
    date: '3 Days Later, Aug 3',
    timeSlot: '02:00 PM - 03:00 PM',
    status: 'UPCOMING',
    statusText: 'Slot Reserved',
    tab: 'upcoming',
    clinicAddress: '45-B Prakasam Road, Near RTC Bus Stand, Tirupati',
    clinicPhone: '+91 877 228 1122',
    doctorOrGroomerName: 'Anitha R. (Senior Pet Stylist)',
    totalPrice: 1299,
    paymentStatus: 'PAY_AT_CLINIC',
    cancellationPolicy: 'Free cancellation up to 2 hours before slot',
    canCancel: true,
    canReschedule: true,
  },
  {
    id: 'apt-301',
    appointmentNumber: '#APT-301',
    providerId: 'petcare-wellness',
    providerName: 'PetCare Wellness & Diagnostics Center',
    providerType: 'VET_HOSPITAL',
    providerLogoUrl: 'https://images.unsplash.com/photo-1576201836106-db1758fd1c97?w=150',
    serviceName: 'Annual CBC Blood Test & Microchipping',
    petId: 'pet-bruno',
    petName: 'Bruno',
    petSpecies: 'Dog',
    petBreed: 'Golden Retriever',
    petAvatarUrl: 'https://images.unsplash.com/photo-1552053831-71594a27632d?w=150',
    date: 'Jul 20, 2026',
    timeSlot: '11:00 AM',
    status: 'COMPLETED',
    statusText: 'Completed',
    tab: 'past',
    clinicAddress: '88 Tilak Road, Tirupati',
    clinicPhone: '+91 877 224 4455',
    doctorOrGroomerName: 'Dr. Priya Mohan',
    totalPrice: 850,
    paymentStatus: 'PAID',
    cancellationPolicy: 'Non-refundable after consultation',
    canCancel: false,
    canReschedule: false,
    invoiceUrl: 'https://mypet.app/invoices/apt-301.pdf',
  },
  {
    id: 'apt-208',
    appointmentNumber: '#APT-208',
    providerId: 'paws-bubbles-spa',
    providerName: 'Paws & Bubbles Pet Grooming Spa',
    providerType: 'GROOMING_CENTER',
    providerLogoUrl: 'https://images.unsplash.com/photo-1516734212186-a967f81ad0d7?w=150',
    serviceName: 'Nail Clipping & Ear Cleaning',
    petId: 'pet-bruno',
    petName: 'Bruno',
    petSpecies: 'Dog',
    petBreed: 'Golden Retriever',
    petAvatarUrl: 'https://images.unsplash.com/photo-1552053831-71594a27632d?w=150',
    date: 'Jul 10, 2026',
    timeSlot: '04:30 PM',
    status: 'CANCELLED',
    statusText: 'Cancelled by Customer',
    tab: 'cancelled',
    clinicAddress: '45-B Prakasam Road, Tirupati',
    clinicPhone: '+91 877 228 1122',
    doctorOrGroomerName: 'Suresh Kumar',
    totalPrice: 299,
    paymentStatus: 'PAY_AT_CLINIC',
    cancellationPolicy: 'Cancelled prior to 2h window',
    canCancel: false,
    canReschedule: false,
  },
];

export async function fetchAppointmentsData(tab: AppointmentTab = 'upcoming'): Promise<Appointment[]> {
  await new Promise((resolve) => setTimeout(resolve, 150));
  return MOCK_APPOINTMENTS.filter((apt) => apt.tab === tab);
}

export async function fetchAppointmentByIdData(aptId: string): Promise<Appointment | null> {
  await new Promise((resolve) => setTimeout(resolve, 100));
  return MOCK_APPOINTMENTS.find((a) => a.id === aptId || a.appointmentNumber.toLowerCase() === aptId.toLowerCase()) || null;
}
