import AsyncStorage from '@react-native-async-storage/async-storage';
import React, { createContext, useCallback, useContext, useEffect, useState } from 'react';

import { useAuth } from '@/context/AuthContext';
import { useAuthIntent } from '@/context/AuthIntentContext';
import { appConfig } from '@/utils/app-config';

export interface FavouriteItem {
  id?: string;
  targetType: 'PRODUCT' | 'SHOP';
  targetId: string;
  createdAt?: string;
}

interface FavouritesContextType {
  favourites: FavouriteItem[];
  loading: boolean;
  isFavourite: (targetType: 'PRODUCT' | 'SHOP', targetId: string) => boolean;
  toggleFavourite: (targetType: 'PRODUCT' | 'SHOP', targetId: string) => Promise<boolean>;
}

const FavouritesContext = createContext<FavouritesContextType | null>(null);
const STORAGE_KEY = 'mypet_favourites_v1';

export function FavouritesProvider({ children }: { children: React.ReactNode }) {
  const { user, session } = useAuth();
  const authIntent = useAuthIntent();
  const [favourites, setFavourites] = useState<FavouriteItem[]>([]);
  const [loading, setLoading] = useState(true);

  // Sync / load favourites
  useEffect(() => {
    const loadFavourites = async () => {
      setLoading(true);
      if (user?.id && session?.access_token) {
        // Authenticated mode: Fetch from server API
        try {
          const res = await fetch(`${appConfig.apiBaseUrl}/api/v1/customer/favourites`, {
            headers: {
              'X-User-Id': user.id,
              Authorization: `Bearer ${session.access_token}`,
            },
          });
          if (res.ok) {
            const data = (await res.json()) as FavouriteItem[];
            setFavourites(data);
          }
        } catch (e) {
          console.warn('Failed to fetch server favourites', e);
        }
      } else {
        // Guest mode: Read local AsyncStorage
        try {
          const stored = await AsyncStorage.getItem(STORAGE_KEY);
          if (stored) {
            setFavourites(JSON.parse(stored));
          }
        } catch (e) {
          console.warn('Failed to load guest favourites', e);
        }
      }
      setLoading(false);
    };
    void loadFavourites();
  }, [user?.id, session?.access_token]);

  const isFavourite = useCallback(
    (targetType: 'PRODUCT' | 'SHOP', targetId: string): boolean => {
      return favourites.some(
        (f) => f.targetType.toUpperCase() === targetType.toUpperCase() && f.targetId === targetId
      );
    },
    [favourites]
  );

  const toggleFavourite = useCallback(
    async (targetType: 'PRODUCT' | 'SHOP', targetId: string): Promise<boolean> => {
      const typeUpper = targetType.toUpperCase() as 'PRODUCT' | 'SHOP';
      const currentlyFav = favourites.some(
        (f) => f.targetType.toUpperCase() === typeUpper && f.targetId === targetId
      );

      // S10 Auth Intent Guard: If guest and attempting to add, prompt auth intent
      if (!session && authIntent && typeof authIntent.requireAuth === 'function') {
        void authIntent.requireAuth({ kind: 'SAVE_FAVOURITE', returnTo: '/favourites' } as never);
      }


      if (currentlyFav) {
        // Remove favourite
        const next = favourites.filter(
          (f) => !(f.targetType.toUpperCase() === typeUpper && f.targetId === targetId)
        );
        setFavourites(next);

        if (user?.id && session?.access_token) {
          try {
            await fetch(
              `${appConfig.apiBaseUrl}/api/v1/customer/favourites?targetType=${typeUpper}&targetId=${targetId}`,
              {
                method: 'DELETE',
                headers: {
                  'X-User-Id': user.id,
                  Authorization: `Bearer ${session.access_token}`,
                },
              }
            );
          } catch (e) {
            console.warn('Error deleting server favourite', e);
          }
        } else {
          await AsyncStorage.setItem(STORAGE_KEY, JSON.stringify(next));
        }
        return false;
      } else {
        // Add favourite
        const newItem: FavouriteItem = { targetType: typeUpper, targetId, createdAt: new Date().toISOString() };
        const next = [newItem, ...favourites];
        setFavourites(next);

        if (user?.id && session?.access_token) {
          try {
            await fetch(`${appConfig.apiBaseUrl}/api/v1/customer/favourites`, {
              method: 'POST',
              headers: {
                'Content-Type': 'application/json',
                'X-User-Id': user.id,
                Authorization: `Bearer ${session.access_token}`,
              },
              body: JSON.stringify({ targetType: typeUpper, targetId }),
            });
          } catch (e) {
            console.warn('Error saving server favourite', e);
          }
        } else {
          await AsyncStorage.setItem(STORAGE_KEY, JSON.stringify(next));
        }
        return true;
      }
    },
    [authIntent, favourites, session, user]
  );


  return (
    <FavouritesContext.Provider value={{ favourites, loading, isFavourite, toggleFavourite }}>
      {children}
    </FavouritesContext.Provider>
  );
}

export function useFavourites() {
  const context = useContext(FavouritesContext);
  if (!context) {
    throw new Error('useFavourites must be used within FavouritesProvider');
  }
  return context;
}
