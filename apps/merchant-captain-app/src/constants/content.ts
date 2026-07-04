export const LANGUAGES = [
  { id: 'en', label: 'English', region: 'Default' },
  { id: 'hi', label: 'हिन्दी', region: 'North & Central India' },
  { id: 'te', label: 'తెలుగు', region: 'Telangana & Andhra' },
] as const;

export type LanguageId = (typeof LANGUAGES)[number]['id'];
