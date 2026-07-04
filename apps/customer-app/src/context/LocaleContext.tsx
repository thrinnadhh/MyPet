import React, { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';

import type { LanguageId } from '@/constants/content';
import { useAuth } from '@/context/AuthContext';
import i18n from '@/i18n';
import { fetchLocale, updateLocale } from '@/services/preferences';

interface LocaleContextValue {
  locale: LanguageId;
  changeLocale: (next: LanguageId) => Promise<void>;
  ready: boolean;
}

const LocaleContext = createContext<LocaleContextValue | null>(null);

export function LocaleProvider({ children }: { children: React.ReactNode }) {
  const { session } = useAuth();
  const [locale, setLocale] = useState<LanguageId>('en');
  const [ready, setReady] = useState(false);

  useEffect(() => {
    let cancelled = false;

    void fetchLocale(session?.access_token)
      .then(async (preferred) => {
        if (cancelled) return;
        const next = preferred === 'hi' ? 'hi' : 'en';
        setLocale(next);
        await i18n.changeLanguage(next);
        setReady(true);
      })
      .catch(async () => {
        if (cancelled) return;
        await i18n.changeLanguage('en');
        setReady(true);
      });

    return () => {
      cancelled = true;
    };
  }, [session?.access_token]);

  const changeLocale = useCallback(
    async (next: LanguageId) => {
      const supported = next === 'hi' ? 'hi' : 'en';
      setLocale(supported);
      await i18n.changeLanguage(supported);
      await updateLocale(supported, session?.access_token);
    },
    [session?.access_token],
  );

  const value = useMemo(
    () => ({ locale, changeLocale, ready }),
    [changeLocale, locale, ready],
  );

  return <LocaleContext.Provider value={value}>{children}</LocaleContext.Provider>;
}

export function useLocale(): LocaleContextValue {
  const context = useContext(LocaleContext);
  if (!context) {
    throw new Error('useLocale must be used within LocaleProvider');
  }
  return context;
}
