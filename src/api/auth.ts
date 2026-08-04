import { get, post } from './client';
import type { LoginResult, UserVO } from '@/types';

export function login(account: string, password: string) {
  return post<LoginResult>('/auth/login', { account, password }, { skipAuth: true });
}

export function logout() {
  return post<{ loggedOut: boolean }>('/auth/logout');
}

export function me() {
  return get<UserVO>('/auth/me');
}

export function register(body: { nickname: string; phone: string; password: string }) {
  return post<LoginResult>('/auth/register', body, { skipAuth: true });
}

/** 旧密码 + 新密码；未登录时须带 account */
export function changePassword(body: {
  account?: string;
  oldPassword: string;
  newPassword: string;
}) {
  return post<{ changed: boolean }>('/auth/password/change', body, { skipAuth: true });
}

export function loginResultToUser(res: LoginResult): UserVO {
  return {
    userId: res.userId,
    nickname: res.nickname,
    phone: res.phone,
    role: res.role,
    cinemaId: res.cinemaId ?? null,
    avatarUrl: null,
  };
}
