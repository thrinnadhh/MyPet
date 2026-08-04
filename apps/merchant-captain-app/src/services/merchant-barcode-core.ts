import { ApiError } from '@/contracts/api-error';
import { barcodeLookupCandidates, barcodeValidationMessage, normalizeBarcode } from '@/utils/barcode';

import type { MerchantOffering } from './merchant-inventory';

const CACHE_PREFIX = '@mypet:merchant-barcode-catalog:v1:';

type CachedBarcodeCatalog = {
  providerId: string;
  updatedAt: string;
  offerings: BarcodeOffering[];
};

export type BarcodeOffering = MerchantOffering & {
  offeringId: string;
  barcode: string;
  stockQuantity: number;
};

export type BarcodeResolution = {
  offering: BarcodeOffering;
  source: 'network' | 'cache';
  cachedAt?: string;
};

export type MerchantBarcodeDependencies = {
  getNetworkState: () => Promise<{ isConnected: boolean | null }>;
  getCacheItem: (key: string) => Promise<string | null>;
  setCacheItem: (key: string, value: string) => Promise<void>;
  fetchOfferingByBarcode: (providerId: string, barcode: string) => Promise<MerchantOffering>;
  fetchOfferings: (providerId: string) => Promise<MerchantOffering[]>;
  now?: () => Date;
};

export type MerchantBarcodeService = {
  refreshProviderBarcodeCatalog: (providerId: string) => Promise<number>;
  resolveMerchantBarcode: (providerId: string, rawBarcode: string) => Promise<BarcodeResolution>;
};

export class OfflineBarcodeMissError extends Error {
  constructor(barcode: string) {
    super(`Barcode ${barcode} is not available in the offline catalog. Connect once to refresh inventory.`);
    this.name = 'OfflineBarcodeMissError';
  }
}

function cacheKey(providerId: string): string {
  return `${CACHE_PREFIX}${providerId}`;
}

function asBarcodeOffering(offering: MerchantOffering): BarcodeOffering | null {
  const barcode = normalizeBarcode(offering.barcode ?? '');
  if (!offering.offeringId || !barcode || offering.stockQuantity === null || offering.stockQuantity === undefined) {
    return null;
  }
  return {
    ...offering,
    offeringId: offering.offeringId,
    barcode,
    stockQuantity: offering.stockQuantity,
  };
}

export function createMerchantBarcodeService(
  dependencies: MerchantBarcodeDependencies,
): MerchantBarcodeService {
  const now = dependencies.now ?? (() => new Date());

  async function readCatalog(providerId: string): Promise<CachedBarcodeCatalog | null> {
    try {
      const raw = await dependencies.getCacheItem(cacheKey(providerId));
      if (!raw) return null;
      const parsed = JSON.parse(raw) as CachedBarcodeCatalog;
      if (parsed.providerId !== providerId || !Array.isArray(parsed.offerings)) return null;
      return parsed;
    } catch {
      return null;
    }
  }

  async function buildAndStoreCatalog(
    providerId: string,
    offerings: MerchantOffering[],
  ): Promise<CachedBarcodeCatalog> {
    const catalog: CachedBarcodeCatalog = {
      providerId,
      updatedAt: now().toISOString(),
      offerings: offerings.flatMap((offering) => {
        const normalized = asBarcodeOffering(offering);
        return normalized ? [normalized] : [];
      }),
    };
    try {
      await dependencies.setCacheItem(cacheKey(providerId), JSON.stringify(catalog));
    } catch {
      // Device storage failure must not block a valid online sale.
    }
    return catalog;
  }

  async function upsertCachedOffering(providerId: string, offering: MerchantOffering): Promise<void> {
    const normalized = asBarcodeOffering(offering);
    if (!normalized) return;

    const existing = await readCatalog(providerId);
    const offerings = existing?.offerings ?? [];
    const withoutCurrent = offerings.filter((item) => item.offeringId !== normalized.offeringId);
    await buildAndStoreCatalog(providerId, [...withoutCurrent, normalized]);
  }

  async function refreshProviderBarcodeCatalog(providerId: string): Promise<number> {
    const offerings = await dependencies.fetchOfferings(providerId);
    const catalog = await buildAndStoreCatalog(providerId, offerings);
    return catalog.offerings.length;
  }

  async function resolveMerchantBarcode(
    providerId: string,
    rawBarcode: string,
  ): Promise<BarcodeResolution> {
    const barcode = normalizeBarcode(rawBarcode);
    const validationMessage = barcodeValidationMessage(barcode);
    if (!barcode) throw new Error('Enter or scan a barcode first.');
    if (validationMessage) throw new Error(validationMessage);

    const networkState = await dependencies.getNetworkState();
    if (networkState.isConnected) {
      try {
        const offering = await dependencies.fetchOfferingByBarcode(providerId, barcode);
        const normalized = asBarcodeOffering(offering);
        if (!normalized) throw new Error('The scanned catalog item is not a stock-tracked product.');
        await upsertCachedOffering(providerId, normalized);
        return { offering: normalized, source: 'network' };
      } catch (error) {
        if (error instanceof ApiError && error.status < 500 && error.status !== 408 && error.status !== 429) {
          throw error;
        }
        if (!(error instanceof TypeError) && !(error instanceof ApiError)) throw error;
      }
    }

    const catalog = await readCatalog(providerId);
    const candidates = new Set(barcodeLookupCandidates(barcode));
    const offering = catalog?.offerings.find((item) => candidates.has(normalizeBarcode(item.barcode)));
    if (!offering) throw new OfflineBarcodeMissError(barcode);

    return {
      offering,
      source: 'cache',
      cachedAt: catalog?.updatedAt,
    };
  }

  return {
    refreshProviderBarcodeCatalog,
    resolveMerchantBarcode,
  };
}
