import { appConfig } from '../utils/app-config';

export class ApiError extends Error {
  constructor(
    public status: number,
    message: string,
    public data?: any
  ) {
    super(message);
    this.name = 'ApiError';
  }
}

class ApiClient {
  private sessionToken: string | null = null;
  private userId: string | null = null;
  private userRole: string | null = 'CUSTOMER';
  private gatewaySecret: string = 'dev-gateway-secret-key';

  public setSessionToken(token: string | null) {
    this.sessionToken = token;
  }

  public setUserContext(userId: string | null, role: string | null = 'CUSTOMER') {
    this.userId = userId;
    this.userRole = role || 'CUSTOMER';
  }

  public setGatewaySecret(secret: string) {
    this.gatewaySecret = secret;
  }

  private getBaseUrl(): string {
    return appConfig.apiBaseUrl || 'http://localhost:8080';
  }

  private buildHeaders(customHeaders?: Record<string, string>): Record<string, string> {
    const headers: Record<string, string> = {
      'Content-Type': 'application/json',
      'X-Internal-Gateway-Secret': this.gatewaySecret,
      ...(customHeaders || {}),
    };

    if (this.sessionToken) {
      headers['Authorization'] = `Bearer ${this.sessionToken}`;
    }
    if (this.userId) {
      headers['X-User-Id'] = this.userId;
    }
    if (this.userRole) {
      headers['X-User-Role'] = this.userRole;
    }

    return headers;
  }

  public async request<T = any>(
    path: string,
    options: {
      method?: string;
      body?: any;
      headers?: Record<string, string>;
    } = {}
  ): Promise<T> {
    const { method = 'GET', body, headers: customHeaders } = options;
    const baseUrl = this.getBaseUrl();
    const url = path.startsWith('http://') || path.startsWith('https://')
      ? path
      : `${baseUrl.replace(/\/+$/, '')}/${path.replace(/^\/+/, '')}`;

    const headers = this.buildHeaders(customHeaders);
    const config: RequestInit = {
      method,
      headers,
    };

    if (body !== undefined && method !== 'GET' && method !== 'HEAD') {
      config.body = typeof body === 'string' ? body : JSON.stringify(body);
    }

    const response = await fetch(url, config);

    if (!response.ok) {
      let errorData: any = null;
      try {
        errorData = await response.json();
      } catch (e) {
        errorData = await response.text();
      }
      const message =
        typeof errorData === 'object' && errorData?.message
          ? errorData.message
          : typeof errorData === 'string' && errorData.length > 0
          ? errorData
          : `HTTP ${response.status} ${response.statusText}`;

      throw new ApiError(response.status, message, errorData);
    }

    if (response.status === 204) {
      return {} as T;
    }

    try {
      return await response.json();
    } catch (e) {
      return {} as T;
    }
  }

  public get<T = any>(path: string, headers?: Record<string, string>): Promise<T> {
    return this.request<T>(path, { method: 'GET', headers });
  }

  public post<T = any>(path: string, body?: any, headers?: Record<string, string>): Promise<T> {
    return this.request<T>(path, { method: 'POST', body, headers });
  }

  public put<T = any>(path: string, body?: any, headers?: Record<string, string>): Promise<T> {
    return this.request<T>(path, { method: 'PUT', body, headers });
  }

  public patch<T = any>(path: string, body?: any, headers?: Record<string, string>): Promise<T> {
    return this.request<T>(path, { method: 'PATCH', body, headers });
  }

  public delete<T = any>(path: string, headers?: Record<string, string>): Promise<T> {
    return this.request<T>(path, { method: 'DELETE', headers });
  }
}

export const apiClient = new ApiClient();
