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
import { supabase } from '@/utils/supabase';

export default function LoginScreen() {
  const theme = useTheme();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [fullName, setFullName] = useState('');
  const [isSignUp, setIsSignUp] = useState(false);
  const [loading, setLoading] = useState(false);

  const handleAuth = useCallback(async () => {
    if (!email.trim() || !password.trim()) {
      Alert.alert('Error', 'Please fill in email and password.');
      return;
    }

    if (isSignUp && !fullName.trim()) {
      Alert.alert('Error', 'Please enter your full name.');
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
              role: 'CUSTOMER',
            },
          },
        });
        if (error) throw error;
        Alert.alert('Success', 'Verification email sent. Please check your inbox.');
      } else {
        const { error } = await supabase.auth.signInWithPassword({ email, password });
        if (error) throw error;
      }
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : 'Something went wrong.';
      Alert.alert('Authentication Failed', message);
    } finally {
      setLoading(false);
    }
  }, [email, password, fullName, isSignUp]);

  return (
    <ThemedView style={styles.container}>
      <SafeAreaView style={styles.safeArea}>
        <View style={styles.inner}>
          <View style={[styles.hero, { backgroundColor: theme.primarySoft, borderColor: theme.border }]}>
            <View style={[styles.logoWrap, { backgroundColor: theme.primary }]}>
              <AppIcon name="paw" color="#FFFFFF" size={28} />
            </View>
            <ThemedText style={styles.brand}>PawsNearMe</ThemedText>
            <ThemedText type="small" themeColor="textSecondary" style={styles.tagline}>
              {isSignUp ? 'Create your account and start caring for your pets nearby.' : 'Welcome back. Book services and chat with trusted merchants.'}
            </ThemedText>
          </View>

          <AppCard>
            <View style={styles.form}>
            {isSignUp ? (
              <TextField
                label="Full name"
                placeholder="Your name"
                value={fullName}
                onChangeText={setFullName}
                autoCapitalize="words"
                accessibilityLabel="Full Name Input"
              />
            ) : null}
            <TextField
              label="Email"
              placeholder="you@email.com"
              value={email}
              onChangeText={setEmail}
              keyboardType="email-address"
              autoCapitalize="none"
              autoCorrect={false}
              accessibilityLabel="Email Input"
            />
            <TextField
              label="Password"
              placeholder="Enter password"
              value={password}
              onChangeText={setPassword}
              secureTextEntry
              autoCapitalize="none"
              autoCorrect={false}
              accessibilityLabel="Password Input"
            />

            <PrimaryButton
              label={isSignUp ? 'Create account' : 'Log in'}
              onPress={() => void handleAuth()}
              loading={loading}
              style={styles.submit}
            />
            </View>
          </AppCard>

          <PrimaryButton
            label={isSignUp ? 'Already have an account? Log in' : "Don't have an account? Sign up"}
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
