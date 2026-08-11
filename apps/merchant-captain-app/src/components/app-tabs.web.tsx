import { Link, Slot, usePathname } from 'expo-router';
import React from 'react';
import {
  Pressable,
  StyleSheet,
  Text,
  View,
  useColorScheme,
} from 'react-native';

import { AppIcon, type AppIconName } from '@/components/app-icon';
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
  icon: AppIconName;
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
      icon: 'store',
      visible: !isCaptain,
    },
    {
      name: 'admin',
      href: '/admin',
      label: 'Admin',
      icon: 'shield',
      visible: isAdmin || (isProvider && appConfig.allowDemoMode),
    },
    {
      name: 'orders',
      href: '/orders',
      label: 'Orders',
      icon: 'cart',
      visible: isProvider,
    },
    {
      name: 'bookings',
      href: '/explore',
      label: 'Bookings',
      icon: 'calendar',
      visible: isProvider,
    },
    {
      name: 'inventory',
      href: '/inventory',
      label: 'Inventory',
      icon: 'inventory',
      visible: isProvider,
    },
    {
      name: 'pos',
      href: '/billing',
      label: 'POS',
      icon: 'history',
      visible: isAdmin || isProvider,
    },
    {
      name: 'finance',
      href: '/finance',
      label: 'Finance',
      icon: 'wallet',
      visible: isProvider,
    },
    {
      name: 'delivery',
      href: '/delivery',
      label: 'Delivery',
      icon: 'truck',
      visible: isCaptain,
    },
    {
      name: 'earnings',
      href: '/earnings',
      label: isAdmin ? 'Payouts' : 'Earnings',
      icon: 'sparkle',
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
            backgroundColor: colors.backgroundElement,
            borderTopColor: colors.border,
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
                style={({ pressed }) => [
                  styles.tab,
                  selected && {
                    backgroundColor: colors.primarySoft,
                    borderColor: colors.primary,
                  },
                  pressed && styles.pressed,
                ]}
              >
                <AppIcon
                  name={tab.icon}
                  color={selected ? colors.primary : colors.textSecondary}
                  size={18}
                />
                <Text
                  style={[
                    styles.label,
                    {
                      color: selected ? colors.primary : colors.textSecondary,
                      fontWeight: selected ? '700' : '600',
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
    minHeight: 64,
    borderTopWidth: StyleSheet.hairlineWidth,
    flexDirection: 'row',
    flexWrap: 'wrap',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 6,
    paddingHorizontal: 12,
    paddingVertical: 8,
  },
  tab: {
    minHeight: 44,
    borderRadius: 22,
    borderWidth: 1,
    borderColor: 'transparent',
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 6,
    paddingHorizontal: 14,
    paddingVertical: 8,
  },
  label: {
    fontSize: 13,
  },
  pressed: {
    opacity: 0.8,
  },
});
