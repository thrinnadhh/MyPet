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
  const [phone, setPhone] = useState('');
  const [otp, setOtp] = useState('');
  const [showOtpField, setShowOtpField] = useState(false);
  const [loading, setLoading] = useState(false);

  const handleSendOtp = useCallback(async () => {
    if (!phone.trim()) {
      Alert.alert(t('common.error'), t('login.fillPhone'));
      return;
    }

    let normalizedPhone = phone.trim().replace(/[\s-()]/g, '');
    if (!normalizedPhone.startsWith('+')) {
      normalizedPhone = `+91${normalizedPhone}`;
    }

    setLoading(true);

    try {
      const { error } = await supabase.auth.signInWithOtp({
        phone: normalizedPhone,
        options: {
          channel: 'sms',
        }
      });
      if (error) throw error;
      Alert.alert(t('common.success'), t('login.otpSent'));
      setShowOtpField(true);
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : t('login.somethingWrong');
      Alert.alert(t('login.authFailed'), message);
    } finally {
      setLoading(false);
    }
  }, [phone, t]);

  const handleVerifyOtp = useCallback(async () => {
    if (!phone.trim()) {
      Alert.alert(t('common.error'), t('login.fillPhone'));
      return;
    }
    if (!otp.trim()) {
      Alert.alert(t('common.error'), t('login.fillOtp'));
      return;
    }

    let normalizedPhone = phone.trim().replace(/[\s-()]/g, '');
    if (!normalizedPhone.startsWith('+')) {
      normalizedPhone = `+91${normalizedPhone}`;
    }

    setLoading(true);

    try {
      const { error } = await supabase.auth.verifyOtp({
        phone: normalizedPhone,
        token: otp.trim(),
        type: 'sms',
      });
      if (error) throw error;
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : t('login.somethingWrong');
      Alert.alert(t('login.authFailed'), message);
    } finally {
      setLoading(false);
    }
  }, [phone, otp, t]);

  return (
    <ThemedView style={styles.container}>
      <SafeAreaView style={styles.safeArea}>
        <View style={styles.inner}>
          <View style={[styles.hero, { backgroundColor: theme.primarySoft, borderColor: theme.border }]}>
            <View style={[styles.logoWrap, { backgroundColor: theme.primary }]}>
              <AppIcon name="paw" color="#FFFFFF" size={28} />
            </View>
            <ThemedText style={styles.brand}>{t('common.brand')}</ThemedText>
            <ThemedText type="small" themeColor="textSecondary" style={styles.tagline}>
              {t('login.taglineSignIn')}
            </ThemedText>
          </View>

          <AppCard>
            <View style={styles.form}>
            <TextField
              label={t('login.phone')}
              placeholder={t('login.phonePlaceholder')}
              value={phone}
              onChangeText={setPhone}
              keyboardType="phone-pad"
              autoCapitalize="none"
              autoCorrect={false}
              editable={!showOtpField}
              accessibilityLabel="Phone Number Input"
            />

            {showOtpField ? (
              <>
                <TextField
                  label={t('login.otp')}
                  placeholder={t('login.otpPlaceholder')}
                  value={otp}
                  onChangeText={setOtp}
                  keyboardType="number-pad"
                  autoCapitalize="none"
                  autoCorrect={false}
                  accessibilityLabel="OTP Code Input"
                />

                <PrimaryButton
                  label={t('login.verifyOtp')}
                  onPress={() => void handleVerifyOtp()}
                  loading={loading}
                  style={styles.submit}
                />

                <PrimaryButton
                  label={t('login.changePhone')}
                  onPress={() => {
                    setShowOtpField(false);
                    setOtp('');
                  }}
                  variant="ghost"
                />
              </>
            ) : (
              <PrimaryButton
                label={t('login.sendOtp')}
                onPress={() => void handleSendOtp()}
                loading={loading}
                style={styles.submit}
              />
            )}
            </View>
          </AppCard>
        </View>
      </SafeAreaView>
    </ThemedView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1 },
  safeArea: { flex: 1, justifyContent: 'center' },
  inner: {
    paddingHorizontal: Spacing.four,
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
    width: 64,
    height: 64,
    borderRadius: 32,
    alignItems: 'center',
    justifyContent: 'center',
  },
  brand: {
    fontSize: 28,
    fontWeight: '900',
  },
  tagline: {
    textAlign: 'center',
    lineHeight: 20,
  },
  submit: {
    marginTop: Spacing.two,
  },
  form: {
    gap: Spacing.two,
  },
});
