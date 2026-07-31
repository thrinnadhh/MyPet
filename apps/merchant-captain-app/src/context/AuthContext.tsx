import React, { createContext, useContext, useEffect, useState } from 'react';
import { supabase } from '../utils/supabase';
import { Session, User } from '@supabase/supabase-js';
import { appConfig } from '../utils/app-config';
import { syncAuthenticatedProfile } from '../utils/profile-sync';

import { apiClient } from '../services/api-client';

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
  const [loading, setLoading] = useState(true);

  const providerId = user?.id ?? null;
  const captainId = user?.id ?? null;

  useEffect(() => {
    if (appConfig.allowDemoMode) {
      console.log("AuthProvider: Running in explicit demo mode");
      const mockUser = {
        id: 'd3b07384-d113-4e4e-9c8e-3d8e3d8e3d8e', // Merchant ID
        email: 'merchant@pawsnearme.com',
        app_metadata: { role: 'MERCHANT' },
        user_metadata: {},
        aud: 'authenticated',
        created_at: new Date().toISOString()
      } as User;
      
      const mockSession = {
        access_token: 'mock-jwt-token-for-dev-purposes-only',
        token_type: 'bearer',
        expires_in: 3600,
        refresh_token: 'mock-refresh-token',
        user: mockUser
      } as Session;

      setSession(mockSession);
      setUser(mockUser);
      setRole('MERCHANT');
      setActiveRole('PROVIDER');
      apiClient.setSessionToken(mockSession.access_token);
      apiClient.setUserContext(mockUser.id, 'MERCHANT');
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

    const applySession = async (nextSession: Session | null) => {
      setSession(nextSession);
      setUser(nextSession?.user ?? null);
      const backendRole = normalizeBackendRole(nextSession?.user?.app_metadata?.role as string | undefined);
      setRole(backendRole);
      setActiveRole(resolveActiveRole(backendRole));
      
      apiClient.setSessionToken(nextSession?.access_token ?? null);
      apiClient.setUserContext(nextSession?.user?.id ?? null, backendRole);

      if (nextSession) {
        try {
          await syncAuthenticatedProfile(nextSession, backendRole as 'MERCHANT' | 'CAPTAIN' | 'ADMIN');
        } catch (error) {
          console.warn('Profile sync failed', error);
        }
      }
      setLoading(false);
    };

    // Standard Supabase Session listener
    supabase.auth.getSession().then(({ data: { session } }) => {
      void applySession(session);
    });

    const { data: { subscription } } = supabase.auth.onAuthStateChange((_event, session) => {
      void applySession(session);
    });

    return () => {
      subscription.unsubscribe();
    };
  }, []);

  const toggleActiveRole = () => {
    setActiveRole((prev) => (prev === 'PROVIDER' ? 'CAPTAIN' : 'PROVIDER'));
  };

  const signOut = async () => {
    apiClient.setSessionToken(null);
    apiClient.setUserContext(null, null);
    await supabase.auth.signOut();
  };

  return (
    <AuthContext.Provider value={{ user, session, role, activeRole, providerId, captainId, toggleActiveRole, loading, signOut }}>
      {children}
    </AuthContext.Provider>
  );
};

export const useAuth = () => useContext(AuthContext);
