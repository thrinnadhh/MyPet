import React, { useState, useCallback } from 'react';
import { StyleSheet, View, TextInput, TouchableOpacity, Alert, ActivityIndicator, useColorScheme } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { supabase } from '../utils/supabase';
import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { Colors, Spacing } from '@/constants/theme';
import { AppIcon } from '@/components/app-icon';

export default function LoginScreen() {
  const scheme = useColorScheme() === 'dark' ? 'dark' : 'light';
  const colors = Colors[scheme];

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
        // Sign up flow as MERCHANT role
        const { error } = await supabase.auth.signUp({
          email,
          password,
          options: {
            data: {
              full_name: fullName,
              role: 'MERCHANT', // Backend authorization role for merchant operators
            },
          },
        });

        if (error) throw error;
        Alert.alert('Success', 'Verification email sent. Please check your inbox.');
      } else {
        // Sign in flow
        const { error } = await supabase.auth.signInWithPassword({
          email,
          password,
        });

        if (error) throw error;
      }
    } catch (err: any) {
      Alert.alert('Authentication Failed', err.message || 'Something went wrong.');
    } finally {
      setLoading(false);
    }
  }, [email, password, fullName, isSignUp]);

  const toggleMode = useCallback(() => {
    setIsSignUp((prev) => !prev);
  }, []);

  return (
    <ThemedView style={styles.container}>
      <SafeAreaView style={styles.safeArea}>
        <View style={styles.innerContainer}>
          <View style={styles.header}>
            <View style={styles.brandRow}>
              <AppIcon name="store" color={colors.primary} size={28} />
              <ThemedText type="subtitle" style={styles.logoText}>
                PawsNearMe Merchant
              </ThemedText>
            </View>
            <ThemedText type="small" style={{ color: colors.textSecondary, marginTop: Spacing.one }}>
              {isSignUp ? 'Register as a merchant to list your services' : 'Log in to manage your bookings and catalog'}
            </ThemedText>
          </View>

          <View style={styles.form}>
            {isSignUp && (
              <TextInput
                placeholder="Full Name *"
                placeholderTextColor={colors.textSecondary}
                style={[styles.input, { backgroundColor: colors.backgroundElement, color: colors.text }]}
                value={fullName}
                onChangeText={setFullName}
                autoCapitalize="words"
                accessibilityLabel="Full Name Input"
              />
            )}

            <TextInput
              placeholder="Email Address *"
              placeholderTextColor={colors.textSecondary}
              style={[styles.input, { backgroundColor: colors.backgroundElement, color: colors.text }]}
              value={email}
              onChangeText={setEmail}
              keyboardType="email-address"
              autoCapitalize="none"
              autoCorrect={false}
              accessibilityLabel="Email Input"
            />

            <TextInput
              placeholder="Password *"
              placeholderTextColor={colors.textSecondary}
              style={[styles.input, { backgroundColor: colors.backgroundElement, color: colors.text }]}
              value={password}
              onChangeText={setPassword}
              secureTextEntry
              autoCapitalize="none"
              autoCorrect={false}
              accessibilityLabel="Password Input"
            />

            <TouchableOpacity
              style={[styles.button, { backgroundColor: colors.primary }]}
              onPress={handleAuth}
              disabled={loading}
              activeOpacity={0.8}
              accessibilityLabel={isSignUp ? 'Register Button' : 'Login Button'}
            >
              {loading ? (
                <ActivityIndicator color="#ffffff" />
              ) : (
                <ThemedText style={styles.buttonText}>
                  {isSignUp ? 'Register Business' : 'Log In'}
                </ThemedText>
              )}
            </TouchableOpacity>
          </View>

          <TouchableOpacity
            style={styles.toggleButton}
            onPress={toggleMode}
            activeOpacity={0.7}
            accessibilityLabel="Toggle Auth Mode Button"
          >
            <ThemedText type="small" style={{ color: colors.primary }}>
              {isSignUp ? 'Already registered? Log in' : "New merchant? Register your business"}
            </ThemedText>
          </TouchableOpacity>
        </View>
      </SafeAreaView>
    </ThemedView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  safeArea: {
    flex: 1,
    justifyContent: 'center',
  },
  innerContainer: {
    paddingHorizontal: Spacing.four,
  },
  header: {
    marginBottom: Spacing.five,
    alignItems: 'center',
  },
  logoText: {
    fontWeight: 'bold',
  },
  brandRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: Spacing.two,
  },
  form: {
    gap: Spacing.two,
  },
  input: {
    height: 52,
    borderRadius: Spacing.two,
    paddingHorizontal: Spacing.three,
    fontSize: 15,
  },
  button: {
    height: 52,
    borderRadius: Spacing.two,
    justifyContent: 'center',
    alignItems: 'center',
    marginTop: Spacing.two,
  },
  buttonText: {
    color: '#ffffff',
    fontWeight: '700',
  },
  toggleButton: {
    marginTop: Spacing.four,
    alignItems: 'center',
    paddingVertical: Spacing.two,
  },
});
