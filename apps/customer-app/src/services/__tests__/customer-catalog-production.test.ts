import { appConfig } from '@/utils/app-config';
import { fetchProviders } from '../provider-discovery';
import {
  fetchCommerceProduct,
  fetchCommerceProducts,
  fetchProviderOfferings,
  fetchPublicProvider,
  fetchShopProfile,
} from '../customer-catalog';

jest.mock('@/utils/app-config', () => ({
  appConfig: {
    apiBaseUrl: 'https://api.mypet.test/',
    allowDemoMode: false,
  },
}));

jest.mock('../provider-discovery', () => ({
  fetchProviders: jest.fn(),
}));

const mockedFetch = jest.fn();
const mockedFetchProviders = fetchProviders as jest.MockedFunction<typeof fetchProviders>;

function jsonResponse(body: unknown, status = 200): Response {
  return {
    ok: status >= 200 && status < 300,
    status,
    json: jest.fn().mockResolvedValue(body),
  } as unknown as Response;
}

const provider = {
  providerId: 'provider-1',
  providerType: 'PET_STORE',
  fulfillmentType: 'DELIVERY',
  name: 'Tirupati Pet Mart',
  description: 'Verified local pet store',
  city: 'Tirupati',
  status: 'ACTIVE',
  ratingAvg: '4.6',
  ratingCount: 27,
};

const recent = new Date().toISOString();
const old = new Date(Date.now() - 60 * 24 * 60 * 60 * 1000).toISOString();

const offerings = [
  {
    offeringId: 'food-1',
    providerId: 'provider-1',
    name: 'Puppy Nutrition Pack',
    description: 'Complete puppy food',
    category: 'Food & Nutrition',
    price: '499.50',
    imageUrl: '',
    status: 'ACTIVE',
    stockQuantity: 5,
    sku: 'PUPPY-3KG',
    createdAt: recent,
  },
  {
    offeringId: 'toy-1',
    providerId: 'provider-1',
    name: 'Rope Toy',
    description: null,
    category: 'Toys & Enrichment',
    price: 199,
    imageUrl: null,
    status: 'ACTIVE',
    stockQuantity: 0,
    sku: null,
    createdAt: old,
  },
  {
    offeringId: 'inactive-1',
    providerId: 'provider-1',
    name: 'Hidden Treat',
    category: 'Treats & Chews',
    price: 99,
    status: 'INACTIVE',
    stockQuantity: 10,
    createdAt: recent,
  },
];

describe('customer catalog production adapter', () => {
  beforeEach(() => {
    mockedFetch.mockReset();
    mockedFetchProviders.mockReset();
    global.fetch = mockedFetch as unknown as typeof fetch;
    appConfig.allowDemoMode = false;
    appConfig.apiBaseUrl = 'https://api.mypet.test/';
  });

  it('fetches a public provider from the production API and trims the base URL', async () => {
    mockedFetch.mockResolvedValueOnce(jsonResponse(provider));

    await expect(fetchPublicProvider('provider 1')).resolves.toEqual(provider);

    expect(mockedFetch).toHaveBeenCalledWith(
      'https://api.mypet.test/api/v1/providers/provider%201',
      { headers: { Accept: 'application/json' } },
    );
  });

  it('surfaces non-success catalog responses instead of returning fake data', async () => {
    mockedFetch.mockResolvedValueOnce(jsonResponse({ code: 'NOT_FOUND' }, 404));

    await expect(fetchPublicProvider('missing')).rejects.toThrow('CATALOG_404');
  });

  it('filters inactive offerings at the production service boundary', async () => {
    mockedFetch.mockResolvedValueOnce(jsonResponse(offerings));

    const result = await fetchProviderOfferings('provider-1');

    expect(result.map((item) => item.offeringId)).toEqual(['food-1', 'toy-1']);
    expect(mockedFetch).toHaveBeenCalledWith(
      'https://api.mypet.test/api/v1/catalog/offerings?providerId=provider-1',
      { headers: { Accept: 'application/json' } },
    );
  });

  it('maps an authoritative offering and provider into a customer commerce product', async () => {
    mockedFetch
      .mockResolvedValueOnce(jsonResponse(offerings[0]))
      .mockResolvedValueOnce(jsonResponse(provider));

    const product = await fetchCommerceProduct('food-1');

    expect(product).toEqual(expect.objectContaining({
      id: 'food-1',
      name: 'Puppy Nutrition Pack',
      brand: 'Tirupati Pet Mart',
      category: 'food',
      price: 499.5,
      inStock: true,
      stockCount: 5,
      providerId: 'provider-1',
      providerName: 'Tirupati Pet Mart',
      isNewArrival: true,
    }));
    expect(product.variants[0]).toEqual(expect.objectContaining({
      name: 'PUPPY-3KG',
      price: 499.5,
      inStock: true,
      stockCount: 5,
    }));
    expect(product.specifications).toEqual(expect.objectContaining({
      Category: 'Food & Nutrition',
      SKU: 'PUPPY-3KG',
      Availability: 'In stock',
    }));
    expect(product.imageUrl).toBeTruthy();
  });

  it('maps provider discovery plus real offerings, then applies category and new-arrival filters', async () => {
    mockedFetchProviders.mockResolvedValueOnce([
      {
        id: 'provider-1',
        name: 'Tirupati Pet Mart',
        description: 'Verified local pet store',
        distanceKm: 2,
        rating: 4.6,
        ratingCount: 27,
      },
    ]);
    mockedFetch.mockResolvedValueOnce(jsonResponse(offerings));

    const result = await fetchCommerceProducts({ category: 'nutrition', onlyNewArrivals: true });

    expect(result).toHaveLength(1);
    expect(result[0]).toEqual(expect.objectContaining({
      id: 'food-1',
      category: 'food',
      deliveryTime: expect.stringContaining('mins'),
      deliveryEstimate: expect.stringContaining('2.0 km'),
    }));
    expect(mockedFetchProviders).toHaveBeenCalledWith('PET_STORE', expect.anything());
  });

  it('uses a provider-specific production query and maps out-of-stock products safely', async () => {
    mockedFetch
      .mockResolvedValueOnce(jsonResponse(provider))
      .mockResolvedValueOnce(jsonResponse([offerings[1]]));

    const result = await fetchCommerceProducts({ providerId: 'provider-1' });

    expect(result).toHaveLength(1);
    expect(result[0]).toEqual(expect.objectContaining({
      id: 'toy-1',
      category: 'toys',
      inStock: false,
      stockCount: 0,
      description: 'Rope Toy from Tirupati Pet Mart.',
    }));
    expect(result[0].variants[0]).toEqual(expect.objectContaining({
      name: 'Standard',
      inStock: false,
    }));
    expect(result[0].specifications.Availability).toBe('Out of stock');
  });

  it('builds the customer shop profile from real provider and catalog data', async () => {
    mockedFetch
      .mockResolvedValueOnce(jsonResponse(provider))
      .mockResolvedValueOnce(jsonResponse(offerings));

    const shop = await fetchShopProfile('provider-1');

    expect(shop).toEqual(expect.objectContaining({
      id: 'provider-1',
      name: 'Tirupati Pet Mart',
      tagline: 'Verified local pet store',
      address: 'Tirupati',
      city: 'Tirupati',
      rating: '4.6 ★',
      reviewCount: 27,
      isVerified: true,
      openingHours: 'Store hours confirmed during checkout',
    }));
    expect(shop.categories).toEqual(['food', 'toys']);
    expect(shop.products).toHaveLength(2);
  });
});
