import axios, { type AxiosRequestConfig } from 'axios';
import { ApiError, type ApiEnvelope } from '@/types';
import { getUseMock } from '@/stores/mock';
import { getAccessToken, useAuthStore } from '@/stores/auth';
import { dispatchMock } from '@/mock/router';

function getSessionIdHeader(): string | null {
  try {
    return localStorage.getItem('miaoyu_sessionId');
  } catch {
    return null;
  }
}

const http = axios.create({
  baseURL: '/api/v1',
  timeout: 15_000,
});

export interface RequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'DELETE' | 'PATCH';
  params?: Record<string, unknown>;
  data?: unknown;
  headers?: Record<string, string>;
  /** skip auth header */
  skipAuth?: boolean;
  silent?: boolean;
}

function buildQuery(params?: Record<string, unknown>): Record<string, string> {
  const q: Record<string, string> = {};
  if (!params) return q;
  for (const [k, v] of Object.entries(params)) {
    if (v === undefined || v === null || v === '') continue;
    q[k] = String(v);
  }
  return q;
}

function maybeToast(msg: string, silent?: boolean) {
  if (silent) return;
  import('antd').then(({ message }) => message.error(msg)).catch(() => {});
}

function handleUnauthorized() {
  useAuthStore.getState().clearAuth();
  if (typeof window !== 'undefined' && window.location.pathname.startsWith('/admin')) {
    const redirect = `${window.location.pathname}${window.location.search}`;
    window.location.replace(`/admin/login?redirect=${encodeURIComponent(redirect)}`);
    return;
  }
  void useAuthStore.getState().openLoginModal();
}

export async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const method = (options.method || 'GET').toUpperCase();
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    'X-City-Id': localStorage.getItem('miaoyu_city_id') || 'city_sh',
    ...options.headers,
  };

  const sessionId = getSessionIdHeader();
  if (sessionId) headers['X-Session-Id'] = sessionId;

  if (!options.skipAuth) {
    const token = getAccessToken();
    if (token) headers.Authorization = `Bearer ${token}`;
  }

  if (getUseMock()) {
    const envelope = await dispatchMock({
      method,
      path: path.startsWith('/') ? path : `/${path}`,
      query: buildQuery(options.params),
      body: options.data,
      headers,
    });
    return unwrap(envelope, options.silent) as T;
  }

  const config: AxiosRequestConfig = {
    url: path,
    method: method as AxiosRequestConfig['method'],
    params: options.params,
    data: options.data,
    headers,
  };

  try {
    const res = await http.request<ApiEnvelope<T>>(config);
    const envelope = res.data;
    if (envelope?.accessToken) {
      useAuthStore.getState().setAccessToken(envelope.accessToken);
    }
    return unwrap(envelope, options.silent) as T;
  } catch (err: unknown) {
    const ax = err as {
      response?: { status?: number; data?: ApiEnvelope<unknown> };
      message?: string;
    };
    const status = ax.response?.status;
    const body = ax.response?.data;
    if (status === 401) {
      handleUnauthorized();
      throw new ApiError('未登录或登录已过期', {
        code: -1,
        errorCode: 'UNAUTHORIZED',
        httpStatus: 401,
      });
    }
    if (body && typeof body === 'object' && 'code' in body) {
      return unwrap(body as ApiEnvelope<T>, options.silent) as T;
    }
    const msg = ax.message || '网络异常';
    maybeToast(msg, options.silent);
    throw new ApiError(msg, { httpStatus: status });
  }
}

function unwrap<T>(envelope: ApiEnvelope<T>, silent?: boolean): T {
  if (!envelope) throw new ApiError('空响应');
  if (envelope.accessToken) {
    useAuthStore.getState().setAccessToken(envelope.accessToken);
  }
  if (envelope.code === 200) {
    return envelope.data;
  }
  const errorCode =
    envelope.data && typeof envelope.data === 'object'
      ? (envelope.data as { errorCode?: string }).errorCode
      : undefined;
  const msg = envelope.message || '请求失败';
  if (errorCode === 'UNAUTHORIZED') {
    handleUnauthorized();
  } else {
    maybeToast(msg, silent);
  }
  throw new ApiError(msg, {
    code: envelope.code,
    errorCode,
    data: envelope.data as { errorCode?: string },
  });
}

export function idempotencyKey(prefix = 'idemp') {
  return `${prefix}_${Date.now()}_${Math.random().toString(36).slice(2, 10)}`;
}

export const get = <T>(path: string, params?: Record<string, unknown>, opts?: RequestOptions) =>
  request<T>(path, { ...opts, method: 'GET', params });

export const post = <T>(path: string, data?: unknown, opts?: RequestOptions) =>
  request<T>(path, { ...opts, method: 'POST', data });

export const put = <T>(path: string, data?: unknown, opts?: RequestOptions) =>
  request<T>(path, { ...opts, method: 'PUT', data });

export const del = <T>(path: string, params?: Record<string, unknown>, opts?: RequestOptions) =>
  request<T>(path, { ...opts, method: 'DELETE', params });
