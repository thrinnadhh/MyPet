import { appConfig } from '@/utils/app-config';

function authHeaders(accessToken?: string | null): Record<string, string> {
  const headers: Record<string, string> = { Accept: 'application/json' };
  if (accessToken) headers.Authorization = `Bearer ${accessToken}`;
  return headers;
}

function mimeForFilename(filename: string): string {
  const lower = filename.toLowerCase();
  if (lower.endsWith('.pdf')) return 'application/pdf';
  if (lower.endsWith('.png')) return 'image/png';
  return 'image/jpeg';
}

export async function uploadFileFromUri(
  localUri: string,
  filename: string,
  accessToken?: string | null,
): Promise<string> {
  const urlResponse = await fetch(
    `${appConfig.apiBaseUrl}/api/v1/providers/upload-url?filename=${encodeURIComponent(filename)}`,
    { method: 'POST', headers: authHeaders(accessToken) },
  );
  if (!urlResponse.ok) throw new Error('Could not get upload URL');
  const { uploadUrl, fileUrl } = (await urlResponse.json()) as { uploadUrl: string; fileUrl: string };

  const formData = new FormData();
  formData.append('file', {
    uri: localUri,
    name: filename,
    type: mimeForFilename(filename),
  } as unknown as Blob);

  const uploadResponse = await fetch(uploadUrl, {
    method: 'POST',
    body: formData,
    headers: { Accept: 'application/json' },
  });
  if (!uploadResponse.ok) throw new Error('File upload failed');
  return fileUrl;
}
