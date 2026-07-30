import AsyncStorage from '@react-native-async-storage/async-storage';
import React, { createContext, useCallback, useContext, useEffect, useState } from 'react';
import { Alert } from 'react-native';

import { appConfig } from '@/utils/app-config';

export interface ServiceRegionFeatureFlags {
  allowProducts: boolean;
  allowGrooming: boolean;
  allowVet: boolean;
  allowOwnDelivery: boolean;
  allow3pDelivery: boolean;
  allowCod: boolean;
  allowOnlinePayment: boolean;
}

export interface ActiveCity {
  id: string;
  cityIdentity: string;
  displayName: string;
  state: string;
  country: string;
  centerLatitude: number;
  centerLongitude: number;
  radiusKm: number;
  pincodes: string[];
  featureFlags: ServiceRegionFeatureFlags;
}

export const DEFAULT_TIRUPATI_REGION: ActiveCity = {
  id: '81111111-1111-1111-1111-111111111111',
  cityIdentity: 'tirupati',
  displayName: 'Tirupati',
  state: 'Andhra Pradesh',
  country: 'India',
  centerLatitude: 13.6288,
  centerLongitude: 79.4192,
  radiusKm: 25.0,
  pincodes: ['517501', '517502', '517507'],
  featureFlags: {
    allowProducts: true,
    allowGrooming: true,
    allowVet: true,
    allowOwnDelivery: true,
    allow3pDelivery: true,
    allowCod: true,
    allowOnlinePayment: true,
  },
};

interface LocationContextType {
  activeCity: ActiveCity;
  enabledCities: ActiveCity[];
  isLocationModalOpen: boolean;
  isNotifyModalOpen: boolean;
  requestedUnavailableCity: string | null;
  loading: boolean;
  openLocationModal: () => void;
  closeLocationModal: () => void;
  closeNotifyModal: () => void;
  selectCity: (city: ActiveCity) => Promise<void>;
  requestUnavailableCityLaunch: (cityName: string) => void;
  submitCityNotificationRequest: (contactInfo: string) => Promise<void>;
  refreshCities: () => Promise<void>;
}

const LocationContext = createContext<LocationContextType | null>(null);
const STORAGE_KEY = 'mypet_active_city_v1';

export function LocationProvider({ children }: { children: React.ReactNode }) {
  const [activeCity, setActiveCity] = useState<ActiveCity>(DEFAULT_TIRUPATI_REGION);
  const [enabledCities, setEnabledCities] = useState<ActiveCity[]>([DEFAULT_TIRUPATI_REGION]);
  const [isLocationModalOpen, setIsLocationModalOpen] = useState(false);
  const [isNotifyModalOpen, setIsNotifyModalOpen] = useState(false);
  const [requestedUnavailableCity, setRequestedUnavailableCity] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  const fetchActiveCities = useCallback(async (): Promise<ActiveCity[]> => {
    try {
      const response = await fetch(`${appConfig.apiBaseUrl}/api/v1/service-regions/active`);
      if (response.ok) {
        const data = (await response.json()) as ActiveCity[];
        if (Array.isArray(data) && data.length > 0) {
          return data;
        }
      }
    } catch (e) {
      console.warn('Failed to fetch active service regions', e);
    }
    return [DEFAULT_TIRUPATI_REGION];
  }, []);

  const refreshCities = useCallback(async () => {
    setLoading(true);
    const cities = await fetchActiveCities();
    setEnabledCities(cities);

    // If current active city is no longer in enabled cities, fallback to first enabled city
    setActiveCity((current) => {
      const stillActive = cities.find((c) => c.cityIdentity === current.cityIdentity);
      return stillActive ?? cities[0] ?? DEFAULT_TIRUPATI_REGION;
    });
    setLoading(false);
  }, [fetchActiveCities]);

  useEffect(() => {
    const initLocation = async () => {
      const cities = await fetchActiveCities();
      setEnabledCities(cities);

      try {
        const stored = await AsyncStorage.getItem(STORAGE_KEY);
        if (stored) {
          const parsed = JSON.parse(stored) as ActiveCity;
          const matched = cities.find((c) => c.cityIdentity === parsed.cityIdentity);
          if (matched) {
            setActiveCity(matched);
          } else if (cities.length > 0) {
            setActiveCity(cities[0]);
          }
        }
      } catch (e) {
        console.warn('Error reading stored active city', e);
      } finally {
        setLoading(false);
      }
    };
    void initLocation();
  }, [fetchActiveCities]);

  const selectCity = useCallback(async (city: ActiveCity) => {
    setActiveCity(city);
    setIsLocationModalOpen(false);
    try {
      await AsyncStorage.setItem(STORAGE_KEY, JSON.stringify(city));
    } catch (e) {
      console.warn('Error persisting active city', e);
    }
  }, []);

  const openLocationModal = useCallback(() => setIsLocationModalOpen(true), []);
  const closeLocationModal = useCallback(() => setIsLocationModalOpen(false), []);
  const closeNotifyModal = useCallback(() => {
    setIsNotifyModalOpen(false);
    setRequestedUnavailableCity(null);
  }, []);

  const requestUnavailableCityLaunch = useCallback((cityName: string) => {
    setRequestedUnavailableCity(cityName);
    setIsLocationModalOpen(false);
    setIsNotifyModalOpen(true);
  }, []);

  const submitCityNotificationRequest = useCallback(async (contactInfo: string) => {
    if (!contactInfo.trim()) return;
    Alert.alert('Thank you!', `We've recorded your interest for ${requestedUnavailableCity ?? 'your city'}. We will notify ${contactInfo} when we launch!`);
    setIsNotifyModalOpen(false);
    setRequestedUnavailableCity(null);
  }, [requestedUnavailableCity]);

  return (
    <LocationContext.Provider
      value={{
        activeCity,
        enabledCities,
        isLocationModalOpen,
        isNotifyModalOpen,
        requestedUnavailableCity,
        loading,
        openLocationModal,
        closeLocationModal,
        closeNotifyModal,
        selectCity,
        requestUnavailableCityLaunch,
        submitCityNotificationRequest,
        refreshCities,
      }}
    >
      {children}
    </LocationContext.Provider>
  );
}

export function useLocation() {
  const context = useContext(LocationContext);
  if (!context) {
    throw new Error('useLocation must be used within LocationProvider');
  }
  return context;
}
