import Constants from 'expo-constants';
import { useEffect } from 'react';
import { Platform, Vibration } from 'react-native';

import { appConfig } from '@/utils/app-config';

type NotificationsModule = typeof import('expo-notifications');

const isExpoGo = Constants.appOwnership === 'expo';

let notificationsModulePromise:
  | Promise<NotificationsModule | null>
  | null = null;

async function getNotificationsModule(): Promise<NotificationsModule | null> {
  if (Platform.OS === 'web' || isExpoGo) {
    return null;
  }

  if (!notificationsModulePromise) {
    notificationsModulePromise = import('expo-notifications')
      .then((Notifications) => {
        Notifications.setNotificationHandler({
          handleNotification: async () => ({
            shouldShowAlert: true,
            shouldPlaySound: true,
            shouldSetBadge: true,
            shouldShowBanner: true,
            shouldShowList: true,
          }),
        });

        return Notifications;
      })
      .catch((error) => {
        notificationsModulePromise = null;
        console.warn('Unable to initialize notifications', error);
        return null;
      });
  }

  return notificationsModulePromise;
}

async function ensureAndroidChannel(
  Notifications: NotificationsModule,
  soundProfile: string,
) {
  if (Platform.OS !== 'android') {
    return;
  }

  await Notifications.setNotificationChannelAsync('merchant-orders', {
    name: 'Merchant order alerts',
    importance: Notifications.AndroidImportance.MAX,
    vibrationPattern: [0, 400, 200, 400, 200, 600],
    sound: soundProfile === 'order_alert'
      ? 'order_alert.wav'
      : 'default',
    bypassDnd: true,
    lockscreenVisibility:
      Notifications.AndroidNotificationVisibility.PUBLIC,
  });
}

async function registerPushToken(
  accessToken?: string | null,
  appRole?: string | null,
) {
  const Notifications = await getNotificationsModule();

  if (!Notifications) {
    return;
  }

  const Device = await import('expo-device');

  if (!Device.isDevice) {
    return;
  }

  const permission = await Notifications.requestPermissionsAsync();

  if (!permission.granted) {
    return;
  }

  const soundProfile =
    appRole === 'PROVIDER' ? 'order_alert' : 'default';

  await ensureAndroidChannel(Notifications, soundProfile);

  const token = (
    await Notifications.getExpoPushTokenAsync()
  ).data;

  const response = await fetch(
    `${appConfig.apiBaseUrl}/api/v1/notifications/push-tokens`,
    {
      method: 'POST',
      headers: {
        Accept: 'application/json',
        'Content-Type': 'application/json',
        ...(accessToken
          ? { Authorization: `Bearer ${accessToken}` }
          : {}),
      },
      body: JSON.stringify({
        expoPushToken: token,
        platform: Platform.OS,
        appRole: appRole ?? undefined,
        soundProfile,
      }),
    },
  );

  if (!response.ok) {
    throw new Error(
      `Push-token registration failed with ${response.status}`,
    );
  }
}

export function usePushNotifications(
  userId?: string | null,
  accessToken?: string | null,
  appRole?: string | null,
) {
  useEffect(() => {
    if (
      !userId ||
      appConfig.allowDemoMode ||
      Platform.OS === 'web' ||
      isExpoGo
    ) {
      return;
    }

    void registerPushToken(accessToken, appRole).catch((error) => {
      console.warn('Unable to register merchant push token', error);
    });
  }, [accessToken, appRole, userId]);
}

export async function playMerchantOrderAlertSound() {
  /*
   * Remote notifications are unavailable in Expo Go.
   * Preserve the foreground merchant alert using vibration.
   */
  if (isExpoGo || Platform.OS === 'web') {
    Vibration.vibrate([0, 400, 200, 400, 200, 600]);
    return;
  }

  try {
    const Notifications = await getNotificationsModule();

    if (!Notifications) {
      Vibration.vibrate([0, 400, 200, 400, 200, 600]);
      return;
    }

    await Notifications.scheduleNotificationAsync({
      content: {
        title: 'New order received!',
        body: 'Pack before pickup',
        sound:
          Platform.OS === 'android'
            ? 'order_alert.wav'
            : 'default',
        priority:
          Notifications.AndroidNotificationPriority.MAX,
        categoryIdentifier: 'merchant-orders',
      },
      trigger: null,
    });
  } catch (error) {
    console.warn('Unable to play merchant notification sound', error);

    Vibration.vibrate([0, 400, 200, 400, 200, 600]);
  }
}
