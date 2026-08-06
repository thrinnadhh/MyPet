# MyPet Operations App

Expo React Native application for merchant, captain and operational-admin
workflows: onboarding, catalog and barcode inventory, order fulfilment,
appointments, dispatch, delivery proof, support cases and controlled admin
operations.

## Requirements

- Node.js `20.19.x`
- npm
- a reachable MyPet API environment
- physical devices for camera and foreground/background location validation
- Expo/EAS credentials only when producing signed internal builds

## Install and validate

Use the committed lockfile and keep framework dependencies aligned with Expo
SDK 56.

```bash
npm ci
npx --yes expo-doctor@1.20.1
npm run typecheck
npm run lint
npm test
```

When Expo Doctor reports framework compatibility drift, regenerate both package
files with Expo's installer rather than editing lockfile versions or integrity
values manually:

```bash
npx expo install --fix --npm
npm ci
npx --yes expo-doctor@1.20.1
npm run typecheck
npm run lint
npm test
```

## Run locally

```bash
npm run start
```

Native targets:

```bash
npm run android
npm run ios
```

Expo Go is insufficient for release evidence involving barcode-camera behavior,
background captain tracking, foreground-service disclosure, permanent permission
denial recovery, notification taps or force-stopped application restart.

## Release validation

`eas.json` defines development, preview and internal-beta build profiles. Signed
artifacts require the repository's Expo project credentials and should be
validated on the physical-device matrix before distribution.

For captain location flows, record all of the following:

- device and operating-system version
- foreground and background permission grant/deny behavior
- GPS-disabled recovery
- foreground-service disclosure on Android
- restart and offline recovery during an active delivery
- sign-out stopping background tracking

A successful native build does not authorize release distribution by itself.
