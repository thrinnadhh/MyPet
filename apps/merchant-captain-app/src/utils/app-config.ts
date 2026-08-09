import { Platform } from 'react-native';

const isTruthy = (value: string | undefined) => value === 'true' || value === '1';

export type OperationalAppVariant = 'merchant' | 'operations';

const defaultGatewayUrl = Platform.select({
  android: 'http://10.0.2.2:8080',
  ios: 'http://localhost:8080',
  default: 'http://localhost:8080',
}) ?? 'http://localhost:8080';
const allowDemoMode = __DEV__ && isTruthy(process.env.EXPO_PUBLIC_ALLOW_DEMO_MODE);
const configuredApiBaseUrl = process.env.EXPO_PUBLIC_API_BASE_URL?.trim().replace(/\/+$/, '');
const configuredDemoRole = process.env.EXPO_PUBLIC_DEMO_ROLE?.trim().toUpperCase();
const configuredVariant = process.env.EXPO_PUBLIC_APP_VARIANT?.trim().toLowerCase();
const appVariant: OperationalAppVariant = configuredVariant === 'merchant' ? 'merchant' : 'operations';
const demoRole = configuredDemoRole === 'ADMIN' || configuredDemoRole === 'CAPTAIN'
  ? configuredDemoRole
  : 'MERCHANT';

export const appConfig = {
  apiBaseUrl: configuredApiBaseUrl || (__DEV__ ? defaultGatewayUrl : ''),
  supabaseUrl: process.env.EXPO_PUBLIC_SUPABASE_URL,
  supabaseAnonKey: process.env.EXPO_PUBLIC_SUPABASE_ANON_KEY,
  allowDemoMode,
  demoRole,
  appVariant,
  isMerchantBuild: appVariant === 'merchant',
  isOperationsBuild: appVariant === 'operations',
};

export function requireMobileConfig() {
  const missing = [
    appConfig.apiBaseUrl ? null : 'EXPO_PUBLIC_API_BASE_URL',
    appConfig.supabaseUrl ? null : 'EXPO_PUBLIC_SUPABASE_URL',
    appConfig.supabaseAnonKey ? null : 'EXPO_PUBLIC_SUPABASE_ANON_KEY',
  ].filter(Boolean);

  if (missing.length > 0 && !appConfig.allowDemoMode) {
    throw new Error(
      `Missing mobile configuration: ${missing.join(', ')}. ` +
      'Set EXPO_PUBLIC_ALLOW_DEMO_MODE=true only in a development build with local demo fixtures.'
    );
  }
  if (!__DEV__ && !appConfig.apiBaseUrl.startsWith('https://')) {
    throw new Error('EXPO_PUBLIC_API_BASE_URL must use HTTPS in production builds.');
  }
  if (!__DEV__ && appConfig.allowDemoMode) {
    throw new Error('Demo mode is forbidden in production builds.');
  }
}
