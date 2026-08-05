import { readFileSync } from 'node:fs';
import { join } from 'node:path';

function source(relativePath: string): string {
  return readFileSync(join(process.cwd(), relativePath), 'utf8');
}

describe('customer end-to-end regression contracts', () => {
  it('uses live catalog APIs instead of production demo fixtures', () => {
    const category = source('src/app/category/[id].tsx');
    const aliasRoute = source('src/app/commerce/[slug].tsx');
    const product = source('src/app/commerce/product-detail.tsx');
    const shop = source('src/app/shop/[id].tsx');
    const favourites = source('src/app/favourites/index.tsx');
    const discovery = source('src/screens/commerce-discovery-screen.tsx');
    const registry = source('src/services/route-catalog.ts');

    expect(category).toMatch(/fetchCommerceProducts/);
    expect(aliasRoute).toMatch(/fetchCommerceProducts/);
    expect(product).toMatch(/fetchCommerceProduct/);
    expect(shop).toMatch(/fetchShopProfile/);
    expect(favourites).toMatch(/fetchCommerceProduct/);
    expect(favourites).toMatch(/fetchShopProfile/);
    expect(discovery).toMatch(/fetchProviders\('PET_STORE'/);
    expect(`${category}\n${aliasRoute}\n${product}\n${shop}\n${favourites}\n${discovery}\n${registry}`).not.toMatch(
      /SAMPLE_PRODUCTS|SHOPS_DATA/,
    );
  });

  it('propagates and clears the authenticated payment token', () => {
    const auth = source('src/context/AuthContext.tsx');
    const payments = source('src/services/customer-payments.ts');

    expect(auth).toMatch(/apiClient\.setSessionToken\(nextSession\?\.access_token \?\? null\)/);
    expect(auth).toMatch(/apiClient\.setSessionToken\(null\)/);
    expect(payments).toMatch(/apiClient\.post/);
  });

  it('books appointments with an owned pet through hold and confirmation', () => {
    const screen = source('src/screens/appointment-discovery-screen.tsx');
    const service = source('src/services/appointment-booking.ts');

    expect(screen).toMatch(/fetchCustomerPets/);
    expect(screen).toMatch(/createCustomerPet/);
    expect(screen).toMatch(/holdAppointmentSlot/);
    expect(screen).toMatch(/confirmAppointmentHold/);
    expect(service).toMatch(/petId: input\.petId/);
    expect(service).not.toMatch(/petId: bookingUserId/);
  });

  it('isolates carts by customer and rebuilds validated carts', () => {
    const cart = source('src/context/CartContext.tsx');
    const order = source('src/app/orders/[id].tsx');
    const subscriptions = source('src/app/subscriptions/index.tsx');

    expect(cart).toMatch(/customer_\$\{userId\}/);
    expect(cart).toMatch(/replaceCart/);
    expect(order).toMatch(/buildCartFromRevalidation/);
    expect(order).toMatch(/replaceCart/);
    expect(subscriptions).toMatch(/buildCartFromRevalidation/);
    expect(subscriptions).toMatch(/replaceCart/);
  });

  it('does not show static coupons or simulated voice recognition', () => {
    const wallet = source('src/app/wallet/index.tsx');
    const search = source('src/screens/search-screen.tsx');

    expect(wallet).toMatch(/fetchActivePromotions/);
    expect(wallet).not.toMatch(/SAVE50/);
    expect(search).not.toMatch(/Puppy Nutrition/);
    expect(search).not.toMatch(/Simulate voice|Listening\.\.\. Speak now/);
  });

  it('persists address and unavailable-city intent through backend APIs', () => {
    const profile = source('src/services/customer-profile.ts');
    const location = source('src/context/LocationContext.tsx');

    expect(profile).toMatch(/\/api\/v1\/addresses\/default/);
    expect(profile).toMatch(/method: 'PUT'/);
    expect(location).toMatch(/\/api\/v1\/service-regions\/launch-requests/);
    expect(location).toMatch(/method: 'POST'/);
  });

  it('uses foreground device coordinates to select a service city', () => {
    const context = source('src/context/LocationContext.tsx');
    const modal = source('src/components/location-modal.tsx');
    const locationService = source('src/services/device-location.ts');
    const config = source('../app.json');

    expect(context).toMatch(/requestCurrentCoordinates/);
    expect(context).toMatch(/nearestEnabledCity/);
    expect(modal).toMatch(/Use current location/);
    expect(locationService).toMatch(/requestForegroundPermissionsAsync/);
    expect(locationService).toMatch(/getCurrentPositionAsync/);
    expect(config).toMatch(/expo-location/);
  });

  it('does not expose cached orders after authorization or server failures', () => {
    const orders = source('src/services/customer-orders.ts');

    expect(orders).toMatch(/if \(!isOfflineFailure\(error\)\) throw error/);
    expect(orders).toMatch(/error instanceof OrderHttpError/);
  });

  it('checks server responses for favourites and push registration', () => {
    const favourites = source('src/context/FavouritesContext.tsx');
    const notifications = source('src/hooks/usePushNotifications.ts');

    expect(favourites).toMatch(/if \(!response\.ok\) throw await serverError/);
    expect(notifications).toMatch(/if \(!response\.ok\) throw await responseError/);
  });
});
