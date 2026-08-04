import { Link, Slot, usePathname } from 'expo-router';
import React from 'react';
import {
  Pressable,
  StyleSheet,
  Text,
  View,
  useColorScheme,
} from 'react-native';

import { Colors } from '@/constants/theme';
import { useAuth } from '@/context/AuthContext';
import { appConfig } from '@/utils/app-config';

type WebTab = {
  name: string;
  href:
    | '/'
    | '/admin'
    | '/billing'
    | '/earnings'
    | '/orders'
    | '/explore'
    | '/inventory'
    | '/finance'
    | '/delivery';
  label: string;
  visible: boolean;
};

export default function AppTabs() {
  const { activeRole } = useAuth();
  const pathname = usePathname();
  const scheme = useColorScheme();
  const colors = Colors[scheme === 'dark' ? 'dark' : 'light'];

  const isAdmin = activeRole === 'ADMIN';
  const isProvider = activeRole === 'PROVIDER';
  const isCaptain = activeRole === 'CAPTAIN';

  const tabs: WebTab[] = [
    {
      name: 'home',
      href: '/',
      label: 'Home',
      visible: !isCaptain,
    },
    {
      name: 'admin',
      href: '/admin',
      label: 'Admin',
      visible: isAdmin || (isProvider && appConfig.allowDemoMode),
    },
    {
      name: 'orders',
      href: '/orders',
      label: 'Orders',
      visible: isProvider,
    },
    {
      name: 'bookings',
      href: '/explore',
      label: 'Bookings',
      visible: isProvider,
    },
    {
      name: 'inventory',
      href: '/inventory',
      label: 'Inventory',
      visible: isProvider,
    },
    {
      name: 'pos',
      href: '/billing',
      label: 'POS',
      visible: isAdmin || isProvider,
    },
    {
      name: 'finance',
      href: '/finance',
      label: 'Finance',
      visible: isProvider,
    },
    {
      name: 'delivery',
      href: '/delivery',
      label: 'Delivery',
      visible: isCaptain,
    },
    {
      name: 'earnings',
      href: '/earnings',
      label: isAdmin ? 'Payouts' : 'Earnings',
      visible: isAdmin || isCaptain,
    },
  ];

  return (
    <View
      style={[
        styles.root,
        {
          backgroundColor: colors.background,
        },
      ]}
    >
      <View style={styles.content}>
        <Slot />
      </View>

      <View
        accessibilityRole="tablist"
        style={[
          styles.tabBar,
          {
            backgroundColor: colors.background,
            borderTopColor: colors.backgroundElement,
          },
        ]}
      >
        {tabs.map((tab) => {
          if (!tab.visible) return null;

          const selected =
            tab.href === '/'
              ? pathname === '/'
              : pathname === tab.href || pathname.startsWith(`${tab.href}/`);

          return (
            <Link key={tab.name} href={tab.href} asChild>
              <Pressable
                accessibilityRole="tab"
                accessibilityState={{ selected }}
                style={[
                  styles.tab,
                  selected && {
                    backgroundColor: colors.backgroundElement,
                  },
                ]}
              >
                <Text
                  style={[
                    styles.label,
                    {
                      color: colors.text,
                    },
                  ]}
                >
                  {tab.label}
                </Text>
              </Pressable>
            </Link>
          );
        })}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  root: {
    flex: 1,
  },
  content: {
    flex: 1,
    minHeight: 0,
  },
  tabBar: {
    minHeight: 58,
    borderTopWidth: StyleSheet.hairlineWidth,
    flexDirection: 'row',
    flexWrap: 'wrap',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 8,
    paddingHorizontal: 12,
    paddingVertical: 8,
  },
  tab: {
    minHeight: 40,
    minWidth: 88,
    borderRadius: 12,
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: 14,
    paddingVertical: 8,
  },
  label: {
    fontSize: 14,
    fontWeight: '600',
  },
});
