import React, { createContext, useContext, useEffect, useState } from 'react';
import { supabase } from '../utils/supabase';
import { Session, User } from '@supabase/supabase-js';
import { appConfig } from '../utils/app-config';
import { syncAuthenticatedProfile } from '../utils/profile-sync';

interface AuthContextType {
  user: User | null;
  session: Session | null;
  role: string | null;
  loading: boolean;
  signOut: () => Promise<void>;
}

const AuthContext = createContext<AuthContextType>({
  user: null,
  session: null,
  role: null,
  loading: true,
  signOut: async () => {},
});

export const AuthProvider = ({ children }: { children: React.ReactNode }) => {
  const [user, setUser] = useState<User | null>(null);
  const [session, setSession] = useState<Session | null>(null);
  const [role, setRole] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (appConfig.allowDemoMode) {
      console.log("AuthProvider: Running in explicit demo mode");
      const mockUser = {
        id: 'd3b07384-d113-4e4e-9c8e-3d8e3d8e3d8e',
        email: 'dev@pawsnearme.com',
        app_metadata: { role: 'CUSTOMER' },
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
      setRole('CUSTOMER');
      setLoading(false);
      return;
    }

    const applySession = async (nextSession: Session | null) => {
      setSession(nextSession);
      setUser(nextSession?.user ?? null);
      setRole((nextSession?.user?.app_metadata?.role as string) || 'CUSTOMER');
      if (nextSession) {
        try {
          await syncAuthenticatedProfile(nextSession, 'CUSTOMER');
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

  const signOut = async () => {
    await supabase.auth.signOut();
  };

  return (
    <AuthContext.Provider value={{ user, session, role, loading, signOut }}>
      {children}
    </AuthContext.Provider>
  );
};

export const useAuth = () => useContext(AuthContext);
