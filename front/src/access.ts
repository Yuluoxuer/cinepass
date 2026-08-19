/**
 * Umi access — 管理端角色
 */
import { useAuthStore } from '@/stores/auth';

export default function access() {
  const user = useAuthStore.getState().user;
  const role = user?.role;
  return {
    canAdmin: role === 'admin',
    canStaff: role === 'staff' || role === 'admin',
  };
}

export function useAccess() {
  const user = useAuthStore((s) => s.user);
  const role = user?.role;
  return {
    canAdmin: role === 'admin',
    canStaff: role === 'staff' || role === 'admin',
  };
}
