import { useLocalSearchParams, useRouter } from 'expo-router';
import React, { useMemo } from 'react';

import { CategoryTemplate } from '@/components/commerce/CategoryTemplate';
import { AppBar, StateView } from '@/components/foundation/primitives';
import { ScreenShell } from '@/components/foundation/screen-shell';
import { getCatalogProducts, getCatalogRoute } from '@/services/route-catalog';

export default function CommerceRoute() {
  const { slug } = useLocalSearchParams<{ slug?: string | string[] }>();
  const router = useRouter();
  const definition = useMemo(() => getCatalogRoute(slug), [slug]);
  const products = useMemo(() => (definition ? getCatalogProducts(definition) : []), [definition]);

  if (!definition) {
    return (
      <ScreenShell header={<AppBar title="Pet store" />} testID="unknown-commerce-route">
        <StateView
          kind="empty"
          title="Category unavailable"
          message="This category is not available in the selected launch market yet."
          actionLabel="Back to shops"
          onAction={() => router.replace('/shop' as never)}
        />
      </ScreenShell>
    );
  }

  return <CategoryTemplate title={definition.title} subtitle={definition.subtitle} products={products} />;
}
