from __future__ import annotations

import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / "apps" / "merchant-captain-app"


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def write(path: Path, content: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content, encoding="utf-8")


def replace_once(content: str, old: str, new: str, label: str) -> str:
    count = content.count(old)
    if count != 1:
        raise RuntimeError(f"Expected exactly one {label}; found {count}")
    return content.replace(old, new, 1)


# 1. Add Expo SDK 56-compatible native location packages.
package_path = APP / "package.json"
package = json.loads(read(package_path))
dependencies = package["dependencies"]
dependencies["expo-location"] = "~56.0.22"
dependencies["expo-task-manager"] = "~56.0.24"
write(package_path, json.dumps(package, indent=2) + "\n")


# 2. Configure Android/iOS permissions and foreground/background services.
app_json_path = APP / "app.json"
app_json = json.loads(read(app_json_path))
expo = app_json["expo"]
plugins = expo.setdefault("plugins", [])
location_plugin = [
    "expo-location",
    {
        "locationWhenInUsePermission": "Allow MyPet Captain to use your location to receive nearby delivery offers and share delivery progress.",
        "locationAlwaysAndWhenInUsePermission": "Allow MyPet Captain to use your location while you are online or completing an active delivery.",
        "isIosBackgroundLocationEnabled": True,
        "isAndroidBackgroundLocationEnabled": True,
        "isAndroidForegroundServiceEnabled": True,
    },
]
plugins = [plugin for plugin in plugins if not (isinstance(plugin, str) and plugin == "expo-location") and not (isinstance(plugin, list) and plugin and plugin[0] == "expo-location")]
plugins.insert(1, location_plugin)
expo["plugins"] = plugins
write(app_json_path, json.dumps(app_json, indent=2) + "\n")


# 3. Native/background location service. The task is defined at module scope.
location_service = r'''import AsyncStorage from '@react-native-async-storage/async-storage';
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
'''
write(APP / "src" / "services" / "captain-location.ts", location_service)


# 4. Register the task before Expo Router navigation mounts.
layout_path = APP / "src" / "app" / "_layout.tsx"
layout = read(layout_path)
layout = replace_once(
    layout,
    "import '@/i18n';\n",
    "import '@/i18n';\nimport '@/services/captain-location';\n",
    "root location task import",
)
write(layout_path, layout)


# 5. Stop tracking whenever auth is cleared or the user explicitly signs out.
auth_path = APP / "src" / "context" / "AuthContext.tsx"
auth = read(auth_path)
auth = replace_once(
    auth,
    "import { ApiError, apiClient } from '../services/api-client';\n",
    "import { ApiError, apiClient } from '../services/api-client';\nimport { stopCaptainLocationTracking } from '../services/captain-location';\n",
    "auth location import",
)
auth = replace_once(
    auth,
    "      if (!nextSession) {\n        setRole(null);",
    "      if (!nextSession) {\n        await stopCaptainLocationTracking();\n        setRole(null);",
    "auth session-clear shutdown",
)
auth = replace_once(
    auth,
    "  const signOut = async () => {\n    apiClient.setSessionToken(null);",
    "  const signOut = async () => {\n    await stopCaptainLocationTracking();\n    apiClient.setSessionToken(null);",
    "explicit sign-out shutdown",
)
write(auth_path, auth)


# 6. Replace browser-only captain location with native Expo location lifecycle.
delivery_path = APP / "src" / "app" / "delivery.tsx"
delivery = read(delivery_path)
delivery = replace_once(
    delivery,
    "import { Alert, Modal, Platform, Pressable, StyleSheet, View } from 'react-native';",
    "import { Alert, Linking, Modal, Pressable, StyleSheet, View } from 'react-native';",
    "delivery React Native imports",
)
delivery = replace_once(
    delivery,
    "import { useTheme } from '@/hooks/use-theme';\nimport { appConfig } from '@/utils/app-config';",
    "import { useTheme } from '@/hooks/use-theme';\nimport {\n  CaptainLocationError,\n  getCaptainCoordinates,\n  startCaptainLocationTracking,\n  stopCaptainLocationTracking,\n  syncCaptainLocationNow,\n} from '@/services/captain-location';\nimport { appConfig } from '@/utils/app-config';",
    "delivery location service import",
)
start = delivery.index("interface Coordinates {")
end = delivery.index("export default function DeliveryScreen()")
delivery = delivery[:start] + "type DeliveryStep = 1 | 2 | 3 | 4;\n\n" + delivery[end:]
old_get_coordinates = """  const getCoordinates = useCallback(async (): Promise<Coordinates | null> => {\n    const browserLocation = await browserCoordinates();\n    if (browserLocation) return browserLocation;\n    return appConfig.allowDemoMode ? DEMO_COORDINATES : null;\n  }, []);\n\n  const updateLocation = useCallback(async () => {\n    const coordinates = await getCoordinates();\n    if (!coordinates) return false;\n    const response = await fetch(`${appConfig.apiBaseUrl}/api/v1/captains/location`, {\n      method: 'PUT',\n      headers: authHeaders(true),\n      body: JSON.stringify({ longitude: coordinates.longitude, latitude: coordinates.latitude }),\n    });\n    return response.ok;\n  }, [authHeaders, getCoordinates]);\n"""
new_get_coordinates = """  const getCoordinates = useCallback(\n    () => getCaptainCoordinates({ allowDemoMode: appConfig.allowDemoMode }),\n    [],\n  );\n\n  const updateLocation = useCallback(async () => {\n    if (!user?.id || !session?.access_token) return false;\n    return syncCaptainLocationNow({\n      apiBaseUrl: appConfig.apiBaseUrl,\n      userId: user.id,\n      accessToken: session.access_token,\n      allowDemoMode: appConfig.allowDemoMode,\n    });\n  }, [session, user]);\n"""
delivery = replace_once(delivery, old_get_coordinates, new_get_coordinates, "native coordinate helpers")
delivery = replace_once(
    delivery,
    "      const nextOnline = !isOnline;\n      const coordinates = nextOnline ? await getCoordinates() : null;\n      if (nextOnline && !coordinates) {\n        Alert.alert(\n          'Location access required',\n          'This mobile build does not yet include the native location module. MyPet will not publish fabricated coordinates. Use the web build with browser location, or enable demo mode for sandbox testing.',\n        );\n        return;\n      }",
    "      const nextOnline = !isOnline;\n      const coordinates = nextOnline ? await getCoordinates() : null;",
    "obsolete missing-module warning",
)
delivery = replace_once(
    delivery,
    "      setIsOnline(nextOnline);\n      if (!nextOnline) setActiveOffer(null);",
    "      if (nextOnline) {\n        if (!session?.access_token) {\n          throw new CaptainLocationError('session-missing', 'An authenticated captain session is required.');\n        }\n        const tracking = await startCaptainLocationTracking({\n          apiBaseUrl: appConfig.apiBaseUrl,\n          userId: user.id,\n          accessToken: session.access_token,\n          allowDemoMode: appConfig.allowDemoMode,\n        });\n        if (tracking.warning) {\n          Alert.alert('Background tracking limited', tracking.warning);\n        }\n      } else {\n        await stopCaptainLocationTracking();\n        setActiveOffer(null);\n      }\n      setIsOnline(nextOnline);",
    "online tracking lifecycle",
)
delivery = replace_once(
    delivery,
    "    } catch (error: unknown) {\n      Alert.alert('Status change failed', error instanceof Error ? error.message : 'Please check your connection.');",
    "    } catch (error: unknown) {\n      if (error instanceof CaptainLocationError && error.code === 'permission-blocked') {\n        Alert.alert('Location permission blocked', error.message, [\n          { text: 'Cancel', style: 'cancel' },\n          { text: 'Open settings', onPress: () => void Linking.openSettings() },\n        ]);\n      } else {\n        Alert.alert('Status change failed', error instanceof Error ? error.message : 'Please check your connection.');\n      }",
    "location-aware status error",
)
delivery = replace_once(
    delivery,
    "  }, [authHeaders, getCoordinates, isOnline, user]);",
    "  }, [authHeaders, getCoordinates, isOnline, session, user]);",
    "toggle dependency list",
)
delivery = replace_once(
    delivery,
    "              ? 'Location updates are sent from the browser geolocation source.'",
    "              ? 'Verified device location updates are sent while you are online.'",
    "location banner copy",
)
write(delivery_path, delivery)


# 7. Static contract tests keep native location from regressing.
test_content = r'''import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import test from 'node:test';

function source(path: string): string {
  return readFileSync(join(process.cwd(), path), 'utf8');
}

test('operational app declares Expo native and background location modules', () => {
  const packageJson = JSON.parse(source('package.json')) as { dependencies: Record<string, string> };
  assert.equal(packageJson.dependencies['expo-location'], '~56.0.22');
  assert.equal(packageJson.dependencies['expo-task-manager'], '~56.0.24');
});

test('Expo config enables native location permissions and foreground service', () => {
  const appJson = source('app.json');
  assert.match(appJson, /"expo-location"/);
  assert.match(appJson, /"isIosBackgroundLocationEnabled": true/);
  assert.match(appJson, /"isAndroidBackgroundLocationEnabled": true/);
  assert.match(appJson, /"isAndroidForegroundServiceEnabled": true/);
});

test('captain location task is registered before router navigation', () => {
  const layout = source('src/app/_layout.tsx');
  const service = source('src/services/captain-location.ts');
  assert.match(layout, /import '@\/services\/captain-location';/);
  assert.match(service, /TaskManager\.defineTask/);
  assert.match(service, /Location\.startLocationUpdatesAsync/);
  assert.match(service, /Location\.stopLocationUpdatesAsync/);
});

test('captain delivery uses device location and never restores browser-only fallback', () => {
  const delivery = source('src/app/delivery.tsx');
  assert.match(delivery, /getCaptainCoordinates/);
  assert.match(delivery, /startCaptainLocationTracking/);
  assert.match(delivery, /stopCaptainLocationTracking/);
  assert.equal(delivery.includes('browserCoordinates'), false);
  assert.equal(delivery.includes('does not yet include the native location module'), false);
});

test('sign out and cleared sessions stop background tracking', () => {
  const auth = source('src/context/AuthContext.tsx');
  assert.match(auth, /await stopCaptainLocationTracking\(\)/);
});
'''
write(APP / "src" / "__tests__" / "native-captain-location.test.ts", test_content)

print("Native captain location migration staged successfully.")
