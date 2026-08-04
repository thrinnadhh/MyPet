import AsyncStorage from '@react-native-async-storage/async-storage';
import NetInfo from '@react-native-community/netinfo';

import { apiClient } from './api-client';
import { createMerchantBarcodeService } from './merchant-barcode-core';
import { fetchMerchantOfferings, type MerchantOffering } from './merchant-inventory';

export {
  createMerchantBarcodeService,
  OfflineBarcodeMissError,
  type BarcodeOffering,
  type BarcodeResolution,
  type MerchantBarcodeDependencies,
  type MerchantBarcodeService,
} from './merchant-barcode-core';

const defaultService = createMerchantBarcodeService({
  getNetworkState: () => NetInfo.fetch(),
  getCacheItem: (key) => AsyncStorage.getItem(key),
  setCacheItem: (key, value) => AsyncStorage.setItem(key, value),
  fetchOfferingByBarcode: (providerId, barcode) =>
    apiClient.get<MerchantOffering>(
      `/api/v1/catalog/offerings/by-barcode?storeId=${encodeURIComponent(providerId)}&barcode=${encodeURIComponent(barcode)}`,
    ),
  fetchOfferings: fetchMerchantOfferings,
});

export const refreshProviderBarcodeCatalog = defaultService.refreshProviderBarcodeCatalog;
export const resolveMerchantBarcode = defaultService.resolveMerchantBarcode;
