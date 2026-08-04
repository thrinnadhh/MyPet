import Constants from 'expo-constants';
import { useRouter } from 'expo-router';
import { useCallback, useEffect, useRef } from 'react';
import { Platform } from 'react-native';

import type { NotificationResponse } from 'expo-notifications';

import type { AuthIntent } from '@/auth/auth-intent';
import { useAuthIntent } from '@/context/AuthIntentContext';
import { appConfig } from '@/utils/app-config';

/**
 * Expo Go does not support Android remote push notifications from SDK 53.
 * Avoid importing expo-notifications at module load time because the import
 * itself throws inside Expo Go.
 */
const isExpoGo = Constants.appOwnership === 'expo';

let notificationsModulePromise:
  | Promise<typeof import('expo-notifications') | null>
  | null = null;

async function getNotificationsModule() {
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
        console.warn('Unable to initialize push notifications', error);
        return null;
      });
  }

  return notificationsModulePromise;
}

async function registerPushToken(accessToken?: string | null) {
  if (Platform.OS === 'web' || isExpoGo) {
    return;
  }

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
      ...(accessToken
        ? { Authorization: `Bearer ${accessToken}` }
        : {}),
    },
    body: JSON.stringify({
      expoPushToken: token,
      platform: Platform.OS,
      appRole: 'CUSTOMER',
      soundProfile: 'default',
    }),
  });
}

function notificationIntent(
  data: Record<string, unknown>,
): AuthIntent | null {
  const templateCode =
    typeof data.templateCode === 'string'
      ? data.templateCode.toUpperCase()
      : '';

  const referenceId =
    typeof data.referenceId === 'string'
      ? data.referenceId
      : undefined;

  if (
    templateCode.startsWith('APPOINTMENT_') ||
    templateCode.startsWith('VACCINATION_')
  ) {
    return {
      action: 'ORDER_HISTORY',
      returnTo: '/appointments',
      params: referenceId
        ? { appointmentId: referenceId }
        : undefined,
    };
  }

  if (
    templateCode.includes('RECURRING') ||
    templateCode.includes('SUBSCRIPTION')
  ) {
    return {
      action: 'ORDER_HISTORY',
      returnTo: '/subscriptions',
    };
  }

  if (templateCode.includes('MEDICAL_DOCUMENT')) {
    return {
      action: 'MEDICAL_WRITE',
      returnTo: '/health/reports',
      params: referenceId
        ? { appointmentId: referenceId }
        : undefined,
    };
  }

  if (
    templateCode.includes('ORDER') ||
    templateCode.includes('DELIVERY') ||
    templateCode.includes('PAYMENT') ||
    templateCode.includes('CASE') ||
    templateCode.includes('REFUND')
  ) {
    return {
      action: 'ORDER_HISTORY',
      returnTo: referenceId
        ? `/orders/${referenceId}`
        : '/(tabs)/orders',
    };
  }

  return null;
}

export function usePushNotifications(
  userId?: string | null,
  accessToken?: string | null,
) {
  const router = useRouter();
  const { requireAuth } = useAuthIntent();
  const handledResponseId = useRef<string | null>(null);
  const expoGoNoticeShown = useRef(false);

  const handleNotificationResponse = useCallback(
    async (response: NotificationResponse) => {
      const responseId = response.notification.request.identifier;

      if (handledResponseId.current === responseId) {
        return;
      }

      const intent = notificationIntent(
        response.notification.request.content.data ?? {},
      );

      if (!intent) {
        return;
      }

      handledResponseId.current = responseId;

      const alreadyAuthenticated = await requireAuth(intent);

      if (alreadyAuthenticated) {
        router.push({
          pathname: intent.returnTo,
          params: intent.params,
        } as never);
      }
    },
    [requireAuth, router],
  );

  useEffect(() => {
    if (Platform.OS === 'web') {
      return;
    }

    if (isExpoGo) {
      if (!expoGoNoticeShown.current) {
        expoGoNoticeShown.current = true;
        console.info(
          'Remote push notifications are disabled in Expo Go. ' +
            'Use a development build to test push notifications.',
        );
      }

      return;
    }

    let disposed = false;
    let subscription: { remove: () => void } | undefined;

    void getNotificationsModule()
      .then(async (Notifications) => {
        if (!Notifications || disposed) {
          return;
        }

        subscription =
          Notifications.addNotificationResponseReceivedListener(
            (response) => {
              void handleNotificationResponse(response);
            },
          );

        const response =
          await Notifications.getLastNotificationResponseAsync();

        if (response && !disposed) {
          await handleNotificationResponse(response);
        }
      })
      .catch((error) => {
        console.warn(
          'Unable to initialize notification response handling',
          error,
        );
      });

    return () => {
      disposed = true;
      subscription?.remove();
    };
  }, [handleNotificationResponse]);

  useEffect(() => {
    if (
      Platform.OS === 'web' ||
      isExpoGo ||
      !userId ||
      appConfig.allowDemoMode
    ) {
      return;
    }

    void registerPushToken(accessToken).catch((error) => {
      console.warn('Unable to register push token', error);
    });
  }, [accessToken, userId]);
}
