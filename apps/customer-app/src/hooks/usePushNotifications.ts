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

async function registerPushToken(accessToken?: string | null) {
  if (!Device.isDevice) return;

  const permission = await Notifications.requestPermissionsAsync();
  if (!permission.granted) return;

  if (Platform.OS === 'android') {
    await Notifications.setNotificationChannelAsync('customer-reminders', {
      name: 'Pet care reminders',
      importance: Notifications.AndroidImportance.HIGH,
      sound: 'default',
    });
  }

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
      appRole: 'CUSTOMER',
      soundProfile: 'default',
    }),
  });
}

export function usePushNotifications(userId?: string | null, accessToken?: string | null) {
  useEffect(() => {
    if (!userId || appConfig.allowDemoMode) return;
    void registerPushToken(accessToken);
  }, [userId, accessToken]);
}
