import { useLocalSearchParams } from 'expo-router';
import React, { useMemo } from 'react';

import { ProviderProfileTemplate } from '@/components/commerce/ProviderProfileTemplate';
import { SHOPS_DATA } from '@/services/catalog-data';

export default function ShopProfileScreen() {
  const { id } = useLocalSearchParams<{ id: string }>();

  const shopData = useMemo(() => {
    const shopId = id ?? 'petcare-pharmacy';
    return SHOPS_DATA[shopId] ?? SHOPS_DATA['petcare-pharmacy'];
  }, [id]);

  return <ProviderProfileTemplate shop={shopData} />;
}
