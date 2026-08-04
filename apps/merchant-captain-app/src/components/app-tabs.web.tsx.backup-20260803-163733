import {
  Tabs,
  TabList,
  TabTrigger,
  TabSlot,
  TabTriggerSlotProps,
  TabListProps,
} from 'expo-router/ui';
import { Pressable, useColorScheme, View, StyleSheet } from 'react-native';

import { AppIcon } from './app-icon';
import { ThemedText } from './themed-text';
import { ThemedView } from './themed-view';
import { useAuth } from '@/context/AuthContext';

import { Colors, MaxContentWidth, Radius, Shadows, Spacing } from '@/constants/theme';

export default function AppTabs() {
  const { activeRole } = useAuth();

  return (
    <Tabs>
      <View style={styles.slot}>
        <TabSlot />
      </View>
      <TabList asChild>
        <CustomTabList>
          {activeRole === 'PROVIDER' ? (
            <>
              <TabTrigger name="index" href="/" asChild>
                <TabButton>Home</TabButton>
              </TabTrigger>
              <TabTrigger name="explore" href="/explore" asChild>
                <TabButton>Bookings</TabButton>
              </TabTrigger>
              <TabTrigger name="inventory" href="/inventory" asChild>
                <TabButton>Inventory</TabButton>
              </TabTrigger>
              <TabTrigger name="billing" href="/billing" asChild>
                <TabButton>POS</TabButton>
              </TabTrigger>
              <TabTrigger name="earnings" href="/earnings" asChild>
                <TabButton>Earnings</TabButton>
              </TabTrigger>
            </>
          ) : (
            <>
              <TabTrigger name="delivery" href="/delivery" asChild>
                <TabButton>Delivery</TabButton>
              </TabTrigger>
              <TabTrigger name="earnings" href="/earnings" asChild>
                <TabButton>Earnings</TabButton>
              </TabTrigger>
            </>
          )}
        </CustomTabList>
      </TabList>
    </Tabs>
  );
}

export function TabButton({ children, isFocused, ...props }: TabTriggerSlotProps) {
  return (
    <Pressable {...props} style={({ pressed }) => pressed && styles.pressed}>
      <ThemedView
        type={isFocused ? 'backgroundSelected' : 'backgroundElement'}
        style={styles.tabButtonView}>
        <ThemedText type="small" themeColor={isFocused ? 'text' : 'textSecondary'}>
          {children}
        </ThemedText>
      </ThemedView>
    </Pressable>
  );
}

export function CustomTabList(props: TabListProps) {
  const scheme = useColorScheme();
  const colors = Colors[scheme === 'unspecified' ? 'light' : scheme];

  return (
    <View {...props} style={styles.tabListContainer}>
      <ThemedView type="backgroundElement" style={styles.innerContainer}>
        <View style={styles.brand}>
          <AppIcon name="store" color={colors.primary} size={18} />
          <ThemedText type="smallBold" style={styles.brandText}>
            PawsNearMe Ops
          </ThemedText>
        </View>

        {props.children}
      </ThemedView>
    </View>
  );
}

const styles = StyleSheet.create({
  slot: {
    flex: 1,
    paddingTop: 82,
  },
  tabListContainer: {
    position: 'absolute',
    width: '100%',
    padding: Spacing.three,
    justifyContent: 'center',
    alignItems: 'center',
    flexDirection: 'row',
  },
  innerContainer: {
    paddingVertical: Spacing.two,
    paddingHorizontal: Spacing.three,
    borderRadius: Radius.xl,
    flexDirection: 'row',
    alignItems: 'center',
    flexGrow: 1,
    gap: Spacing.two,
    maxWidth: MaxContentWidth,
    borderWidth: 1,
    borderColor: Colors.light.border,
    ...Shadows.card,
  },
  brand: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.one,
    marginRight: 'auto',
  },
  brandText: {
    color: Colors.light.text,
  },
  pressed: {
    opacity: 0.7,
  },
  tabButtonView: {
    paddingVertical: Spacing.one,
    paddingHorizontal: Spacing.three,
    borderRadius: Spacing.three,
  },
});
