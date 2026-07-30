import { useLocalSearchParams } from 'expo-router';
import React, { useMemo } from 'react';

import { CategoryTemplate } from '@/components/commerce/CategoryTemplate';
import { SAMPLE_PRODUCTS } from '@/services/catalog-data';

const CATEGORY_NAMES: Record<string, string> = {
  food: 'Food & Nutrition',
  furniture: 'Furniture & Sleep',
  toys: 'Toys & Enrichment',
  travel: 'Travel & Apparel',
  treats: 'Treats & Chews',
  waste: 'Waste Management',
  'new-arrivals': 'New Arrivals',
  grooming: 'Grooming Services & Kits',
  hospitals: 'Hospitals & Vet Services',
  vaccinations: 'Vaccinations & Deworming',
};

export default function CategoryScreen() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const catKey = (id ?? 'food').toLowerCase();

  const title = CATEGORY_NAMES[catKey] ?? (catKey.charAt(0).toUpperCase() + catKey.slice(1));

  const products = useMemo(() => {
    if (catKey === 'new-arrivals') {
      return SAMPLE_PRODUCTS.filter((p) => p.isNewArrival);
    }
    const matched = SAMPLE_PRODUCTS.filter((p) => p.category.toLowerCase() === catKey);
    return matched.length > 0 ? matched : SAMPLE_PRODUCTS;
  }, [catKey]);

  return <CategoryTemplate title={title} subtitle="Same-day local delivery in Tirupati" products={products} />;
}
