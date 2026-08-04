import { appConfig } from '@/utils/app-config';

export type GuideWriterAccess = {
  writerId: string;
  userId: string;
  email: string;
  authorName: string;
  companyName: string;
  accessStatus: 'ACTIVE' | 'REVOKED';
};

export type MerchantGuideArticle = {
  id: string;
  category: string;
  title: string;
  summary: string;
  readMinutes: number;
  authorName: string;
  companyName: string;
  likeCount: number;
  createdAt: string;
};

export type CreateGuideArticleInput = {
  category: string;
  title: string;
  summary: string;
  body: string;
  readMinutes: number;
};

function jsonHeaders(accessToken?: string | null): Record<string, string> {
  const headers: Record<string, string> = { Accept: 'application/json', 'Content-Type': 'application/json' };
  if (accessToken) headers.Authorization = `Bearer ${accessToken}`;
  return headers;
}

export async function fetchMyGuideWriterAccess(accessToken?: string | null): Promise<GuideWriterAccess> {
  const response = await fetch(`${appConfig.apiBaseUrl}/api/v1/content/guides/writers/me`, {
    headers: jsonHeaders(accessToken),
  });
  if (!response.ok) throw new Error('Guide publishing permission is not active');
  return response.json() as Promise<GuideWriterAccess>;
}

export async function fetchMyGuideArticles(accessToken?: string | null): Promise<MerchantGuideArticle[]> {
  const response = await fetch(`${appConfig.apiBaseUrl}/api/v1/content/guides/mine`, {
    headers: jsonHeaders(accessToken),
  });
  if (!response.ok) throw new Error('Could not load your health guides');
  return response.json() as Promise<MerchantGuideArticle[]>;
}

export async function createGuideArticle(
  input: CreateGuideArticleInput,
  accessToken?: string | null,
): Promise<MerchantGuideArticle> {
  const response = await fetch(`${appConfig.apiBaseUrl}/api/v1/content/guides`, {
    method: 'POST',
    headers: jsonHeaders(accessToken),
    body: JSON.stringify({ ...input, published: true }),
  });
  if (!response.ok) throw new Error('Could not publish health guide');
  return response.json() as Promise<MerchantGuideArticle>;
}
