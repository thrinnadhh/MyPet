import { fetchAppointmentsData, MOCK_APPOINTMENTS } from '../services/appointments-data';
import { fetchOrdersData, MOCK_ORDERS, TRACKING_STEPS } from '../services/orders-data';

describe('Sprint S14 Orders & Appointments Business Rules', () => {
  test('fetchOrdersData filters by tab correctly', async () => {
    const activeOrders = await fetchOrdersData('active');
    expect(activeOrders.length).toBeGreaterThan(0);
    activeOrders.forEach((o) => expect(o.tab).toBe('active'));

    const pastOrders = await fetchOrdersData('past');
    expect(pastOrders.length).toBeGreaterThan(0);
    pastOrders.forEach((o) => expect(o.tab).toBe('past'));

    const subOrders = await fetchOrdersData('subscription');
    expect(subOrders.length).toBeGreaterThan(0);
    subOrders.forEach((o) => expect(o.tab).toBe('subscription'));
  });

  test('fetchOrdersData filters by search query', async () => {
    const searchResults = await fetchOrdersData('active', 'Royal Canin');
    expect(searchResults.length).toBe(1);
    expect(searchResults[0].orderNumber).toBe('#ORD-8821');
  });

  test('TRACKING_STEPS covers all order progress states in sequence', () => {
    expect(TRACKING_STEPS.length).toBe(5);
    expect(TRACKING_STEPS[0].key).toBe('ORDER_PLACED');
    expect(TRACKING_STEPS[1].key).toBe('CONFIRMED');
    expect(TRACKING_STEPS[2].key).toBe('PREPARING');
    expect(TRACKING_STEPS[3].key).toBe('OUT_FOR_DELIVERY');
    expect(TRACKING_STEPS[4].key).toBe('DELIVERED');
  });

  test('fetchAppointmentsData filters by tab correctly', async () => {
    const upcoming = await fetchAppointmentsData('upcoming');
    expect(upcoming.length).toBeGreaterThan(0);
    upcoming.forEach((a) => expect(a.tab).toBe('upcoming'));

    const past = await fetchAppointmentsData('past');
    expect(past.length).toBeGreaterThan(0);
    past.forEach((a) => expect(a.tab).toBe('past'));

    const cancelled = await fetchAppointmentsData('cancelled');
    expect(cancelled.length).toBeGreaterThan(0);
    cancelled.forEach((a) => expect(a.tab).toBe('cancelled'));
  });

  test('Appointments enforce valid cancellation policy fields', () => {
    MOCK_APPOINTMENTS.forEach((apt) => {
      expect(apt.cancellationPolicy.length).toBeGreaterThan(0);
      expect(apt.appointmentNumber).toMatch(/^#APT-\d+/);
      expect(apt.totalPrice).toBeGreaterThan(0);
    });
  });
});
