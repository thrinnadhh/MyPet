const MERCHANT_VARIANT = 'merchant';
const OPERATIONS_VARIANT = 'operations';

function isLocationPlugin(plugin) {
  return Array.isArray(plugin) && plugin[0] === 'expo-location';
}

module.exports = ({ config }) => {
  const variant = (process.env.MYPET_APP_VARIANT || OPERATIONS_VARIANT).trim().toLowerCase();
  const merchant = variant === MERCHANT_VARIANT;

  if (!merchant && variant !== OPERATIONS_VARIANT) {
    throw new Error(`Unsupported MYPET_APP_VARIANT: ${variant}`);
  }

  const plugins = (config.plugins || []).map((plugin) => {
    if (!merchant || !isLocationPlugin(plugin)) return plugin;

    return [
      'expo-location',
      {
        locationWhenInUsePermission:
          'Allow MyPet Merchant to use your location while you verify the storefront, clinic, or grooming-center location.',
        locationAlwaysAndWhenInUsePermission: undefined,
        isIosBackgroundLocationEnabled: false,
        isAndroidBackgroundLocationEnabled: false,
        isAndroidForegroundServiceEnabled: false,
      },
    ];
  });

  return {
    ...config,
    name: merchant ? 'MyPet Merchant' : config.name,
    slug: merchant ? 'mypet-merchant' : config.slug,
    scheme: merchant ? 'mypet-merchant' : config.scheme,
    version: merchant ? '1.0.0' : config.version,
    ios: {
      ...config.ios,
      buildNumber: merchant ? '1' : config.ios?.buildNumber,
      bundleIdentifier: merchant ? 'com.mypet.merchant' : config.ios?.bundleIdentifier,
    },
    android: {
      ...config.android,
      package: merchant ? 'com.mypet.merchant' : config.android?.package,
      versionCode: merchant ? 1 : config.android?.versionCode,
      blockedPermissions: merchant
        ? [
            ...new Set([
              ...(config.android?.blockedPermissions || []),
              'android.permission.ACCESS_BACKGROUND_LOCATION',
              'android.permission.FOREGROUND_SERVICE_LOCATION',
            ]),
          ]
        : config.android?.blockedPermissions,
    },
    plugins,
    extra: {
      ...config.extra,
      appVariant: variant,
      releaseChannel:
        process.env.EXPO_PUBLIC_RELEASE_CHANNEL || config.extra?.releaseChannel || 'development',
      releaseVersion:
        process.env.EXPO_PUBLIC_RELEASE_VERSION || config.extra?.releaseVersion || config.version,
    },
  };
};
