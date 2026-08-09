import { get, post } from './client';
import type { LoginResult, UserVO } from '@/types';

/** 表单弹窗期望内联展示错误时传 { silent: true }，避免与全局 toast 重复提示 */
export interface AuthRequestOptions {
  silent?: boolean;
  /** 401 时不触发「登录已过期」跳转/挂起；登录表单场景必须传，避免密码错误(401)卡死 loading */
  skipAuthRedirect?: boolean;
}

export function login(account: string, password: string, opts?: AuthRequestOptions) {
  return post<LoginResult>('/auth/login', { account, password }, { skipAuth: true, ...opts });
}

export function logout() {
  return post<{ loggedOut: boolean }>('/auth/logout');
}

export function me() {
  return get<UserVO>('/auth/me');
}

export function register(
  body: { nickname: string; phone: string; password: string },
  opts?: AuthRequestOptions,
) {
  return post<LoginResult>('/auth/register', body, { skipAuth: true, ...opts });
}

/** 旧密码 + 新密码；未登录时须带 account */
/**
 * 修改密码：已登录时携带 JWT（后端用 userId 定位，不依赖 account）；
 * 未登录时须带 account。前端仅登录态使用，account 作为兜底。
 */
export function changePassword(
  body: { account?: string; oldPassword: string; newPassword: string },
  opts?: AuthRequestOptions,
) {
  return post<{ changed: boolean }>('/auth/password/change', body, opts);
}

