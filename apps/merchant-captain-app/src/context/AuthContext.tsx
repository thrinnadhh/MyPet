import React, { createContext, useContext, useEffect, useState } from 'react';
import { supabase } from '../utils/supabase';
import { Session, User } from '@supabase/supabase-js';

interface AuthContextType {
  user: User | null;
  session: Session | null;
  role: string | null;
  activeRole: string | null;
  toggleActiveRole: () => void;
  loading: boolean;
  signOut: () => Promise<void>;
}

const AuthContext = createContext<AuthContextType>({
  user: null,
  session: null,
  role: null,
  activeRole: null,
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

  useEffect(() => {
    // Check if we are in development placeholder mode
    const isPlaceholder = process.env.EXPO_PUBLIC_SUPABASE_URL === undefined || 
                          process.env.EXPO_PUBLIC_SUPABASE_URL.includes("placeholder-project");
    
    if (isPlaceholder) {
      console.log("AuthProvider: Running in mock development mode");
      const mockUser = {
        id: 'd3b07384-d113-4e4e-9c8e-3d8e3d8e3d8e', // Merchant ID
        email: 'merchant@pawsnearme.com',
        app_metadata: { role: 'PROVIDER' },
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
      setRole('PROVIDER');
      setActiveRole('PROVIDER');
      setLoading(false);
      return;
    }

    // Standard Supabase Session listener
    supabase.auth.getSession().then(({ data: { session } }) => {
      setSession(session);
      setUser(session?.user ?? null);
      const userRole = (session?.user?.app_metadata?.role as string) || 'PROVIDER';
      setRole(userRole);
      setActiveRole(userRole);
      setLoading(false);
    });

    const { data: { subscription } } = supabase.auth.onAuthStateChange((_event, session) => {
      setSession(session);
      setUser(session?.user ?? null);
      const userRole = (session?.user?.app_metadata?.role as string) || 'PROVIDER';
      setRole(userRole);
      setActiveRole(userRole);
      setLoading(false);
    });

    return () => {
      subscription.unsubscribe();
    };
  }, []);

  const toggleActiveRole = () => {
    setActiveRole((prev) => (prev === 'PROVIDER' ? 'CAPTAIN' : 'PROVIDER'));
  };

  const signOut = async () => {
    await supabase.auth.signOut();
  };

  return (
    <AuthContext.Provider value={{ user, session, role, activeRole, toggleActiveRole, loading, signOut }}>
      {children}
    </AuthContext.Provider>
  );
};

export const useAuth = () => useContext(AuthContext);
