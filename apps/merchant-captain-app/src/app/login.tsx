import React, { useState, useCallback } from 'react';
import { Alert, StyleSheet, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { AppIcon } from '@/components/app-icon';
import { AppCard } from '@/components/ui/app-card';
import { PrimaryButton } from '@/components/ui/primary-button';
import { TextField } from '@/components/ui/text-field';
import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { Radius, Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';
import { useTranslation } from '@/i18n';
import { supabase } from '@/utils/supabase';

export default function LoginScreen() {
  const theme = useTheme();
  const { t } = useTranslation();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [fullName, setFullName] = useState('');
  const [isSignUp, setIsSignUp] = useState(false);
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
        const { error } = await supabase.auth.signUp({
          email,
          password,
          options: {
            data: {
              full_name: fullName,
              role: 'MERCHANT',
            },
          },
        });
        if (error) throw error;
        Alert.alert(t('common.success'), t('login.verificationSent'));
      } else {
        const { error } = await supabase.auth.signInWithPassword({ email, password });
        if (error) throw error;
      }
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : t('login.somethingWrong');
      Alert.alert(t('login.authFailed'), message);
    } finally {
      setLoading(false);
    }
  }, [email, password, fullName, isSignUp, t]);

  return (
    <ThemedView style={styles.container}>
      <SafeAreaView style={styles.safeArea}>
        <View style={styles.inner}>
          <View style={[styles.hero, { backgroundColor: theme.ctaSoft, borderColor: theme.border }]}>
            <View style={[styles.logoWrap, { backgroundColor: theme.cta }]}>
              <AppIcon name="store" color="#FFFFFF" size={28} />
            </View>
            <ThemedText style={styles.brand}>{t('login.brand')}</ThemedText>
            <ThemedText type="small" themeColor="textSecondary" style={styles.tagline}>
              {isSignUp ? t('login.taglineSignUp') : t('login.taglineSignIn')}
            </ThemedText>
          </View>

          <AppCard>
            <View style={styles.form}>
              {isSignUp ? (
                <TextField
                  label={t('login.businessContactName')}
                  placeholder={t('login.namePlaceholder')}
                  value={fullName}
                  onChangeText={setFullName}
                  autoCapitalize="words"
                />
              ) : null}
              <TextField
                label={t('login.email')}
                placeholder={t('login.emailPlaceholder')}
                value={email}
                onChangeText={setEmail}
                keyboardType="email-address"
                autoCapitalize="none"
                autoCorrect={false}
              />
              <TextField
                label={t('login.password')}
                placeholder={t('login.passwordPlaceholder')}
                value={password}
                onChangeText={setPassword}
                secureTextEntry
                autoCapitalize="none"
                autoCorrect={false}
              />
              <PrimaryButton
                label={isSignUp ? t('login.registerBusiness') : t('login.logIn')}
                onPress={() => void handleAuth()}
                loading={loading}
                variant="secondary"
                style={styles.submit}
              />
            </View>
          </AppCard>

          <PrimaryButton
            label={isSignUp ? t('login.toggleToSignIn') : t('login.toggleToSignUp')}
            onPress={() => setIsSignUp((prev) => !prev)}
            variant="ghost"
          />
        </View>
      </SafeAreaView>
    </ThemedView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1 },
  safeArea: { flex: 1, justifyContent: 'center' },
  inner: { paddingHorizontal: Spacing.four, gap: Spacing.three },
  hero: {
    borderWidth: 1,
    borderRadius: Radius.xl,
    padding: Spacing.four,
    alignItems: 'center',
    gap: Spacing.two,
  },
  logoWrap: {
    width: 64,
    height: 64,
    borderRadius: 32,
    alignItems: 'center',
    justifyContent: 'center',
  },
  brand: { fontSize: 26, fontWeight: '900', textAlign: 'center' },
  tagline: { textAlign: 'center', lineHeight: 20 },
  form: { gap: Spacing.two },
  submit: { marginTop: Spacing.two },
});
