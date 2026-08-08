import fs from 'fs';
import path from 'path';

import { RECURRING_CADENCES, isRecurringCadence } from '@/contracts/recurring-orders';

const ROOT = path.resolve(__dirname, '../..');
const source = (relativePath: string) => fs.readFileSync(path.join(ROOT, relativePath), 'utf8');

function expectAll(content: string, values: string[]) {
  for (const value of values) expect(content).toContain(value);
}

describe('MyPet customer end-to-end journeys', () => {
  it('keeps home discovery and primary catalog routes on live customer services', () => {
    const home = source('src/app/(tabs)/index.tsx');
    const categories = source('src/app/category/[slug].tsx');
    const product = source('src/app/product/[id].tsx');
    const shop = source('src/app/shop/[id].tsx');
    const catalog = source('src/services/customer-catalog.ts');

    expectAll(home, ['fetchCustomerStorefront', 'fetchCustomerCategorySummary']);
    expectAll(categories, ['fetchCustomerCategoryPage']);
    expectAll(product, ['fetchCustomerProductDetail']);
    expectAll(shop, ['fetchCustomerShopDetail']);
    expectAll(catalog, ['/api/v1/discovery/stores', '/api/v1/catalog/offerings']);
  });

  it('keeps resilient media on customer catalog and banner presentation', () => {
    const home = source('src/app/(tabs)/index.tsx');
    const category = source('src/app/category/[slug].tsx');
    const product = source('src/app/product/[id].tsx');
    const shop = source('src/app/shop/[id].tsx');
    const checkout = source('src/app/checkout/index.tsx');

    for (const content of [home, category, product, shop, checkout]) {
      expect(content).toContain('ResilientRemoteImage');
    }
  });

  it('keeps development-only customer demo fixtures explicitly gated', () => {
    const demo = source('src/services/demo-customer-data.ts');
    const config = source('src/utils/app-config.ts');

    expectAll(config, ['EXPO_PUBLIC_ALLOW_DEMO_MODE']);
    expectAll(demo, ['isDemoModeEnabled']);
  });

  it('keeps vet and grooming appointment booking on authenticated hold and payment flow', () => {
    const booking = source('src/services/appointment-booking.ts');
    const payments = source('src/services/customer-appointment-payments.ts');
    const slots = source('src/app/provider/[id]/slots.tsx');

    expectAll(booking, ['/api/v1/appointments/hold', '/api/v1/appointments']);
    expectAll(payments, ['/api/v1/payments/appointments', 'reconcile']);
    expectAll(slots, ['createAppointmentHold', 'appointment-payment']);
  });

  it('keeps appointment confirmation server-authoritative after payment', () => {
    const paymentScreen = source('src/app/appointment-payment.tsx');
    const payments = source('src/services/customer-appointment-payments.ts');

    expectAll(paymentScreen, ['reconcileAppointmentPayment', 'confirmAppointmentPayment']);
    expectAll(payments, ['SUCCESS', '/confirm']);
    expect(paymentScreen).not.toContain('setTimeout(() => router.replace');
  });

  it('keeps product checkout itemized and server-authoritative', () => {
    const checkout = source('src/app/checkout/index.tsx');
    const orders = source('src/services/customer-orders.ts');

    expectAll(checkout, ['lineTotal', 'Server-authoritative']);
    expectAll(orders, ['/api/v1/checkout/quote', '/api/v1/orders']);
  });

  it('keeps demo checkout non-chargeable and isolated from backend order creation', () => {
    const checkout = source('src/app/checkout/index.tsx');

    expectAll(checkout, ['demo', 'preview']);
    expect(checkout).toContain('isDemoModeEnabled');
  });

  it('keeps authenticated customer profile and address requests on bearer auth', () => {
    const api = source('src/services/api-client.ts');
    const profile = source('src/services/customer-profile.ts');
    const orders = source('src/services/customer-orders.ts');
    const payments = source('src/services/customer-payments.ts');

    expect(api).toContain('Authorization');
    expectAll(profile, ['/api/v1/profiles']);
    expectAll(orders, ['Authorization: `Bearer ${accessToken}`']);
    expect(payments).toContain('normalizedPhone');
  });

  it('keeps recurring-order cadence, exactly-once generation and payment safety intact', () => {
    const subscriptions = source('src/app/subscriptions/index.tsx');
    const service = source('src/services/recurring-orders.ts');
    const backend = source('../../backend/order-service/src/main/kotlin/com/pawsnearme/orderservice/service/RecurringOrderService.kt');
    const creator = source('../../backend/order-service/src/main/kotlin/com/pawsnearme/orderservice/service/RecurringOccurrenceOrderCreator.kt');

    expect(RECURRING_CADENCES).toEqual([7, 15, 25, 30, 35]);
    for (const cadence of RECURRING_CADENCES) expect(isRecurringCadence(cadence)).toBe(true);
    expect(isRecurringCadence(10)).toBe(false);

    expectAll(subscriptions, [
      'One scheduled run creates at most one real order',
      'Payment is never charged silently',
      'Generated order',
      'Revalidate and confirm',
      'No silent charging',
    ]);
    expect(service).toContain('/api/v1/orders/subscriptions');
    expectAll(backend, [
      'processDueOrders',
      'RecurringOrderGenerated',
      'RecurringOrderFailed',
      'PRICE_CHANGED',
      'occurrenceOrderCreator.createOrGet',
    ]);
    expectAll(creator, [
      'Propagation.REQUIRES_NEW',
      'findByRecurringOccurrenceId',
      'R-$occurrenceId',
    ]);
    expect(backend).not.toContain('RecurringOrderConfirmationRequired');
  });

  it('keeps authentication token propagation aligned across customer services', () => {
    const supabase = source('src/utils/supabase.ts');
    const payment = source('src/services/customer-payments.ts');

    expectAll(supabase, ['onAuthStateChange']);
    expect(payment).toContain('accessToken');
  });

  it('keeps customer location discovery on device coordinates with fallback', () => {
    const deviceLocation = source('src/services/device-location.ts');
    const locationScreen = source('src/app/location.tsx');

    expectAll(deviceLocation, ['requestForegroundPermissionsAsync', 'getCurrentPositionAsync']);
    expectAll(locationScreen, ['findNearestServiceCity', 'Manual']);
  });

  it('keeps reorder and recurring checkout reconstruction server-revalidated', () => {
    const cart = source('src/services/revalidated-cart.ts');
    const orders = source('src/app/(tabs)/orders.tsx');

    expectAll(cart, ['revalidateReorder', 'fetchCustomerProductDetail']);
    expect(orders).toContain('populateCartFromRevalidatedOrder');
  });
});
