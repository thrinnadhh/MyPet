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
  if (lower.endsWith('.webp')) return 'image/webp';
  return 'image/jpeg';
}

export async function uploadFileFromUri(
  localUri: string,
  filename: string,
  accessToken?: string | null,
): Promise<string> {
  if (!accessToken) throw new Error('Authentication is required to upload documents.');
  const urlResponse = await fetch(`${appConfig.apiBaseUrl}/api/v1/providers/upload-url`, {
    method: 'POST',
    headers: authHeaders(accessToken),
  });
  if (!urlResponse.ok) throw new Error('Could not get upload URL');
  const { uploadToken, uploadUrl } = (await urlResponse.json()) as {
    uploadToken: string;
    uploadUrl: string;
  };
  if (!uploadToken || !uploadUrl) throw new Error('Upload reservation is incomplete.');
  if (new URL(uploadUrl).origin !== new URL(appConfig.apiBaseUrl).origin) {
    throw new Error('Upload URL does not match the configured API origin.');
  }

  const formData = new FormData();
  formData.append('uploadToken', uploadToken);
  formData.append('file', {
    uri: localUri,
    name: filename,
    type: mimeForFilename(filename),
  } as unknown as Blob);

  const uploadResponse = await fetch(uploadUrl, {
    method: 'POST',
    body: formData,
    headers: authHeaders(accessToken),
  });
  if (!uploadResponse.ok) throw new Error('File upload failed');
  const body = (await uploadResponse.json()) as { fileUrl?: string };
  if (!body.fileUrl) throw new Error('File upload returned no document URL.');
  return body.fileUrl;
}
