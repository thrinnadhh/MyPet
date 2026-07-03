import { useEffect } from 'react';
import { Platform } from 'react-native';
import * as Device from 'expo-device';
import * as Notifications from 'expo-notifications';

import { appConfig } from '@/utils/app-config';

Notifications.setNotificationHandler({
  handleNotification: async () => ({
    shouldShowAlert: true,
    shouldPlaySound: true,
    shouldSetBadge: true,
    shouldShowBanner: true,
    shouldShowList: true,
  }),
});

async function ensureAndroidChannel(soundProfile: string) {
  if (Platform.OS !== 'android') return;
  await Notifications.setNotificationChannelAsync('merchant-orders', {
    name: 'Merchant order alerts',
    importance: Notifications.AndroidImportance.MAX,
    vibrationPattern: [0, 400, 200, 400, 200, 600],
    sound: soundProfile === 'order_alert' ? 'order_alert.wav' : 'default',
    bypassDnd: true,
    lockscreenVisibility: Notifications.AndroidNotificationVisibility.PUBLIC,
  });
}

async function registerPushToken(accessToken?: string | null, appRole?: string | null) {
  if (!Device.isDevice) return;

  const permission = await Notifications.requestPermissionsAsync();
  if (!permission.granted) return;

  const soundProfile = appRole === 'PROVIDER' ? 'order_alert' : 'default';
  await ensureAndroidChannel(soundProfile);

  const token = (await Notifications.getExpoPushTokenAsync()).data;
  await fetch(`${appConfig.apiBaseUrl}/api/v1/notifications/push-tokens`, {
    method: 'POST',
    headers: {
      Accept: 'application/json',
      'Content-Type': 'application/json',
      ...(accessToken ? { Authorization: `Bearer ${accessToken}` } : {}),
    },
    body: JSON.stringify({
      expoPushToken: token,
      platform: Platform.OS,
      appRole: appRole ?? undefined,
      soundProfile,
    }),
  });
}

export function usePushNotifications(userId?: string | null, accessToken?: string | null, appRole?: string | null) {
  useEffect(() => {
    if (!userId || appConfig.allowDemoMode) return;
    void registerPushToken(accessToken, appRole);
  }, [userId, accessToken, appRole]);
}

export async function playMerchantOrderAlertSound() {
  try {
    await Notifications.scheduleNotificationAsync({
      content: {
        title: 'New order received!',
        body: 'Pack before pickup',
        sound: Platform.OS === 'android' ? 'order_alert.wav' : 'default',
        priority: Notifications.AndroidNotificationPriority.MAX,
        categoryIdentifier: 'merchant-orders',
      },
      trigger: null,
    });
  } catch {
    // Foreground UI alert still handles vibration if sound asset is missing.
  }
}
