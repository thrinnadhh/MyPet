/**
 * Platform-independent MyPet design contract.
 *
 * Keep values that must be testable in plain Node here. React Native-specific
 * typography and shadow implementations belong in `tokens.ts`.
 */
export const palette = {
  royalBlue: '#004AC6',
  royalBlueBright: '#2563EB',
  royalBlueSoft: '#DBE1FF',
  amber: '#FEA619',
  amberSoft: '#FFDDB8',
  emerald: '#10B981',
  emeraldSoft: '#D1FAE5',
  captainTeal: '#0F766E',
  captainTealSoft: '#CCFBF1',
  merchantOrange: '#C2410C',
  merchantOrangeSoft: '#FFEDD5',
  coolWhite: '#F8F9FF',
  white: '#FFFFFF',
  ink: '#0B1C30',
  inkMuted: '#434655',
  outline: '#C3C6D7',
  error: '#BA1A1A',
  errorSoft: '#FFDAD6',
  darkSurface: '#111D2C',
  darkCard: '#1B293A',
  darkInk: '#EAF1FF',
  darkMuted: '#C3C6D7',
} as const;

export const spacing = {
  x1: 4,
  x2: 8,
  x3: 12,
  x4: 16,
  x5: 20,
  x6: 24,
  x8: 32,
  x12: 48,
} as const;

export const radii = {
  compact: 8,
  control: 12,
  card: 16,
  feature: 24,
  pill: 999,
} as const;

export const touchTarget = 48;

export type MyPetScheme = 'light' | 'dark';
export type OperationalRole = 'merchant' | 'captain' | 'admin';

export function roleAccent(role: OperationalRole, scheme: MyPetScheme) {
  if (scheme === 'dark') {
    if (role === 'captain') return { accent: '#5EEAD4', accentSoft: '#134E4A' };
    if (role === 'merchant') return { accent: '#FDBA74', accentSoft: '#7C2D12' };
    return { accent: '#B4C5FF', accentSoft: '#263D70' };
  }

  if (role === 'captain') return { accent: palette.captainTeal, accentSoft: palette.captainTealSoft };
  if (role === 'merchant') return { accent: palette.merchantOrange, accentSoft: palette.merchantOrangeSoft };
  return { accent: palette.royalBlue, accentSoft: palette.royalBlueSoft };
}
