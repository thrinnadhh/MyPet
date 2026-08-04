import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  FlatList,
  NativeScrollEvent,
  NativeSyntheticEvent,
  Pressable,
  StyleSheet,
  useWindowDimensions,
  View,
} from 'react-native';
import { Image } from 'expo-image';

import { ThemedText } from '@/components/themed-text';
import { PROMO_BANNERS, type PromoBanner } from '@/constants/content';
import { Radius, Shadows, Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';

const BANNER_IMAGES = [
  'https://lh3.googleusercontent.com/aida-public/AB6AXuD6AeLhg79H-Qs95EgMOGxSG2HjJa3jlosvBXsvXE5r1rnCSeml3ETIaLhK2r2kkY8l014vbMq3CO9DvXCeF_udak6kApRBpmmenOLebPTGJDX0lNUEn2c_IpOj50T6QSmzoOy6xOPOOdMY2evfXi4nMgs9TZbCWytwpJvN8ZpQmxIi2hs9iM4G6ZZ-KAAlvmuhSUGUnp0BytQTvH3Yv5djAj6xrWVYt-TSyCg82T7EA5fDQuWEqnrBtDvcw7nJsN8Qi7g7AzJId8',
  'https://lh3.googleusercontent.com/aida-public/AB6AXuASfM4FE2gFaBN0OhgeTBOjES2tuJHOL72sgaRGgO-tENBpVYDnBud9une2vRaHplLerDL25aSx0vh9cJz69DTuFIW1egWGJvltzY6_RQn4GF_mmvas_iU801N87_y6-JFB3H3zQFxvQwyYXfEgQuV0F3BI5heqbe6Fn_zOitcCR1esBTCKNBI4NVMHkzRxgVe8mC0fGuNb2htuR3f91sz8odhN4x_vfPmxh9MBA5fDQuWEqnrBtDvcw7nJsN8Qi7g7AzJId8',
  'https://lh3.googleusercontent.com/aida-public/AB6AXuDP0Bso5NdUTuYfQmdxjGfrU18IgCERDgWEobR1RzRKk0phJmTjprXxrZ2e6MSiBzYHNllyH_O29w9XG-5RgKbo7sx9KygQhzOHoPj74CO-x1GqUujm5wEjiMN462Jd5zLvEnUDGElVK2fb7LGOI7ziuz25lE42roHK7gbnIVfpE7H3TXg8vXkDQ8iQBLfj3YiIarAthoLCqep7tQ7gY0S0wJwunX30RA3VqJ-IO10PEIcc7YJiBsxCcI9DaozwNmO6Uu30bknpLIc',
] as const;

export function BannerCarousel({
  banners = PROMO_BANNERS,
  onPress,
}: {
  banners?: PromoBanner[];
  onPress?: (banner: PromoBanner) => void;
}) {
  const theme = useTheme();
  const { width } = useWindowDimensions();
  const pageWidth = Math.max(280, width - Spacing.three * 2);
  const listRef = useRef<FlatList<PromoBanner>>(null);
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const [index, setIndex] = useState(0);
  const safeBanners = useMemo(() => (banners.length > 0 ? banners : PROMO_BANNERS), [banners]);

  const clearTimer = useCallback(() => {
    if (timerRef.current) {
      clearTimeout(timerRef.current);
      timerRef.current = null;
    }
  }, []);

  const scheduleNext = useCallback(() => {
    clearTimer();
    if (safeBanners.length < 2) return;

    const active = safeBanners[index];
    const delay = Math.max(3, active?.durationSec ?? 5) * 1000;
    timerRef.current = setTimeout(() => {
      const next = (index + 1) % safeBanners.length;
      listRef.current?.scrollToOffset({ offset: next * pageWidth, animated: true });
      setIndex(next);
    }, delay);
  }, [clearTimer, index, pageWidth, safeBanners]);

  useEffect(() => {
    scheduleNext();
    return clearTimer;
  }, [clearTimer, scheduleNext]);

  useEffect(() => {
    setIndex((current) => Math.min(current, Math.max(0, safeBanners.length - 1)));
  }, [safeBanners.length]);

  const handleMomentumEnd = useCallback(
    (event: NativeSyntheticEvent<NativeScrollEvent>) => {
      const nextIndex = Math.round(event.nativeEvent.contentOffset.x / pageWidth);
      setIndex(Math.max(0, Math.min(nextIndex, safeBanners.length - 1)));
    },
    [pageWidth, safeBanners.length],
  );

  return (
    <View style={styles.wrap}>
      <FlatList
        ref={listRef}
        data={safeBanners}
        horizontal
        pagingEnabled
        bounces={false}
        decelerationRate="fast"
        showsHorizontalScrollIndicator={false}
        keyExtractor={(item) => item.id}
        getItemLayout={(_, itemIndex) => ({ length: pageWidth, offset: pageWidth * itemIndex, index: itemIndex })}
        onScrollBeginDrag={clearTimer}
        onMomentumScrollEnd={handleMomentumEnd}
        onScrollToIndexFailed={({ index: failedIndex }) => {
          listRef.current?.scrollToOffset({ offset: failedIndex * pageWidth, animated: true });
        }}
        renderItem={({ item, index: itemIndex }) => (
          <View style={{ width: pageWidth }}>
            <Pressable
              onPress={() => onPress?.(item)}
              style={({ pressed }) => [styles.banner, pressed && styles.pressed]}
              accessibilityRole="button"
              accessibilityLabel={`${item.title}. ${item.subtitle}`}
            >
              <Image
                source={{ uri: BANNER_IMAGES[itemIndex % BANNER_IMAGES.length] }}
                style={StyleSheet.absoluteFill}
                contentFit="cover"
                transition={180}
              />
              <View style={styles.overlay} />
              <View style={styles.copy}>
                <ThemedText style={styles.eyebrow}>{itemIndex === 1 ? 'NEW ARRIVALS' : 'PET ESSENTIALS'}</ThemedText>
                <ThemedText style={styles.title} numberOfLines={2}>{item.title}</ThemedText>
                <ThemedText style={styles.subtitle} numberOfLines={2}>{item.subtitle}</ThemedText>
                <View style={[styles.cta, { backgroundColor: theme.primary }]}>
                  <ThemedText style={styles.ctaLabel}>{itemIndex === 1 ? 'Explore' : 'Shop now'}</ThemedText>
                </View>
              </View>
            </Pressable>
          </View>
        )}
      />

      <View style={styles.dots}>
        {safeBanners.map((banner, dotIndex) => (
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
  wrap: { gap: 8, overflow: 'hidden' },
  banner: {
    height: 166,
    borderRadius: Radius.xl,
    overflow: 'hidden',
    justifyContent: 'center',
    ...Shadows.card,
  },
  pressed: { opacity: 0.94 },
  overlay: {
    ...StyleSheet.absoluteFill,
    backgroundColor: 'rgba(8, 18, 33, 0.48)',
  },
  copy: {
    width: '72%',
    paddingHorizontal: Spacing.four,
    gap: 4,
  },
  eyebrow: {
    color: '#6EE7D8',
    fontSize: 11,
    lineHeight: 15,
    fontWeight: '900',
    letterSpacing: 0.8,
  },
  title: {
    color: '#FFFFFF',
    fontSize: 21,
    lineHeight: 25,
    fontWeight: '900',
  },
  subtitle: {
    color: '#F4F7FB',
    fontSize: 12,
    lineHeight: 17,
    fontWeight: '600',
  },
  cta: {
    alignSelf: 'flex-start',
    borderRadius: 8,
    marginTop: 5,
    paddingHorizontal: 14,
    paddingVertical: 8,
  },
  ctaLabel: { color: '#FFFFFF', fontSize: 12, fontWeight: '800' },
  dots: {
    minHeight: 8,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 5,
  },
  dot: { height: 6, borderRadius: 999 },
});
