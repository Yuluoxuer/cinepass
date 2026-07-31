import { create } from 'zustand';
import type { UserRole, UserVO } from '@/types';

const TOKEN_KEY = 'miaoyu_access_token';
const USER_KEY = 'miaoyu_user';
const EXPIRE_KEY = 'miaoyu_token_expire_at';

interface AuthState {
  accessToken: string | null;
  tokenExpireAt: number | null;
  user: UserVO | null;
  loginModalOpen: boolean;
  loginModalResolver: ((ok: boolean) => void) | null;
  setLogin: (payload: {
    accessToken: string;
    expiresIn: number;
    user: UserVO;
  }) => void;
  setUser: (user: UserVO) => void;
  setAccessToken: (token: string, expiresIn?: number) => void;
  clearAuth: () => void;
  openLoginModal: () => Promise<boolean>;
  closeLoginModal: (success?: boolean) => void;
}

function persist(token: string | null, user: UserVO | null, expireAt: number | null) {
  if (token) localStorage.setItem(TOKEN_KEY, token);
  else localStorage.removeItem(TOKEN_KEY);
  if (user) localStorage.setItem(USER_KEY, JSON.stringify(user));
  else localStorage.removeItem(USER_KEY);
  if (expireAt) localStorage.setItem(EXPIRE_KEY, String(expireAt));
  else localStorage.removeItem(EXPIRE_KEY);
}

export const useAuthStore = create<AuthState>((set, get) => ({
  accessToken: null,
  tokenExpireAt: null,
  user: null,
  loginModalOpen: false,
  loginModalResolver: null,

  setLogin: ({ accessToken, expiresIn, user }) => {
    const expireAt = Date.now() + expiresIn * 1000;
    persist(accessToken, user, expireAt);
    set({ accessToken, user, tokenExpireAt: expireAt });
  },

  setUser: (user) => {
    persist(get().accessToken, user, get().tokenExpireAt);
    set({ user });
  },

  setAccessToken: (token, expiresIn = 3600) => {
    const expireAt = Date.now() + expiresIn * 1000;
    persist(token, get().user, expireAt);
    set({ accessToken: token, tokenExpireAt: expireAt });
  },

  clearAuth: () => {
    persist(null, null, null);
    set({ accessToken: null, user: null, tokenExpireAt: null });
  },

  openLoginModal: () =>
    new Promise<boolean>((resolve) => {
      set({ loginModalOpen: true, loginModalResolver: resolve });
    }),

  closeLoginModal: (success = false) => {
    const resolver = get().loginModalResolver;
    set({ loginModalOpen: false, loginModalResolver: null });
    resolver?.(success);
  },
}));

export function restoreLoginState(): {
  accessToken: string | null;
  tokenExpireAt: number | null;
  user: UserVO | null;
} {
  let user: UserVO | null = null;
  try {
    const raw = localStorage.getItem(USER_KEY);
    if (raw) user = JSON.parse(raw);
  } catch {
    localStorage.removeItem(USER_KEY);
  }
  return {
    accessToken: localStorage.getItem(TOKEN_KEY),
    tokenExpireAt: Number(localStorage.getItem(EXPIRE_KEY)) || null,
    user,
  };
}

export function getAccessToken(): string | null {
  return useAuthStore.getState().accessToken || localStorage.getItem(TOKEN_KEY);
}

export function isStaffOrAdmin(role?: UserRole | null): boolean {
  return role === 'staff' || role === 'admin';
}
