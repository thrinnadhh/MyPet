import { DarkTheme, DefaultTheme, ThemeProvider } from 'expo-router';
import { ActivityIndicator, StyleSheet, View, useColorScheme } from 'react-native';

import { AnimatedSplashOverlay } from '@/components/animated-icon';
import AppTabs from '@/components/app-tabs';
import { ThemedText } from '@/components/themed-text';
import { AuthProvider, useAuth } from '@/context/AuthContext';
import { LocaleProvider } from '@/context/LocaleContext';
import { usePushNotifications } from '@/hooks/usePushNotifications';
import { useTheme } from '@/hooks/use-theme';
import '@/i18n';
import LoginScreen from './login';

function TabLayoutContent() {
  const colorScheme = useColorScheme();
  const theme = useTheme();
  const { user, loading, session, activeRole } = useAuth();
  usePushNotifications(user?.id, session?.access_token, activeRole);

  if (loading) {
    return (
      <View
        style={[styles.loading, { backgroundColor: theme.background }]}
        accessibilityLiveRegion="polite"
        accessibilityLabel="Restoring your MyPet session"
      >
        <ActivityIndicator size="large" color={theme.primary} />
        <ThemedText type="small" themeColor="textSecondary">
          Restoring your workspace…
        </ThemedText>
      </View>
    );
  }

  return (
    <ThemeProvider value={colorScheme === 'dark' ? DarkTheme : DefaultTheme}>
      <AnimatedSplashOverlay />
      {user ? <AppTabs /> : <LoginScreen />}
    </ThemeProvider>
  );
}

export default function TabLayout() {
  return (
    <AuthProvider>
      <LocaleProvider>
        <TabLayoutContent />
      </LocaleProvider>
    </AuthProvider>
  );
}

const styles = StyleSheet.create({
  loading: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    gap: 12,
  },
});
