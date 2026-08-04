import { NativeTabs } from 'expo-router/unstable-native-tabs';
import { useColorScheme } from 'react-native';
import { Colors } from '@/constants/theme';
import { useAuth } from '@/context/AuthContext';
import { appConfig } from '@/utils/app-config';

export default function AppTabs() {
  const { activeRole } = useAuth();
  const scheme = useColorScheme();
  const colors = Colors[scheme === 'unspecified' ? 'light' : scheme];

  return (
    <NativeTabs
      backgroundColor={colors.background}
      indicatorColor={colors.backgroundElement}
      labelStyle={{ selected: { color: colors.text } }}>
      {activeRole === 'ADMIN' ? (
        <>
          <NativeTabs.Trigger name="index">
            <NativeTabs.Trigger.Label>Home</NativeTabs.Trigger.Label>
            <NativeTabs.Trigger.Icon src={require('@/assets/images/tabIcons/home.png')} renderingMode="template" />
          </NativeTabs.Trigger>

          <NativeTabs.Trigger name="admin">
            <NativeTabs.Trigger.Label>Admin</NativeTabs.Trigger.Label>
            <NativeTabs.Trigger.Icon src={require('@/assets/images/tabIcons/explore.png')} renderingMode="template" />
          </NativeTabs.Trigger>

          <NativeTabs.Trigger name="billing">
            <NativeTabs.Trigger.Label>POS</NativeTabs.Trigger.Label>
            <NativeTabs.Trigger.Icon src={require('@/assets/images/tabIcons/explore.png')} renderingMode="template" />
          </NativeTabs.Trigger>

          <NativeTabs.Trigger name="earnings">
            <NativeTabs.Trigger.Label>Payouts</NativeTabs.Trigger.Label>
            <NativeTabs.Trigger.Icon src={require('@/assets/images/tabIcons/explore.png')} renderingMode="template" />
          </NativeTabs.Trigger>
        </>
      ) : activeRole === 'PROVIDER' ? (
        <>
          <NativeTabs.Trigger name="index">
            <NativeTabs.Trigger.Label>Home</NativeTabs.Trigger.Label>
            <NativeTabs.Trigger.Icon src={require('@/assets/images/tabIcons/home.png')} renderingMode="template" />
          </NativeTabs.Trigger>

          <NativeTabs.Trigger name="orders">
            <NativeTabs.Trigger.Label>Orders</NativeTabs.Trigger.Label>
            <NativeTabs.Trigger.Icon src={require('@/assets/images/tabIcons/explore.png')} renderingMode="template" />
          </NativeTabs.Trigger>

          <NativeTabs.Trigger name="explore">
            <NativeTabs.Trigger.Label>Bookings</NativeTabs.Trigger.Label>
            <NativeTabs.Trigger.Icon src={require('@/assets/images/tabIcons/explore.png')} renderingMode="template" />
          </NativeTabs.Trigger>

          <NativeTabs.Trigger name="inventory">
            <NativeTabs.Trigger.Label>Inventory</NativeTabs.Trigger.Label>
            <NativeTabs.Trigger.Icon src={require('@/assets/images/tabIcons/explore.png')} renderingMode="template" />
          </NativeTabs.Trigger>

          <NativeTabs.Trigger name="billing">
            <NativeTabs.Trigger.Label>POS</NativeTabs.Trigger.Label>
            <NativeTabs.Trigger.Icon src={require('@/assets/images/tabIcons/explore.png')} renderingMode="template" />
          </NativeTabs.Trigger>

          <NativeTabs.Trigger name="finance">
            <NativeTabs.Trigger.Label>Finance</NativeTabs.Trigger.Label>
            <NativeTabs.Trigger.Icon src={require('@/assets/images/tabIcons/explore.png')} renderingMode="template" />
          </NativeTabs.Trigger>

          {appConfig.allowDemoMode ? (
            <NativeTabs.Trigger name="admin">
              <NativeTabs.Trigger.Label>Admin</NativeTabs.Trigger.Label>
              <NativeTabs.Trigger.Icon src={require('@/assets/images/tabIcons/explore.png')} renderingMode="template" />
            </NativeTabs.Trigger>
          ) : null}
        </>
      ) : (
        <>
          <NativeTabs.Trigger name="delivery">
            <NativeTabs.Trigger.Label>Delivery</NativeTabs.Trigger.Label>
            <NativeTabs.Trigger.Icon src={require('@/assets/images/tabIcons/home.png')} renderingMode="template" />
          </NativeTabs.Trigger>

          <NativeTabs.Trigger name="earnings">
            <NativeTabs.Trigger.Label>Earnings</NativeTabs.Trigger.Label>
            <NativeTabs.Trigger.Icon src={require('@/assets/images/tabIcons/explore.png')} renderingMode="template" />
          </NativeTabs.Trigger>
        </>
      )}
    </NativeTabs>
  );
}
