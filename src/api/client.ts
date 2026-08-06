import axios, { type AxiosRequestConfig, type AxiosResponse } from 'axios';
import { ApiError, type ApiEnvelope } from '@/types';
import { presentApiError, redirectToRequestError } from './error';
import { getAccessToken, useAuthStore } from '@/stores/auth';

interface ClientRequestConfig extends AxiosRequestConfig {
  silent?: boolean;
  skipAuthRedirect?: boolean;
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
  /** 401 时不自动弹登录框/挂起 Promise，由调用方自行处理。 */
  skipAuthRedirect?: boolean;
}

function isEnvelope(value: unknown): value is ApiEnvelope<unknown> {
  return !!value && typeof value === 'object' && 'code' in value && 'message' in value;
}

function isNotFoundError(err: ApiError): boolean {
  return err.errorCode === 'NOT_FOUND' || err.httpStatus === 404 || err.code === 404;
}

function handleUnauthorized() {
  useAuthStore.getState().clearAuth();
  if (typeof window !== 'undefined' && window.location.pathname.startsWith('/admin')) {
    // 管理端走独立登录页，避免 C 端 LoginModal；已在登录页则不再跳转
    if (!window.location.pathname.startsWith('/admin/login')) {
      const redirect = `${window.location.pathname}${window.location.search}`;
      window.location.replace(`/admin/login?redirect=${encodeURIComponent(redirect)}`);
    }
    return;
  }
  void useAuthStore.getState().openLoginModal();
}

function isUnauthorizedError(err: unknown): boolean {
  return (
    err instanceof ApiError &&
    (err.errorCode === 'UNAUTHORIZED' || err.httpStatus === 401 || err.code === 401 || err.code === 40101)
  );
}

/** 鉴权失败已跳转/弹窗后，挂起 Promise，避免未捕获 rejection 触发 React 开发红屏 */
function pendingAuthRedirect<T>(): Promise<T> {
  return new Promise<T>(() => {});
}

/** 将后端响应统一转换为 ApiError；业务页面不再自行判断 HTTP/业务错误码。 */
function toApiError(envelope: ApiEnvelope<unknown>, httpStatus?: number, skipAuthRedirect?: boolean): ApiError {
  const data = envelope.data && typeof envelope.data === 'object' ? envelope.data as { errorCode?: string } : undefined;
  const errorCode = data?.errorCode;
  const apiError = new ApiError(envelope.message || '请求失败', {
    code: envelope.code,
    errorCode,
    data,
    httpStatus,
  });

  if (!skipAuthRedirect && (errorCode === 'UNAUTHORIZED' || httpStatus === 401 || envelope.code === 401 || envelope.code === 40101)) {
    handleUnauthorized();
  }
  return apiError;
}

function toNetworkError(error: unknown, skipAuthRedirect?: boolean): ApiError {
  const axiosError = error as { response?: { status?: number; data?: unknown }; message?: string };
  const responseData = axiosError.response?.data;
  if (isEnvelope(responseData)) return toApiError(responseData, axiosError.response?.status, skipAuthRedirect);

  const status = axiosError.response?.status;
  const apiError = new ApiError(axiosError.message || '网络异常，请检查网络后重试', { httpStatus: status });
  if (!skipAuthRedirect && status === 401) handleUnauthorized();
  return apiError;
}

function isRequestCancelled(error: unknown) {
  return axios.isCancel(error) || (error as { code?: string })?.code === 'ERR_CANCELED';
}

/** 响应拦截器是唯一的错误码处理入口，同时保留 Promise 失败语义供页面落地错误态。 */
function rejectApiError(error: ApiError, silent?: boolean, skipAuthRedirect?: boolean): Promise<never> {
  const mute = Boolean(silent) || Boolean(error.silent);
  error.silent = mute;
  presentApiError(error, mute);
  redirectToRequestError(error);
  if (isUnauthorizedError(error) && !skipAuthRedirect) {
    return pendingAuthRedirect();
  }
  return Promise.reject(error);
}

http.interceptors.response.use(
  (response: AxiosResponse<ApiEnvelope<unknown>>) => {
    const envelope = response.data;
    if (isEnvelope(envelope) && envelope.code !== 200) {
      const config = response.config as ClientRequestConfig;
      const method = (config.method || 'get').toUpperCase();
      const skipAuthRedirect = Boolean(config.skipAuthRedirect);
      const apiError = toApiError(envelope, response.status, skipAuthRedirect);
      // GET 404 默认静默，由页面用空白形状占位
      const silent = Boolean(config.silent) || (method === 'GET' && isNotFoundError(apiError));
      return rejectApiError(apiError, silent, skipAuthRedirect);
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
    const skipAuthRedirect = Boolean(config?.skipAuthRedirect);
    const apiError = error instanceof ApiError ? error : toNetworkError(error, skipAuthRedirect);
    const method = (config?.method || 'get').toUpperCase();
    const silent = Boolean(config?.silent) || (method === 'GET' && isNotFoundError(apiError));
    return rejectApiError(apiError, silent, skipAuthRedirect);
  },
);

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

  const config: ClientRequestConfig = {
    url: path,
    method: method as AxiosRequestConfig['method'],
    params: options.params,
    data: options.data,
    headers,
    silent: options.silent,
    skipAuthRedirect: options.skipAuthRedirect,
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
