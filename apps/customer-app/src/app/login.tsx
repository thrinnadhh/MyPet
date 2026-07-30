import { useLocalSearchParams, useRouter } from 'expo-router';
import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { Pressable, StyleSheet, TextInput, View } from 'react-native';

import { parseAuthIntent } from '@/auth/auth-intent';
import { type OtpChannel, OtpAuthError, resendOtp, sendOtp, verifyOtp } from '@/auth/otp-auth';
import { AppBar, FilterChip, PrimaryAction } from '@/components/foundation/primitives';
import { ScreenShell } from '@/components/foundation/screen-shell';
import { ThemedText } from '@/components/themed-text';
import { useAuth } from '@/context/AuthContext';
import { useAuthIntent } from '@/context/AuthIntentContext';
import { radii, spacing, touchTarget, typography } from '@/design/tokens';
import { useTheme } from '@/hooks/use-theme';
import { useTranslation } from '@/i18n';
import { appConfig } from '@/utils/app-config';

import { supabase } from '@/utils/supabase';

type Step = 'identifier' | 'code' | 'name';
const RESEND_SECONDS = 30;

export default function LoginScreen() {
  const params = useLocalSearchParams<{ intent?: string; fresh?: string }>();
  const parsedIntent = useMemo(() => parseAuthIntent(params.intent), [params.intent]);
  const fresh = params.fresh === '1';
  const router = useRouter();
  const theme = useTheme();
  const { t } = useTranslation();
  const { markOtpVerified } = useAuth();
  const { clearPendingIntent, resumePendingIntent } = useAuthIntent();
  const [step, setStep] = useState<Step>('identifier');
  const [channel, setChannel] = useState<OtpChannel>('phone');
  const [identifierInput, setIdentifierInput] = useState('');
  const [identifier, setIdentifier] = useState('');
  const [code, setCode] = useState('');
  const [displayName, setDisplayName] = useState('');
  const [seconds, setSeconds] = useState(0);
  const [loading, setLoading] = useState(false);
  const [errorCode, setErrorCode] = useState<string | null>(null);

  useEffect(() => {
    if (seconds <= 0) return;
    const timer = setInterval(() => setSeconds((current) => Math.max(0, current - 1)), 1000);
    return () => clearInterval(timer);
  }, [seconds]);

  const errorMessage = useMemo(() => {
    if (!errorCode) return null;
    const key: Record<string, string> = {
      INVALID_INPUT: 'auth.invalidInput', INVALID_CODE: 'auth.invalidCode', EXPIRED_CODE: 'auth.expiredCode',
      RATE_LIMITED: 'auth.rateLimited', NETWORK: 'auth.network', UNKNOWN: 'auth.unknown',
    };
    return t(key[errorCode] ?? 'auth.unknown');
  }, [errorCode, t]);

  const run = useCallback(async (operation: () => Promise<void>) => {
    setLoading(true);
    setErrorCode(null);
    try { await operation(); }
    catch (error) { setErrorCode(error instanceof OtpAuthError ? error.code : 'UNKNOWN'); }
    finally { setLoading(false); }
  }, []);

  const send = useCallback(() => run(async () => {
    const normalized = await sendOtp(channel, identifierInput);
    setIdentifier(normalized);
    setCode('');
    setSeconds(RESEND_SECONDS);
    setStep('code');
  }), [channel, identifierInput, run]);

  const finish = useCallback(async () => {
    await resumePendingIntent(parsedIntent);
  }, [parsedIntent, resumePendingIntent]);

  const verify = useCallback(() => run(async () => {
    const session = await verifyOtp(channel, identifier, code);
    markOtpVerified();
    const name = typeof session.user.user_metadata?.full_name === 'string' ? session.user.user_metadata.full_name.trim() : '';
    if (!name) setStep('name');
    else await finish();
  }), [channel, code, finish, identifier, markOtpVerified, run]);

  const saveName = useCallback(() => run(async () => {
    const name = displayName.trim();
    if (name.length < 2) throw new OtpAuthError('INVALID_INPUT', 'Display name is required.');
    const { error } = await supabase.auth.updateUser({ data: { full_name: name, role: 'CUSTOMER' } });
    if (error) throw error;
    await finish();
  }), [displayName, finish, run]);

  const resend = useCallback(() => run(async () => {
    if (seconds > 0) return;
    await resendOtp(channel, identifier);
    setSeconds(RESEND_SECONDS);
  }), [channel, identifier, run, seconds]);

  const reset = useCallback(() => {
    setStep('identifier'); setIdentifier(''); setIdentifierInput(''); setCode(''); setErrorCode(null); setSeconds(0);
  }, []);

  const cancel = useCallback(() => {
    clearPendingIntent();
    if (router.canGoBack()) router.back(); else router.replace('/(tabs)/home' as never);
  }, [clearPendingIntent, router]);

  return (
    <ScreenShell header={<AppBar title={t(fresh ? 'auth.freshTitle' : 'auth.title')} subtitle={t('auth.subtitle')} />} testID="otp-auth-screen">
      {step === 'identifier' ? (
        <View style={styles.stack}>
          <View style={styles.row}>
            <FilterChip label={t('auth.phone')} selected={channel === 'phone'} onPress={() => setChannel('phone')} />
            <FilterChip label={t('auth.email')} selected={channel === 'email'} onPress={() => setChannel('email')} />
          </View>
          <TextInput
            value={identifierInput}
            onChangeText={setIdentifierInput}
            placeholder={t(channel === 'phone' ? 'auth.phonePlaceholder' : 'auth.emailPlaceholder')}
            placeholderTextColor={theme.textSecondary}
            keyboardType={channel === 'phone' ? 'phone-pad' : 'email-address'}
            autoCapitalize="none"
            autoCorrect={false}
            style={[styles.input, { color: theme.text, backgroundColor: theme.backgroundElement, borderColor: theme.border }]}
            accessibilityLabel={t(channel === 'phone' ? 'auth.phonePlaceholder' : 'auth.emailPlaceholder')}
          />
          <PrimaryAction label={t('auth.sendCode')} onPress={() => void send()} loading={loading} />
        </View>
      ) : null}


      {step === 'code' ? (
        <View style={styles.stack}>
          <ThemedText style={styles.heading}>{t('auth.verifyTitle')}</ThemedText>
          <ThemedText themeColor="textSecondary">{t('auth.verifySubtitle', { identifier })}</ThemedText>
          <TextInput
            value={code}
            onChangeText={(value) => setCode(value.replace(/\D/g, '').slice(0, 6))}
            placeholder={t('auth.codePlaceholder')}
            placeholderTextColor={theme.textSecondary}
            keyboardType="number-pad"
            textContentType="oneTimeCode"
            maxLength={6}
            style={[styles.input, styles.code, { color: theme.text, backgroundColor: theme.backgroundElement, borderColor: theme.border }]}
            accessibilityLabel={t('auth.codePlaceholder')}
          />
          <PrimaryAction label={t('auth.verify')} onPress={() => void verify()} loading={loading} disabled={code.length !== 6} />
          <Pressable style={styles.link} disabled={seconds > 0 || loading} onPress={() => void resend()} accessibilityRole="button">
            <ThemedText style={{ color: seconds > 0 ? theme.textSecondary : theme.primary, fontWeight: '700' }}>{seconds > 0 ? t('auth.resendIn', { seconds }) : t('auth.resend')}</ThemedText>
          </Pressable>
          <Pressable style={styles.link} onPress={reset} accessibilityRole="button"><ThemedText style={{ color: theme.primary }}>{t('auth.changeIdentifier')}</ThemedText></Pressable>
        </View>
      ) : null}

      {step === 'name' ? (
        <View style={styles.stack}>
          <ThemedText style={styles.heading}>{t('auth.nameTitle')}</ThemedText>
          <ThemedText themeColor="textSecondary">{t('auth.nameSubtitle')}</ThemedText>
          <TextInput
            value={displayName}
            onChangeText={setDisplayName}
            placeholder={t('auth.namePlaceholder')}
            placeholderTextColor={theme.textSecondary}
            autoCapitalize="words"
            style={[styles.input, { color: theme.text, backgroundColor: theme.backgroundElement, borderColor: theme.border }]}
            accessibilityLabel={t('auth.namePlaceholder')}
          />
          <PrimaryAction label={t('auth.saveName')} onPress={() => void saveName()} loading={loading} disabled={displayName.trim().length < 2} />
        </View>
      ) : null}

      {errorMessage ? <View style={[styles.error, { backgroundColor: theme.errorSoft }]} accessibilityLiveRegion="assertive"><ThemedText style={{ color: theme.danger }}>{errorMessage}</ThemedText></View> : null}
      {errorCode ? <Pressable style={styles.link} onPress={reset} accessibilityRole="button"><ThemedText style={{ color: theme.primary }}>{t('auth.recovery')}</ThemedText></Pressable> : null}
      <Pressable style={styles.cancel} onPress={cancel} accessibilityRole="button"><ThemedText themeColor="textSecondary">{t('auth.cancel')}</ThemedText></Pressable>
    </ScreenShell>
  );
}

const styles = StyleSheet.create({
  stack: { gap: spacing.x4 },
  row: { flexDirection: 'row', gap: spacing.x2, flexWrap: 'wrap' },
  heading: { ...typography.title },
  input: { minHeight: touchTarget, borderWidth: 1, borderRadius: radii.compact, paddingHorizontal: spacing.x4, ...typography.body },
  code: { fontSize: 26, letterSpacing: 8, textAlign: 'center' },
  link: { minHeight: 44, alignItems: 'center', justifyContent: 'center', paddingHorizontal: spacing.x2 },
  error: { borderRadius: radii.compact, padding: spacing.x4 },
  cancel: { minHeight: 48, alignItems: 'center', justifyContent: 'center' },
});
