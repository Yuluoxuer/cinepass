/**
 * 妙语购票 — Umi 运行时
 * C 端公开浏览；管理端单独守卫；401 弹 LoginModal
 */
import { message } from 'antd';
import { isApiError, presentApiError, redirectToRequestError } from '@/api/error';
import { restoreLoginState, useAuthStore, isStaffOrAdmin } from '@/stores/auth';
import { ApiError } from '@/types';
import '@/styles/tokens.css';

function isApiErrorReason(reason: unknown): reason is ApiError {
  return (
    reason instanceof ApiError ||
    (Boolean(reason) &&
      typeof reason === 'object' &&
      (reason as { name?: string }).name === 'ApiError')
  );
}

function dismissDevOverlay() {
  try {
    const overlay = (window as unknown as {
      __react_refresh_error_overlay__?: { clearRuntimeErrors?: (dismiss?: boolean) => void };
    }).__react_refresh_error_overlay__;
    overlay?.clearRuntimeErrors?.(true);
  } catch {
    /* ignore */
  }
}

/** 尽早拦截未处理 ApiError：react-refresh overlay 不尊重 preventDefault */
(function installApiRejectionGuard() {
  if (typeof window === 'undefined') return;
  const w = window as unknown as { __miaoyuApiRejectionGuard?: boolean };
  if (w.__miaoyuApiRejectionGuard) return;
  w.__miaoyuApiRejectionGuard = true;

  window.addEventListener('unhandledrejection', (ev) => {
    if (!isApiErrorReason(ev.reason)) return;
    ev.preventDefault();
    // overlay 已在同轮监听里挂上，下一帧清掉（react-refresh 不尊重 preventDefault）
    requestAnimationFrame(() => dismissDevOverlay());
  });
})();

(function bootstrap() {
  if (typeof window === 'undefined') return;
  try {
    localStorage.removeItem('miaoyu_use_mock');
  } catch {
    /* ignore */
  }
  const { accessToken, tokenExpireAt, user } = restoreLoginState();
  // 旧 Mock 签发的假 token（非 JWT 三段式）不可打真实后端，启动时清掉
  if (accessToken && accessToken.split('.').length !== 3) {
    useAuthStore.getState().clearAuth();
    return;
  }
  if (accessToken) {
    useAuthStore.setState({ accessToken, tokenExpireAt, user });
  }
})();

// 页面遗漏 catch 时，仅兜底接口错误：提示原因并转到可恢复错误页，不影响普通运行时错误排查。
if (typeof window !== 'undefined') {
  window.addEventListener('unhandledrejection', (event: PromiseRejectionEvent) => {
    if (!isApiError(event.reason)) return;
    event.preventDefault();
    if (event.reason.silent) return;
    presentApiError(event.reason);
    redirectToRequestError(event.reason);
  }, true);
}

export const request = {
  timeout: 15000,
};

function isAdminPath(pathname: string) {
  return pathname.startsWith('/admin');
}

function isPublicPath(pathname: string) {
  if (pathname.startsWith('/m/')) return true;
  if (pathname === '/admin/login') return true;
  return !isAdminPath(pathname);
}

export function onRouteChange({ location }: { location: { pathname: string } }) {
  const { pathname } = location;
  if (isPublicPath(pathname)) return;

  const { accessToken, user } = useAuthStore.getState();
  if (!accessToken) {
    window.location.replace(`/admin/login?redirect=${encodeURIComponent(pathname)}`);
    return;
  }
  if (user && !isStaffOrAdmin(user.role)) {
    message.error('无管理端权限');
    window.location.replace('/');
    return;
  }
  if (pathname.startsWith('/admin/users') && user?.role !== 'admin') {
    message.error('仅系统管理员可访问用户管理');
    window.location.replace('/admin');
  }
}
