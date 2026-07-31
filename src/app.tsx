/**
 * 妙语购票 — Umi 运行时
 * C 端公开浏览；管理端单独守卫；401 弹 LoginModal
 */
import { message } from 'antd';
import { restoreLoginState, useAuthStore, isStaffOrAdmin } from '@/stores/auth';
import '@/styles/tokens.css';

(function bootstrap() {
  if (typeof window === 'undefined') return;
  const { accessToken, tokenExpireAt, user } = restoreLoginState();
  if (accessToken) {
    useAuthStore.setState({ accessToken, tokenExpireAt, user });
  }
})();

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
