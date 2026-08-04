import React, { createContext, useContext, useEffect, useState } from 'react';
import { Session, User } from '@supabase/supabase-js';

import { ApiError, apiClient } from '../services/api-client';
import { stopCaptainLocationTracking } from '../services/captain-location';
import { appConfig } from '../utils/app-config';
import { syncAuthenticatedProfile } from '../utils/profile-sync';
import { supabase } from '../utils/supabase';

interface AuthContextType {
  user: User | null;
  session: Session | null;
  role: string | null;
  activeRole: string | null;
  providerId: string | null;
  captainId: string | null;
  toggleActiveRole: () => void;
  loading: boolean;
  signOut: () => Promise<void>;
}

interface ProviderIdentity {
  providerId: string;
  status?: string;
}

interface CaptainIdentity {
  captainId: string;
}

const AuthContext = createContext<AuthContextType>({
  user: null,
  session: null,
  role: null,
  activeRole: null,
  providerId: null,
  captainId: null,
  toggleActiveRole: () => {},
  loading: true,
  signOut: async () => {},
});

export const AuthProvider = ({ children }: { children: React.ReactNode }) => {
  const [user, setUser] = useState<User | null>(null);
  const [session, setSession] = useState<Session | null>(null);
  const [role, setRole] = useState<string | null>(null);
  const [activeRole, setActiveRole] = useState<string | null>(null);
  const [providerId, setProviderId] = useState<string | null>(null);
  const [captainId, setCaptainId] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (appConfig.allowDemoMode) {
      const demoRole = appConfig.demoRole;
      const isAdminDemo = demoRole === 'ADMIN';
      const isCaptainDemo = demoRole === 'CAPTAIN';

      const merchantDemoId = 'd3b07384-d113-4e4e-9c8e-3d8e3d8e3d8e';
      const captainDemoId = 'c7f4b0c9-88c8-4f09-8aa5-f6c587c62ee1';
      const adminDemoId = 'a11d0000-0000-4000-8000-000000000001';

      const demoUserId = isAdminDemo
        ? adminDemoId
        : isCaptainDemo
          ? captainDemoId
          : merchantDemoId;

      const demoEmail = isAdminDemo
        ? 'admin@pawsnearme.com'
        : isCaptainDemo
          ? 'captain@pawsnearme.com'
          : 'merchant@pawsnearme.com';

      const demoName = isAdminDemo
        ? 'Demo Admin'
        : isCaptainDemo
          ? 'Demo Captain'
          : 'Demo Merchant';

      console.log(`AuthProvider: Running in ${demoRole} demo mode`);

      const mockUser = {
        id: demoUserId,
        email: demoEmail,
        app_metadata: { role: demoRole },
        user_metadata: {
          full_name: demoName,
          role: demoRole,
        },
        aud: 'authenticated',
        created_at: new Date().toISOString(),
      } as User;

      const mockSession = {
        access_token: 'mock-jwt-token-for-dev-purposes-only',
        token_type: 'bearer',
        expires_in: 3600,
        refresh_token: 'mock-refresh-token',
        user: mockUser,
      } as Session;

      setSession(mockSession);
      setUser(mockUser);
      setRole(demoRole);
      setActiveRole(
        isAdminDemo ? 'ADMIN' : isCaptainDemo ? 'CAPTAIN' : 'PROVIDER',
      );

      setProviderId(
        isAdminDemo ? merchantDemoId : isCaptainDemo ? null : merchantDemoId,
      );
      setCaptainId(
        isAdminDemo ? captainDemoId : isCaptainDemo ? captainDemoId : null,
      );

      apiClient.setSessionToken(mockSession.access_token);
      setLoading(false);
      return;
    }

    const normalizeBackendRole = (rawRole: string | undefined) => {
      const roleValue = rawRole?.toUpperCase();
      return roleValue === 'PROVIDER' ? 'MERCHANT' : roleValue || 'MERCHANT';
    };

    const resolveActiveRole = (backendRole: string) => {
      if (backendRole === 'ADMIN') return 'ADMIN';
      if (backendRole === 'CAPTAIN') return 'CAPTAIN';
      return 'PROVIDER';
    };

    const isMissingIdentity = (error: unknown) =>
      error instanceof ApiError && (error.status === 400 || error.status === 404);

    const resolveOperationalIdentity = async (backendRole: string) => {
      let nextProviderId: string | null = null;
      let nextCaptainId: string | null = null;

      if (backendRole === 'MERCHANT' || backendRole === 'ADMIN') {
        try {
          const providers = await apiClient.get<ProviderIdentity[]>('/api/v1/providers/me');
          const preferred = providers.find((provider) => provider.status === 'ACTIVE') ?? providers[0];
          nextProviderId = preferred?.providerId ?? null;
        } catch (error) {
          if (!isMissingIdentity(error)) {
            console.warn('Provider identity resolution failed', error);
          }
        }
      }

      if (backendRole === 'CAPTAIN' || backendRole === 'ADMIN') {
        try {
          const captain = await apiClient.get<CaptainIdentity>('/api/v1/captains/me');
          nextCaptainId = captain.captainId;
        } catch (error) {
          if (!isMissingIdentity(error)) {
            console.warn('Captain identity resolution failed', error);
          }
        }
      }

      setProviderId(nextProviderId);
      setCaptainId(nextCaptainId);
    };

    let lastHydrationKey: string | null = null;

    const applySession = async (nextSession: Session | null) => {
      setSession(nextSession);
      setUser(nextSession?.user ?? null);
      apiClient.setSessionToken(nextSession?.access_token ?? null);

      if (!nextSession) {
        await stopCaptainLocationTracking();
        setRole(null);
        setActiveRole(null);
        setProviderId(null);
        setCaptainId(null);
        lastHydrationKey = null;
        setLoading(false);
        return;
      }

      const backendRole = normalizeBackendRole(
        (nextSession.user.app_metadata?.role as string | undefined) ??
          (nextSession.user.user_metadata?.role as string | undefined),
      );
      setRole(backendRole);
      setActiveRole(resolveActiveRole(backendRole));

      // Supabase authentication is complete. Release the UI immediately;
      // backend profile/provider hydration continues without blocking login.
      setLoading(false);

      const hydrationKey = `${nextSession.user.id}:${nextSession.access_token}`;
      if (lastHydrationKey === hydrationKey) return;
      lastHydrationKey = hydrationKey;

      void (async () => {
        try {
          await Promise.race([
            syncAuthenticatedProfile(
              nextSession,
              backendRole as 'MERCHANT' | 'CAPTAIN' | 'ADMIN',
            ),
            new Promise<never>((_, reject) =>
              setTimeout(() => reject(new Error('Profile sync timed out')), 5000),
            ),
          ]);
        } catch (error) {
          console.warn('Profile sync deferred', error);
        }

        try {
          await Promise.race([
            resolveOperationalIdentity(backendRole),
            new Promise<never>((_, reject) =>
              setTimeout(() => reject(new Error('Identity resolution timed out')), 5000),
            ),
          ]);
        } catch (error) {
          console.warn('Operational identity hydration deferred', error);
        }
      })();
    };

    void supabase.auth.getSession().then(({ data: { session: initialSession } }) => {
      void applySession(initialSession);
    });

    const {
      data: { subscription },
    } = supabase.auth.onAuthStateChange((_event, nextSession) => {
      void applySession(nextSession);
    });

    return () => {
      subscription.unsubscribe();
    };
  }, []);

  const toggleActiveRole = () => {
    setActiveRole((previous) => {
      if (previous === 'PROVIDER' && captainId) return 'CAPTAIN';
      if (previous === 'CAPTAIN' && providerId) return 'PROVIDER';
      return previous;
    });
  };

  const signOut = async () => {
    await stopCaptainLocationTracking();
    apiClient.setSessionToken(null);
    setProviderId(null);
    setCaptainId(null);
    await supabase.auth.signOut();
  };

  return (
    <AuthContext.Provider
      value={{
        user,
        session,
        role,
        activeRole,
        providerId,
        captainId,
        toggleActiveRole,
        loading,
        signOut,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
};

export const useAuth = () => useContext(AuthContext);
