import Constants from 'expo-constants';
import { useRouter } from 'expo-router';
import { useCallback, useEffect, useRef } from 'react';
import { Platform, Vibration } from 'react-native';

import type { NotificationResponse } from 'expo-notifications';

import { appConfig } from '@/utils/app-config';

type NotificationsModule = typeof import('expo-notifications');

const isExpoGo = Constants.appOwnership === 'expo';

let notificationsModulePromise: Promise<NotificationsModule | null> | null = null;

async function getNotificationsModule(): Promise<NotificationsModule | null> {
  if (Platform.OS === 'web' || isExpoGo) return null;

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

async function responseError(response: Response): Promise<Error> {
  const body = (await response.json().catch(() => null)) as { message?: string; error?: string } | null;
  return new Error(body?.message || body?.error || `Push registration failed (${response.status})`);
}

async function ensureAndroidChannel(
  Notifications: NotificationsModule,
  soundProfile: string,
) {
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

async function registerPushToken(
  accessToken: string,
  appRole?: string | null,
): Promise<string | null> {
  const Notifications = await getNotificationsModule();
  if (!Notifications) return null;

  const Device = await import('expo-device');
  if (!Device.isDevice) return null;

  const permission = await Notifications.requestPermissionsAsync();
  if (!permission.granted) return null;

  const soundProfile = appRole === 'PROVIDER' ? 'order_alert' : 'default';
  await ensureAndroidChannel(Notifications, soundProfile);

  const projectId =
    Constants.easConfig?.projectId ??
    (Constants.expoConfig?.extra?.eas as { projectId?: string } | undefined)?.projectId;
  const tokenResponse = projectId
    ? await Notifications.getExpoPushTokenAsync({ projectId })
    : await Notifications.getExpoPushTokenAsync();
  const token = tokenResponse.data;

  const response = await fetch(`${appConfig.apiBaseUrl}/api/v1/notifications/push-tokens`, {
    method: 'POST',
    headers: {
      Accept: 'application/json',
      'Content-Type': 'application/json',
      Authorization: `Bearer ${accessToken}`,
    },
    body: JSON.stringify({
      expoPushToken: token,
      platform: Platform.OS,
      appRole: appRole ?? undefined,
      soundProfile,
    }),
  });
  if (!response.ok) throw await responseError(response);
  return token;
}

async function unregisterPushToken(token: string, accessToken: string): Promise<void> {
  const response = await fetch(
    `${appConfig.apiBaseUrl}/api/v1/notifications/push-tokens?token=${encodeURIComponent(token)}`,
    {
      method: 'DELETE',
      headers: {
        Accept: 'application/json',
        Authorization: `Bearer ${accessToken}`,
      },
    },
  );
  if (!response.ok && response.status !== 404) throw await responseError(response);
}

function merchantNotificationRoute(
  data: Record<string, unknown>,
  appRole?: string | null,
): string | null {
  const templateCode = typeof data.templateCode === 'string'
    ? data.templateCode.trim().toUpperCase()
    : '';

  if (appRole === 'CAPTAIN') {
    if (templateCode.includes('DELIVERY') || templateCode.includes('DISPATCH') || templateCode.includes('ORDER')) {
      return '/delivery';
    }
    return null;
  }

  if (appRole === 'ADMIN') {
    if (templateCode.includes('CASE') || templateCode.includes('PROVIDER') || templateCode.includes('ADMIN')) {
      return '/admin';
    }
    return null;
  }

  if (templateCode.includes('APPOINTMENT') || templateCode.includes('BOOKING')) {
    return '/explore';
  }
  if (templateCode.includes('RECURRING') || templateCode.includes('SUBSCRIPTION')) {
    return '/subscriptions';
  }
  if (templateCode.includes('PAYOUT') || templateCode.includes('FINANCE') || templateCode.includes('SETTLEMENT')) {
    return '/finance';
  }
  if (templateCode.includes('ORDER') || templateCode.includes('DELIVERY') || templateCode.includes('PAYMENT')) {
    return '/orders';
  }
  return null;
}

export function usePushNotifications(
  userId?: string | null,
  accessToken?: string | null,
  appRole?: string | null,
) {
  const router = useRouter();
  const handledResponseId = useRef<string | null>(null);
  const registeredForUser = useRef<string | null>(null);
  const registeredToken = useRef<string | null>(null);
  const previousAuthentication = useRef<{ userId: string; accessToken: string } | null>(null);

  const handleNotificationResponse = useCallback(async (response: NotificationResponse) => {
    const responseId = response.notification.request.identifier;
    if (handledResponseId.current === responseId) return;

    const route = merchantNotificationRoute(
      response.notification.request.content.data ?? {},
      appRole,
    );
    if (!route) return;

    handledResponseId.current = responseId;
    router.push(route as never);
  }, [appRole, router]);

  useEffect(() => {
    if (Platform.OS === 'web' || isExpoGo) return;

    let disposed = false;
    let subscription: { remove: () => void } | undefined;

    void getNotificationsModule()
      .then(async (Notifications) => {
        if (!Notifications || disposed) return;
        subscription = Notifications.addNotificationResponseReceivedListener((response) => {
          void handleNotificationResponse(response);
        });
        const coldStartResponse = await Notifications.getLastNotificationResponseAsync();
        if (coldStartResponse && !disposed) {
          await handleNotificationResponse(coldStartResponse);
        }
      })
      .catch((error) => console.warn('Unable to initialize notification response handling', error));

    return () => {
      disposed = true;
      subscription?.remove();
    };
  }, [handleNotificationResponse]);

  useEffect(() => {
    const previous = previousAuthentication.current;
    if (previous && !userId && registeredToken.current) {
      const token = registeredToken.current;
      registeredToken.current = null;
      registeredForUser.current = null;
      void unregisterPushToken(token, previous.accessToken).catch((error) => {
        console.warn('Unable to unregister operational push token', error);
      });
    }
    previousAuthentication.current = userId && accessToken ? { userId, accessToken } : null;
  }, [accessToken, userId]);

  useEffect(() => {
    if (
      !userId ||
      !accessToken ||
      appConfig.allowDemoMode ||
      Platform.OS === 'web' ||
      isExpoGo
    ) {
      return;
    }
    if (registeredForUser.current === userId && registeredToken.current) return;

    void registerPushToken(accessToken, appRole)
      .then((token) => {
        if (token) {
          registeredForUser.current = userId;
          registeredToken.current = token;
        }
      })
      .catch((error) => {
        registeredForUser.current = null;
        registeredToken.current = null;
        console.warn('Unable to register merchant push token', error);
      });
  }, [accessToken, appRole, userId]);
}

export async function playMerchantOrderAlertSound() {
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
        sound: Platform.OS === 'android' ? 'order_alert.wav' : 'default',
        priority: Notifications.AndroidNotificationPriority.MAX,
        categoryIdentifier: 'merchant-orders',
        data: { templateCode: 'ORDER_RECEIVED' },
      },
      trigger: null,
    });
  } catch (error) {
    console.warn('Unable to play merchant notification sound', error);
    Vibration.vibrate([0, 400, 200, 400, 200, 600]);
  }
}
