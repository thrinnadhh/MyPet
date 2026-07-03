import { appConfig } from '@/utils/app-config';

export interface PromoBanner {
  id: string;
  title: string;
  subtitle: string;
  accent: string;
  durationSec: number;
}

export interface GuideArticle {
  id: string;
  category: string;
  title: string;
  summary: string;
  readMinutes: number;
}

function headers(accessToken?: string | null): Record<string, string> {
  const result: Record<string, string> = { Accept: 'application/json' };
  if (accessToken) result.Authorization = `Bearer ${accessToken}`;
  return result;
}

export async function fetchBanners(accessToken?: string | null): Promise<PromoBanner[]> {
  if (appConfig.allowDemoMode) {
    const { PROMO_BANNERS } = await import('@/constants/content');
    return PROMO_BANNERS.map((b) => ({
      id: b.id,
      title: b.title,
      subtitle: b.subtitle,
      accent: b.accent,
      durationSec: b.durationSec,
    }));
  }
  const response = await fetch(`${appConfig.apiBaseUrl}/api/v1/content/banners`, { headers: headers(accessToken) });
  if (!response.ok) throw new Error('Could not load banners');
  return response.json() as Promise<PromoBanner[]>;
}

export async function fetchGuides(category: string | null, accessToken?: string | null): Promise<GuideArticle[]> {
  if (appConfig.allowDemoMode) {
    const { GUIDE_ARTICLES } = await import('@/constants/content');
    return GUIDE_ARTICLES.filter((g) => !category || g.category === category).map((g) => ({
      id: g.id,
      category: g.category,
      title: g.title,
      summary: g.summary,
      readMinutes: g.readMinutes,
    }));
  }
  const query = category ? `?category=${encodeURIComponent(category)}` : '';
  const response = await fetch(`${appConfig.apiBaseUrl}/api/v1/content/guides${query}`, { headers: headers(accessToken) });
  if (!response.ok) throw new Error('Could not load guides');
  return response.json() as Promise<GuideArticle[]>;
}
