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
