import { SymbolView } from 'expo-symbols';
import { Text, type StyleProp, type ViewStyle } from 'react-native';

export type AppIconName =
  | 'cart'
  | 'calendar'
  | 'clock'
  | 'location'
  | 'paw'
  | 'store'
  | 'medical'
  | 'sparkle'
  | 'star'
  | 'history';

const SYMBOLS: Record<AppIconName, { ios: string; android: string; fallback: string }> = {
  cart: { ios: 'cart.fill', android: 'shopping_cart', fallback: 'C' },
  calendar: { ios: 'calendar', android: 'calendar_month', fallback: 'D' },
  clock: { ios: 'clock.fill', android: 'schedule', fallback: 'T' },
  location: { ios: 'location.fill', android: 'location_on', fallback: 'L' },
  paw: { ios: 'pawprint.fill', android: 'pets', fallback: 'P' },
  store: { ios: 'storefront.fill', android: 'storefront', fallback: 'S' },
  medical: { ios: 'cross.case.fill', android: 'medical_services', fallback: 'M' },
  sparkle: { ios: 'sparkles', android: 'auto_awesome', fallback: '*' },
  star: { ios: 'star.fill', android: 'star', fallback: 'R' },
  history: { ios: 'clock.arrow.circlepath', android: 'history', fallback: 'H' },
};

export function AppIcon({
  name,
  color,
  size = 18,
  style,
}: {
  name: AppIconName;
  color: string;
  size?: number;
  style?: StyleProp<ViewStyle>;
}) {
  const symbol = SYMBOLS[name];

  return (
    <SymbolView
      name={{ ios: symbol.ios as never, android: symbol.android as never, web: symbol.android as never }}
      size={size}
      tintColor={color}
      style={style}
      fallback={<Text style={{ color, fontSize: size * 0.8, fontWeight: '800' }}>{symbol.fallback}</Text>}
    />
  );
}
