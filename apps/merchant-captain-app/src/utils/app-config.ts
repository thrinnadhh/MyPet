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
const configuredPrivacyPolicyUrl = process.env.EXPO_PUBLIC_PRIVACY_POLICY_URL?.trim();
const configuredAccountDeletionUrl = process.env.EXPO_PUBLIC_ACCOUNT_DELETION_URL?.trim();
const appVariant: OperationalAppVariant = configuredVariant === 'merchant' ? 'merchant' : 'operations';
const demoRole = configuredDemoRole === 'ADMIN' || configuredDemoRole === 'CAPTAIN'
  ? configuredDemoRole
  : 'MERCHANT';

export const appConfig = {
  apiBaseUrl: configuredApiBaseUrl || (__DEV__ ? defaultGatewayUrl : ''),
  supabaseUrl: process.env.EXPO_PUBLIC_SUPABASE_URL,
  supabaseAnonKey: process.env.EXPO_PUBLIC_SUPABASE_ANON_KEY,
  privacyPolicyUrl: configuredPrivacyPolicyUrl,
  accountDeletionUrl: configuredAccountDeletionUrl,
  allowDemoMode,
  demoRole,
  appVariant,
  isMerchantBuild: appVariant === 'merchant',
  isOperationsBuild: appVariant === 'operations',
};

function isHttpsUrl(value: string | undefined): boolean {
  if (!value) return false;
  try {
    return new URL(value).protocol === 'https:';
  } catch {
    return false;
  }
}

export function requireMobileConfig() {
  const missing = [
    appConfig.apiBaseUrl ? null : 'EXPO_PUBLIC_API_BASE_URL',
    appConfig.supabaseUrl ? null : 'EXPO_PUBLIC_SUPABASE_URL',
    appConfig.supabaseAnonKey ? null : 'EXPO_PUBLIC_SUPABASE_ANON_KEY',
    !__DEV__ && appConfig.isMerchantBuild && !appConfig.privacyPolicyUrl
      ? 'EXPO_PUBLIC_PRIVACY_POLICY_URL'
      : null,
    !__DEV__ && appConfig.isMerchantBuild && !appConfig.accountDeletionUrl
      ? 'EXPO_PUBLIC_ACCOUNT_DELETION_URL'
      : null,
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
  if (!__DEV__ && appConfig.isMerchantBuild && !isHttpsUrl(appConfig.privacyPolicyUrl)) {
    throw new Error('EXPO_PUBLIC_PRIVACY_POLICY_URL must be a valid HTTPS URL in Merchant production builds.');
  }
  if (!__DEV__ && appConfig.isMerchantBuild && !isHttpsUrl(appConfig.accountDeletionUrl)) {
    throw new Error('EXPO_PUBLIC_ACCOUNT_DELETION_URL must be a valid HTTPS URL in Merchant production builds.');
  }
  if (!__DEV__ && appConfig.allowDemoMode) {
    throw new Error('Demo mode is forbidden in production builds.');
  }
}
