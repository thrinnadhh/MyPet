import * as Localization from 'expo-localization';
import i18n from 'i18next';
import { initReactI18next } from 'react-i18next';

import en from './en.json';
import hi from './hi.json';

const deviceLang = Localization.getLocales()[0]?.languageCode ?? 'en';
const initialLanguage = deviceLang === 'hi' ? 'hi' : 'en';

void i18n.use(initReactI18next).init({
  compatibilityJSON: 'v4',
  resources: {
    en: { translation: en },
    hi: { translation: hi },
  },
  lng: initialLanguage,
  fallbackLng: 'en',
  interpolation: { escapeValue: false },
});

export { useTranslation } from 'react-i18next';
export default i18n;

export function t(key: string, options?: Record<string, unknown>): string {
  return i18n.t(key, options);
}
