import React, { useState } from 'react';
import { ScrollView, StyleSheet, View } from 'react-native';

import { AppBar, FilterChip, SearchField, StateView } from '@/components/foundation/primitives';
import { ScreenShell } from '@/components/foundation/screen-shell';
import { spacing } from '@/design/tokens';
import { useTranslation } from '@/i18n';

type Filter = 'all' | 'products' | 'providers' | 'guides';
export default function SearchScreen() {
  const { t } = useTranslation();
  const [query, setQuery] = useState('');
  const [filter, setFilter] = useState<Filter>('all');
  const filters: Filter[] = ['all', 'products', 'providers', 'guides'];
  return (
    <ScreenShell header={<AppBar title={t('searchFoundation.title')} subtitle={t('searchFoundation.subtitle')} />} testID="search-screen">
      <SearchField value={query} onChangeText={setQuery} placeholder={t('searchFoundation.placeholder')} />
      <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.filters}>
        {filters.map((item) => <FilterChip key={item} label={t(`searchFoundation.${item}`)} selected={filter === item} onPress={() => setFilter(item)} />)}
      </ScrollView>
      <View style={styles.state}><StateView kind="empty" title={t('searchFoundation.promptTitle')} message={t('searchFoundation.promptMessage')} /></View>
    </ScreenShell>
  );
}
const styles = StyleSheet.create({ filters: { gap: spacing.x2 }, state: { minHeight: 320 } });
