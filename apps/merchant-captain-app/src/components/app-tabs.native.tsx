import { NativeTabs } from 'expo-router/unstable-native-tabs';
import { useColorScheme } from 'react-native';

import { Colors } from '@/constants/theme';
import { useAuth } from '@/context/AuthContext';
import { appConfig } from '@/utils/app-config';

export default function AppTabs() {
  const { activeRole } = useAuth();
  const scheme = useColorScheme();
  const resolvedScheme = scheme === 'dark' ? 'dark' : 'light';
  const colors = Colors[resolvedScheme];

  const isAdmin = activeRole === 'ADMIN';
  const isProvider = activeRole === 'PROVIDER';
  const isCaptain = activeRole === 'CAPTAIN';

  /*
   * NativeTabs does not support dynamically adding/removing triggers.
   * Keep one stable trigger registry for the entire app lifecycle and
   * only change visibility for the current operational role.
   */
  return (
    <NativeTabs
      backgroundColor={colors.background}
      indicatorColor={colors.backgroundElement}
      labelStyle={{ selected: { color: colors.text } }}
    >
      <NativeTabs.Trigger name="index" hidden={isCaptain}>
        <NativeTabs.Trigger.Label>Home</NativeTabs.Trigger.Label>
        <NativeTabs.Trigger.Icon src={require('@/assets/images/tabIcons/home.png')} renderingMode="template" />
      </NativeTabs.Trigger>

      <NativeTabs.Trigger name="admin" hidden={!isAdmin && !(isProvider && appConfig.allowDemoMode)}>
        <NativeTabs.Trigger.Label>Admin</NativeTabs.Trigger.Label>
        <NativeTabs.Trigger.Icon src={require('@/assets/images/tabIcons/explore.png')} renderingMode="template" />
      </NativeTabs.Trigger>

      <NativeTabs.Trigger name="store" hidden={!isProvider}>
        <NativeTabs.Trigger.Label>Store</NativeTabs.Trigger.Label>
        <NativeTabs.Trigger.Icon src={require('@/assets/images/tabIcons/home.png')} renderingMode="template" />
      </NativeTabs.Trigger>

      <NativeTabs.Trigger name="orders" hidden={!isProvider}>
        <NativeTabs.Trigger.Label>Orders</NativeTabs.Trigger.Label>
        <NativeTabs.Trigger.Icon src={require('@/assets/images/tabIcons/explore.png')} renderingMode="template" />
      </NativeTabs.Trigger>

      <NativeTabs.Trigger name="explore" hidden={!isProvider}>
        <NativeTabs.Trigger.Label>Bookings</NativeTabs.Trigger.Label>
        <NativeTabs.Trigger.Icon src={require('@/assets/images/tabIcons/explore.png')} renderingMode="template" />
      </NativeTabs.Trigger>

      <NativeTabs.Trigger name="inventory" hidden={!isProvider}>
        <NativeTabs.Trigger.Label>Inventory</NativeTabs.Trigger.Label>
        <NativeTabs.Trigger.Icon src={require('@/assets/images/tabIcons/explore.png')} renderingMode="template" />
      </NativeTabs.Trigger>

      <NativeTabs.Trigger name="subscriptions" hidden={!isProvider}>
        <NativeTabs.Trigger.Label>Subscriptions</NativeTabs.Trigger.Label>
        <NativeTabs.Trigger.Icon src={require('@/assets/images/tabIcons/explore.png')} renderingMode="template" />
      </NativeTabs.Trigger>

      <NativeTabs.Trigger name="billing" hidden={!isAdmin && !isProvider}>
        <NativeTabs.Trigger.Label>POS</NativeTabs.Trigger.Label>
        <NativeTabs.Trigger.Icon src={require('@/assets/images/tabIcons/explore.png')} renderingMode="template" />
      </NativeTabs.Trigger>

      <NativeTabs.Trigger name="finance" hidden={!isProvider}>
        <NativeTabs.Trigger.Label>Finance</NativeTabs.Trigger.Label>
        <NativeTabs.Trigger.Icon src={require('@/assets/images/tabIcons/explore.png')} renderingMode="template" />
      </NativeTabs.Trigger>

      <NativeTabs.Trigger name="delivery" hidden={!isCaptain}>
        <NativeTabs.Trigger.Label>Delivery</NativeTabs.Trigger.Label>
        <NativeTabs.Trigger.Icon src={require('@/assets/images/tabIcons/home.png')} renderingMode="template" />
      </NativeTabs.Trigger>

      <NativeTabs.Trigger name="earnings" hidden={!isAdmin && !isCaptain}>
        <NativeTabs.Trigger.Label>{isAdmin ? 'Payouts' : 'Earnings'}</NativeTabs.Trigger.Label>
        <NativeTabs.Trigger.Icon src={require('@/assets/images/tabIcons/explore.png')} renderingMode="template" />
      </NativeTabs.Trigger>
    </NativeTabs>
  );
}
