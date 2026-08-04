import React, { useCallback, useState } from 'react';
import { Alert, StyleSheet, View } from 'react-native';

import { AppIcon } from '@/components/app-icon';
import { FeedbackBanner, FilterChip } from '@/components/foundation/primitives';
import { ScreenShell } from '@/components/foundation/screen-shell';
import { ThemedText } from '@/components/themed-text';
import { AppCard } from '@/components/ui/app-card';
import { PrimaryButton } from '@/components/ui/primary-button';
import { TextField } from '@/components/ui/text-field';
import { Radius, Spacing } from '@/constants/theme';
import { palette } from '@/design/tokens';
import { useTheme } from '@/hooks/use-theme';
import { useTranslation } from '@/i18n';
import { supabase } from '@/utils/supabase';

type SignupRole = 'MERCHANT' | 'CAPTAIN';

const AUTH_TIMEOUT_MS = 15000;

function withAuthTimeout<T>(operation: Promise<T>): Promise<T> {
  return new Promise<T>((resolve, reject) => {
    const timer = setTimeout(() => {
      reject(new Error('Authentication timed out. Check the phone internet connection and try again.'));
    }, AUTH_TIMEOUT_MS);

    operation.then(
      (value) => {
        clearTimeout(timer);
        resolve(value);
      },
      (error) => {
        clearTimeout(timer);
        reject(error);
      },
    );
  });
}

function friendlyAuthError(error: unknown, fallback: string): string {
  const message = error instanceof Error ? error.message : fallback;
  if (/email not confirmed/i.test(message)) {
    return 'Your email is not verified. Open the Supabase verification email, verify the account, then log in again.';
  }
  if (/invalid login credentials/i.test(message)) {
    return 'Incorrect email or password. Confirm that this merchant account belongs to the same Supabase project.';
  }
  if (/network request failed|failed to fetch/i.test(message)) {
    return 'The phone could not reach Supabase. Check Wi-Fi/mobile data, VPN, and EXPO_PUBLIC_SUPABASE_URL.';
  }
  return message;
}

export default function LoginScreen() {
  const theme = useTheme();
  const { t } = useTranslation();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [fullName, setFullName] = useState('');
  const [isSignUp, setIsSignUp] = useState(false);
  const [signupRole, setSignupRole] = useState<SignupRole>('MERCHANT');
  const [loading, setLoading] = useState(false);

  const handleAuth = useCallback(async () => {
    if (!email.trim() || !password.trim()) {
      Alert.alert(t('common.error'), t('login.fillEmailPassword'));
      return;
    }

    if (isSignUp && !fullName.trim()) {
      Alert.alert(t('common.error'), t('login.enterFullName'));
      return;
    }

    setLoading(true);

    try {
      if (isSignUp) {
        const { data, error } = await withAuthTimeout(supabase.auth.signUp({
          email: email.trim(),
          password,
          options: {
            data: {
              full_name: fullName.trim(),
              role: signupRole,
            },
          },
        }));
        if (error) throw error;
        Alert.alert(
          t('common.success'),
          data.session
            ? 'Account created and signed in.'
            : 'Account created. Verify the email sent by Supabase, then log in.',
        );
      } else {
        const { error } = await withAuthTimeout(
          supabase.auth.signInWithPassword({ email: email.trim().toLowerCase(), password }),
        );
        if (error) throw error;
      }
    } catch (err: unknown) {
      Alert.alert(
        t('login.authFailed'),
        friendlyAuthError(err, t('login.somethingWrong')),
      );
    } finally {
      setLoading(false);
    }
  }, [email, password, fullName, isSignUp, signupRole, t]);

  const selectedRoleHint = signupRole === 'MERCHANT' ? t('login.merchantHint') : t('login.captainHint');

  return (
    <ScreenShell scroll={false} contentContainerStyle={styles.screen} testID="operational-login">
      <View style={styles.inner}>
        <View style={[styles.hero, { backgroundColor: theme.primarySoft, borderColor: theme.border }]}>
          <View style={[styles.logoWrap, { backgroundColor: theme.primary }]}>
            <AppIcon name={signupRole === 'CAPTAIN' ? 'truck' : 'store'} color={palette.white} size={30} />
          </View>
          <ThemedText style={styles.brand}>{t('login.brand')}</ThemedText>
          <ThemedText type="small" themeColor="textSecondary" style={styles.tagline}>
            {isSignUp ? t('login.taglineSignUp') : t('login.taglineSignIn')}
          </ThemedText>
        </View>

        <AppCard style={styles.card}>
          <View style={styles.form}>
            {isSignUp ? (
              <>
                <ThemedText type="smallBold" themeColor="textSecondary">
                  {t('login.accountType')}
                </ThemedText>
                <View style={styles.roleRow}>
                  <FilterChip
                    label={t('login.merchant')}
                    selected={signupRole === 'MERCHANT'}
                    icon="store"
                    onPress={() => setSignupRole('MERCHANT')}
                  />
                  <FilterChip
                    label={t('login.captain')}
                    selected={signupRole === 'CAPTAIN'}
                    icon="truck"
                    onPress={() => setSignupRole('CAPTAIN')}
                  />
                </View>
                <FeedbackBanner
                  title={signupRole === 'MERCHANT' ? t('login.merchant') : t('login.captain')}
                  message={selectedRoleHint}
                  tone="info"
                  icon={signupRole === 'MERCHANT' ? 'store' : 'truck'}
                />
                <TextField
                  label={signupRole === 'MERCHANT' ? t('login.businessContactName') : t('login.captainFullName')}
                  placeholder={t('login.namePlaceholder')}
                  value={fullName}
                  onChangeText={setFullName}
                  autoCapitalize="words"
                  textContentType="name"
                />
              </>
            ) : null}

            <TextField
              label={t('login.email')}
              placeholder={t('login.emailPlaceholder')}
              value={email}
              onChangeText={setEmail}
              keyboardType="email-address"
              autoCapitalize="none"
              autoCorrect={false}
              textContentType="emailAddress"
            />
            <TextField
              label={t('login.password')}
              placeholder={t('login.passwordPlaceholder')}
              value={password}
              onChangeText={setPassword}
              secureTextEntry
              autoCapitalize="none"
              autoCorrect={false}
              textContentType={isSignUp ? 'newPassword' : 'password'}
              returnKeyType="done"
              onSubmitEditing={() => void handleAuth()}
            />
            <PrimaryButton
              label={
                isSignUp
                  ? signupRole === 'MERCHANT'
                    ? t('login.registerBusiness')
                    : t('login.registerCaptain')
                  : t('login.logIn')
              }
              onPress={() => void handleAuth()}
              loading={loading}
              style={styles.submit}
            />
          </View>
        </AppCard>

        <PrimaryButton
          label={isSignUp ? t('login.toggleToSignIn') : t('login.toggleToSignUp')}
          onPress={() => setIsSignUp((previous) => !previous)}
          variant="ghost"
        />
      </View>
    </ScreenShell>
  );
}

const styles = StyleSheet.create({
  screen: {
    justifyContent: 'center',
    paddingHorizontal: Spacing.four,
    paddingVertical: Spacing.four,
  },
  inner: {
    width: '100%',
    maxWidth: 560,
    alignSelf: 'center',
    gap: Spacing.three,
  },
  hero: {
    borderWidth: 1,
    borderRadius: Radius.xl,
    padding: Spacing.four,
    alignItems: 'center',
    gap: Spacing.two,
  },
  logoWrap: {
    width: 68,
    height: 68,
    borderRadius: 34,
    alignItems: 'center',
    justifyContent: 'center',
  },
  brand: { fontSize: 26, fontWeight: '900', textAlign: 'center' },
  tagline: { textAlign: 'center', lineHeight: 20, maxWidth: 440 },
  card: { padding: Spacing.four },
  form: { gap: Spacing.three },
  roleRow: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: Spacing.two,
  },
  submit: { marginTop: Spacing.one },
});
