import AsyncStorage from '@react-native-async-storage/async-storage';
import * as Location from 'expo-location';
import * as TaskManager from 'expo-task-manager';
import { Platform } from 'react-native';

export const CAPTAIN_LOCATION_TASK = 'mypet-captain-location-updates';

const SESSION_KEY = 'mypet.captain.location-session.v1';
const DEMO_COORDINATES: CaptainCoordinates = { latitude: 13.6288, longitude: 79.4192 };
const MAX_LOCATION_AGE_MS = 60_000;

export interface CaptainCoordinates {
  latitude: number;
  longitude: number;
  accuracy?: number | null;
  timestamp?: number;
}

interface CaptainLocationSession {
  apiBaseUrl: string;
  userId: string;
  accessToken: string;
}

export type CaptainTrackingMode = 'background' | 'foreground' | 'web' | 'demo';

export class CaptainLocationError extends Error {
  constructor(
    public readonly code:
      | 'services-disabled'
      | 'permission-denied'
      | 'permission-blocked'
      | 'location-unavailable'
      | 'location-stale'
      | 'session-missing'
      | 'publish-failed',
    message: string,
  ) {
    super(message);
    this.name = 'CaptainLocationError';
  }
}

function isValidCoordinate(value: number, minimum: number, maximum: number): boolean {
  return Number.isFinite(value) && value >= minimum && value <= maximum;
}

function normalizeLocation(location: Location.LocationObject): CaptainCoordinates {
  const coordinates: CaptainCoordinates = {
    latitude: location.coords.latitude,
    longitude: location.coords.longitude,
    accuracy: location.coords.accuracy,
    timestamp: location.timestamp,
  };

  if (
    !isValidCoordinate(coordinates.latitude, -90, 90) ||
    !isValidCoordinate(coordinates.longitude, -180, 180)
  ) {
    throw new CaptainLocationError('location-unavailable', 'The device returned an invalid location.');
  }

  if (coordinates.timestamp && Date.now() - coordinates.timestamp > MAX_LOCATION_AGE_MS) {
    throw new CaptainLocationError('location-stale', 'The device location is stale. Turn on GPS and try again.');
  }

  return coordinates;
}

async function readSession(): Promise<CaptainLocationSession | null> {
  const stored = await AsyncStorage.getItem(SESSION_KEY);
  if (!stored) return null;
  try {
    return JSON.parse(stored) as CaptainLocationSession;
  } catch {
    await AsyncStorage.removeItem(SESSION_KEY);
    return null;
  }
}

async function saveSession(session: CaptainLocationSession): Promise<void> {
  await AsyncStorage.setItem(SESSION_KEY, JSON.stringify(session));
}

async function publishCoordinates(
  session: CaptainLocationSession,
  coordinates: CaptainCoordinates,
): Promise<void> {
  const response = await fetch(`${session.apiBaseUrl}/api/v1/captains/location`, {
    method: 'PUT',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${session.accessToken}`,
      'X-User-Id': session.userId,
      'X-User-Role': 'CAPTAIN',
    },
    body: JSON.stringify({
      longitude: coordinates.longitude,
      latitude: coordinates.latitude,
      accuracyMeters: coordinates.accuracy ?? null,
      capturedAt: coordinates.timestamp ? new Date(coordinates.timestamp).toISOString() : new Date().toISOString(),
    }),
  });

  if (!response.ok) {
    throw new CaptainLocationError('publish-failed', `Location update was rejected (${response.status}).`);
  }
}

if (!TaskManager.isTaskDefined(CAPTAIN_LOCATION_TASK)) {
  TaskManager.defineTask(CAPTAIN_LOCATION_TASK, async ({ data, error }) => {
    if (error || !data) return;
    const session = await readSession();
    if (!session) return;

    const locations = (data as { locations?: Location.LocationObject[] }).locations ?? [];
    const latest = locations.at(-1);
    if (!latest) return;

    try {
      await publishCoordinates(session, normalizeLocation(latest));
    } catch {
      // The next OS-delivered update retries naturally. Never fabricate a coordinate.
    }
  });
}

async function ensureForegroundPermission(): Promise<void> {
  const servicesEnabled = await Location.hasServicesEnabledAsync();
  if (!servicesEnabled) {
    throw new CaptainLocationError('services-disabled', 'Turn on device location services before going online.');
  }

  let permission = await Location.getForegroundPermissionsAsync();
  if (permission.status !== Location.PermissionStatus.GRANTED && permission.canAskAgain) {
    permission = await Location.requestForegroundPermissionsAsync();
  }

  if (permission.status !== Location.PermissionStatus.GRANTED) {
    throw new CaptainLocationError(
      permission.canAskAgain ? 'permission-denied' : 'permission-blocked',
      permission.canAskAgain
        ? 'Location permission is required before going online.'
        : 'Location permission is blocked. Enable it in device settings.',
    );
  }
}

async function requestBackgroundPermission(): Promise<boolean> {
  if (Platform.OS === 'web') return false;
  let permission = await Location.getBackgroundPermissionsAsync();
  if (permission.status !== Location.PermissionStatus.GRANTED && permission.canAskAgain) {
    permission = await Location.requestBackgroundPermissionsAsync();
  }
  return permission.status === Location.PermissionStatus.GRANTED;
}

export async function getCaptainCoordinates(options?: {
  allowDemoMode?: boolean;
}): Promise<CaptainCoordinates> {
  if (options?.allowDemoMode) {
    return { ...DEMO_COORDINATES, timestamp: Date.now(), accuracy: 0 };
  }

  await ensureForegroundPermission();
  const location = await Location.getCurrentPositionAsync({
    accuracy: Location.Accuracy.High,
    mayShowUserSettingsDialog: true,
  });
  return normalizeLocation(location);
}

export async function startCaptainLocationTracking(input: {
  apiBaseUrl: string;
  userId: string;
  accessToken: string;
  allowDemoMode?: boolean;
}): Promise<{ mode: CaptainTrackingMode; warning?: string }> {
  if (input.allowDemoMode) return { mode: 'demo' };
  if (!input.userId || !input.accessToken) {
    throw new CaptainLocationError('session-missing', 'An authenticated captain session is required.');
  }

  await ensureForegroundPermission();
  await saveSession({
    apiBaseUrl: input.apiBaseUrl,
    userId: input.userId,
    accessToken: input.accessToken,
  });

  if (Platform.OS === 'web') return { mode: 'web' };

  const taskManagerAvailable = await TaskManager.isAvailableAsync();
  const backgroundGranted = await requestBackgroundPermission();
  if (!taskManagerAvailable || !backgroundGranted) {
    return {
      mode: 'foreground',
      warning: backgroundGranted
        ? 'Background tracking is unavailable in this build. Keep the app open during deliveries.'
        : 'Background location was not granted. Keep the app open during deliveries or enable Always access in settings.',
    };
  }

  const alreadyStarted = await Location.hasStartedLocationUpdatesAsync(CAPTAIN_LOCATION_TASK);
  if (!alreadyStarted) {
    await Location.startLocationUpdatesAsync(CAPTAIN_LOCATION_TASK, {
      accuracy: Location.Accuracy.High,
      activityType: Location.ActivityType.AutomotiveNavigation,
      distanceInterval: 20,
      timeInterval: 15_000,
      deferredUpdatesDistance: 50,
      deferredUpdatesInterval: 30_000,
      pausesUpdatesAutomatically: false,
      showsBackgroundLocationIndicator: true,
      foregroundService: {
        notificationTitle: 'MyPet delivery tracking',
        notificationBody: 'Your location is shared while you are online for delivery work.',
        notificationColor: '#208AEF',
      },
    });
  }

  return { mode: 'background' };
}

export async function syncCaptainLocationNow(input: {
  apiBaseUrl: string;
  userId: string;
  accessToken: string;
  allowDemoMode?: boolean;
}): Promise<boolean> {
  try {
    const coordinates = await getCaptainCoordinates({ allowDemoMode: input.allowDemoMode });
    await publishCoordinates(
      { apiBaseUrl: input.apiBaseUrl, userId: input.userId, accessToken: input.accessToken },
      coordinates,
    );
    return true;
  } catch {
    return false;
  }
}

export async function stopCaptainLocationTracking(): Promise<void> {
  try {
    if (Platform.OS !== 'web') {
      const started = await Location.hasStartedLocationUpdatesAsync(CAPTAIN_LOCATION_TASK);
      if (started) await Location.stopLocationUpdatesAsync(CAPTAIN_LOCATION_TASK);
    }
  } finally {
    await AsyncStorage.removeItem(SESSION_KEY);
  }
}
