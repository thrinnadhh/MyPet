import React, { useCallback, useEffect, useRef, useState } from 'react';
import { Dimensions, Pressable, StyleSheet, View } from 'react-native';

import { ThemedText } from '@/components/themed-text';
import { PROMO_BANNERS, type PromoBanner } from '@/constants/content';
import { Radius, Shadows, Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';

const { width: SCREEN_WIDTH } = Dimensions.get('window');
const BANNER_WIDTH = SCREEN_WIDTH - Spacing.four * 2;

export function BannerCarousel({
  banners = PROMO_BANNERS,
  onPress,
}: {
  banners?: PromoBanner[];
  onPress?: (banner: PromoBanner) => void;
}) {
  const theme = useTheme();
  const [index, setIndex] = useState(0);
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  const scheduleNext = useCallback(() => {
    if (timerRef.current) clearTimeout(timerRef.current);
    const current = banners[index];
    if (!current) return;
    timerRef.current = setTimeout(() => {
      setIndex((prev) => (prev + 1) % banners.length);
    }, current.durationSec * 1000);
  }, [banners, index]);

  useEffect(() => {
    scheduleNext();
    return () => {
      if (timerRef.current) clearTimeout(timerRef.current);
    };
  }, [scheduleNext]);

  const active = banners[index];
  if (!active) return null;

  return (
    <View style={styles.wrap}>
      <Pressable
        onPress={() => onPress?.(active)}
        style={({ pressed }) => [
          styles.banner,
          { backgroundColor: active.accent, opacity: pressed ? 0.92 : 1 },
        ]}
        accessibilityRole="button"
        accessibilityLabel={`${active.title}. ${active.subtitle}`}
      >
        <ThemedText style={styles.title}>{active.title}</ThemedText>
        <ThemedText style={styles.subtitle}>{active.subtitle}</ThemedText>
        <ThemedText style={styles.timerHint}>{active.durationSec}s spotlight</ThemedText>
      </Pressable>
      <View style={styles.dots}>
        {banners.map((banner, dotIndex) => (
          <View
            key={banner.id}
            style={[
              styles.dot,
              {
                backgroundColor: dotIndex === index ? theme.primary : theme.border,
                width: dotIndex === index ? 18 : 6,
              },
            ]}
          />
        ))}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  wrap: { gap: Spacing.two },
  banner: {
    width: BANNER_WIDTH,
    minHeight: 112,
    borderRadius: Radius.xl,
    padding: Spacing.four,
    gap: 6,
    ...Shadows.card,
  },
  title: { color: '#FFFFFF', fontSize: 20, fontWeight: '900' },
  subtitle: { color: '#FFF7ED', fontSize: 14, lineHeight: 20, fontWeight: '600' },
  timerHint: { color: 'rgba(255,255,255,0.75)', fontSize: 11, fontWeight: '700', marginTop: 4 },
  dots: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 6,
  },
  dot: { height: 6, borderRadius: 3 },
});
