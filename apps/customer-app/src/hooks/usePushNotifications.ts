import { useCallback, useEffect, useRef } from 'react';
import { useRouter } from 'expo-router';
import { Platform } from 'react-native';
import * as Device from 'expo-device';
import * as Notifications from 'expo-notifications';

import type { AuthIntent } from '@/auth/auth-intent';
import { useAuthIntent } from '@/context/AuthIntentContext';
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

function notificationIntent(data: Record<string, unknown>): AuthIntent | null {
  const templateCode = typeof data.templateCode === 'string' ? data.templateCode.toUpperCase() : '';
  const referenceId = typeof data.referenceId === 'string' ? data.referenceId : undefined;

  if (templateCode.startsWith('APPOINTMENT_') || templateCode.startsWith('VACCINATION_')) {
    return {
      action: 'ORDER_HISTORY',
      returnTo: '/appointments',
      params: referenceId ? { appointmentId: referenceId } : undefined,
    };
  }

  if (templateCode.includes('RECURRING') || templateCode.includes('SUBSCRIPTION')) {
    return { action: 'ORDER_HISTORY', returnTo: '/subscriptions' };
  }

  if (templateCode.includes('MEDICAL_DOCUMENT')) {
    return {
      action: 'MEDICAL_WRITE',
      returnTo: '/health/reports',
      params: referenceId ? { appointmentId: referenceId } : undefined,
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
      returnTo: referenceId ? `/orders/${referenceId}` : '/(tabs)/orders',
    };
  }

  return null;
}

export function usePushNotifications(userId?: string | null, accessToken?: string | null) {
  const router = useRouter();
  const { requireAuth } = useAuthIntent();
  const handledResponseId = useRef<string | null>(null);

  const handleNotificationResponse = useCallback(async (response: Notifications.NotificationResponse) => {
    const responseId = response.notification.request.identifier;
    if (handledResponseId.current === responseId) return;

    const intent = notificationIntent(response.notification.request.content.data ?? {});
    if (!intent) return;

    handledResponseId.current = responseId;
    const alreadyAuthenticated = await requireAuth(intent);
    if (alreadyAuthenticated) {
      router.push({ pathname: intent.returnTo, params: intent.params } as never);
    }
  }, [requireAuth, router]);

  useEffect(() => {
    const subscription = Notifications.addNotificationResponseReceivedListener((response) => {
      void handleNotificationResponse(response);
    });

    void Notifications.getLastNotificationResponseAsync().then((response) => {
      if (response) void handleNotificationResponse(response);
    });

    return () => subscription.remove();
  }, [handleNotificationResponse]);

  useEffect(() => {
    if (!userId || appConfig.allowDemoMode) return;
    void registerPushToken(accessToken);
  }, [userId, accessToken]);
}
