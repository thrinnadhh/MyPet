import { getCatalogProducts, getCatalogRoute, normalizeRouteParam } from '@/services/route-catalog';

describe('customer catalog route registry', () => {
  it('normalizes Expo route parameters', () => {
    expect(normalizeRouteParam(['Food-Nutrition'])).toBe('food-nutrition');
    expect(normalizeRouteParam(undefined)).toBe('');
  });

  it('resolves Stitch catalog aliases', () => {
    expect(getCatalogRoute('food-nutrition')?.category).toBe('food');
    expect(getCatalogRoute('toys-enrichment')?.category).toBe('toys');
    expect(getCatalogRoute('travel-apparel')?.category).toBe('travel');
    expect(getCatalogRoute('waste-management')?.category).toBe('waste');
  });

  it('returns only products belonging to a category', () => {
    const route = getCatalogRoute('furniture-sleep');
    expect(route).not.toBeNull();
    const products = getCatalogProducts(route!);
    expect(products.length).toBeGreaterThan(0);
    expect(products.every((product) => product.category === 'furniture')).toBe(true);
  });

  it('filters the new-arrivals screen without inventing products', () => {
    const route = getCatalogRoute('new-arrivals');
    expect(route).not.toBeNull();
    const products = getCatalogProducts(route!);
    expect(products.length).toBeGreaterThan(0);
    expect(products.every((product) => product.isNewArrival)).toBe(true);
  });

  it('rejects unknown route slugs', () => {
    expect(getCatalogRoute('not-a-real-category')).toBeNull();
  });
});
