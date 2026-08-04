import axios, { type AxiosRequestConfig, type AxiosResponse } from 'axios';
import { ApiError, type ApiEnvelope } from '@/types';
import { presentApiError, redirectToRequestError } from './error';
import { getUseMock } from '@/stores/mock';
import { getAccessToken, useAuthStore } from '@/stores/auth';
import { dispatchMock } from '@/mock/router';

interface ClientRequestConfig extends AxiosRequestConfig {
  silent?: boolean;
}

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
  /** 不展示全局错误提示，由调用页面自行展示错误态。 */
  silent?: boolean;
  /** 不携带登录凭据。 */
  skipAuth?: boolean;
}

function buildQuery(params?: Record<string, unknown>): Record<string, string> {
  const query: Record<string, string> = {};
  if (!params) return query;
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined && value !== null && value !== '') query[key] = String(value);
  }
  return query;
}

function isEnvelope(value: unknown): value is ApiEnvelope<unknown> {
  return !!value && typeof value === 'object' && 'code' in value && 'message' in value;
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

/** 将后端响应统一转换为 ApiError；业务页面不再自行判断 HTTP/业务错误码。 */
function toApiError(envelope: ApiEnvelope<unknown>, httpStatus?: number): ApiError {
  const data = envelope.data && typeof envelope.data === 'object' ? envelope.data as { errorCode?: string } : undefined;
  const errorCode = data?.errorCode;
  const apiError = new ApiError(envelope.message || '请求失败', {
    code: envelope.code,
    errorCode,
    data,
    httpStatus,
  });

  if (errorCode === 'UNAUTHORIZED' || httpStatus === 401 || envelope.code === 401 || envelope.code === 40101) {
    handleUnauthorized();
  }
  return apiError;
}

function toNetworkError(error: unknown): ApiError {
  const axiosError = error as { response?: { status?: number; data?: unknown }; message?: string };
  const responseData = axiosError.response?.data;
  if (isEnvelope(responseData)) return toApiError(responseData, axiosError.response?.status);

  const status = axiosError.response?.status;
  const apiError = new ApiError(axiosError.message || '网络异常，请检查网络后重试', { httpStatus: status });
  if (status === 401) handleUnauthorized();
  return apiError;
}

function isRequestCancelled(error: unknown) {
  return axios.isCancel(error) || (error as { code?: string })?.code === 'ERR_CANCELED';
}

/** 响应拦截器是唯一的错误码处理入口，同时保留 Promise 失败语义供页面落地错误态。 */
function rejectApiError(error: ApiError, silent?: boolean): Promise<never> {
  error.silent = Boolean(silent) || Boolean(error.silent);
  presentApiError(error, silent);
  redirectToRequestError(error);
  return Promise.reject(error);
}

http.interceptors.response.use(
  (response: AxiosResponse<ApiEnvelope<unknown>>) => {
    const envelope = response.data;
    if (isEnvelope(envelope) && envelope.code !== 200) {
      return rejectApiError(toApiError(envelope, response.status), (response.config as ClientRequestConfig).silent);
    }
    if (envelope?.accessToken) useAuthStore.getState().setAccessToken(envelope.accessToken);
    return response;
  },
  (error: unknown) => {
    const config = (error as { config?: ClientRequestConfig })?.config;
    if (isRequestCancelled(error)) {
      const cancelled = new ApiError('请求已取消');
      return rejectApiError(cancelled, true);
    }
    return rejectApiError(error instanceof ApiError ? error : toNetworkError(error), config?.silent);
  },
);

function resolveMockEnvelope<T>(envelope: ApiEnvelope<T>, silent?: boolean): T | Promise<never> {
  if (!isEnvelope(envelope)) {
    return rejectApiError(new ApiError('服务返回了无效响应'), silent);
  }
  if (envelope.code !== 200) return rejectApiError(toApiError(envelope), silent);
  if (envelope.accessToken) useAuthStore.getState().setAccessToken(envelope.accessToken);
  return envelope.data;
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
    return dispatchMock({
      method,
      path: path.startsWith('/') ? path : `/${path}`,
      query: buildQuery(options.params),
      body: options.data,
      headers,
    })
      .then((envelope) => resolveMockEnvelope(envelope as ApiEnvelope<T>, options.silent))
      .catch((error: unknown) => error instanceof ApiError ? Promise.reject(error) : rejectApiError(toNetworkError(error), options.silent));
  }

  const config: ClientRequestConfig = {
    url: path,
    method: method as AxiosRequestConfig['method'],
    params: options.params,
    data: options.data,
    headers,
    silent: options.silent,
  };
  return http.request<ApiEnvelope<T>>(config)
    .then((response) => response.data.data)
    .catch((error: unknown) => {
      // 拦截器已处理提示与错误码；这里只将失败结果交回页面的错误态，不直接抛出异常。
      return Promise.reject(error instanceof ApiError ? error : toNetworkError(error));
    });
}

export function idempotencyKey(prefix = 'idemp') {
  return `${prefix}_${Date.now()}_${Math.random().toString(36).slice(2, 10)}`;
}

export const get = <T>(path: string, params?: Record<string, unknown>, opts?: RequestOptions) => request<T>(path, { ...opts, method: 'GET', params });
export const post = <T>(path: string, data?: unknown, opts?: RequestOptions) => request<T>(path, { ...opts, method: 'POST', data });
export const put = <T>(path: string, data?: unknown, opts?: RequestOptions) => request<T>(path, { ...opts, method: 'PUT', data });
export const del = <T>(path: string, params?: Record<string, unknown>, opts?: RequestOptions) => request<T>(path, { ...opts, method: 'DELETE', params });
