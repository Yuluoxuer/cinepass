import React, { useState } from 'react';
import { history } from 'umi';
import { login as loginApi } from '@/api/auth';
import { getCinemaIdFromAccessToken, isStaffOrAdmin, useAuthStore } from '@/stores/auth';
import type { UserVO } from '@/types';
import styles from './LoginModal.less';

const LoginModal: React.FC = () => {
  const open = useAuthStore((s) => s.loginModalOpen);
  const close = useAuthStore((s) => s.closeLoginModal);
  const setLogin = useAuthStore((s) => s.setLogin);
  const [account, setAccount] = useState('演示用户甲');
  const [password, setPassword] = useState('demo123456');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  if (!open) return null;

  const onSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    if (!account.trim() || account.length > 64) {
      setError('账号长度 1–64');
      return;
    }
    if (password.length < 8 || password.length > 64) {
      setError('密码长度 8–64');
      return;
    }
    setLoading(true);
    try {
      const res = await loginApi(account.trim(), password);
      const user: UserVO = {
        userId: res.userId,
        nickname: res.nickname,
        phone: res.phone,
        role: res.role,
        avatarUrl: null,
        cinemaId: res.cinemaId || getCinemaIdFromAccessToken(res.accessToken),
      };
      setLogin({ accessToken: res.accessToken, expiresIn: res.expiresIn, user });
      close(true);
      // staff / admin 登录后进入运营后台，避免仍停在购票页
      if (isStaffOrAdmin(res.role)) {
        history.push('/admin');
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : '登录失败');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className={styles.mask} onClick={() => close(false)}>
      <div className={styles.modal} onClick={(e) => e.stopPropagation()}>
        <div className={styles.head}>
          <h3>登录妙语购票</h3>
          <button type="button" className={styles.close} onClick={() => close(false)}>
            ×
          </button>
        </div>
        <form onSubmit={onSubmit}>
          <label>
            账号
            <input
              value={account}
              onChange={(e) => setAccount(e.target.value)}
              placeholder="昵称或手机号"
              autoComplete="username"
            />
          </label>
          <label>
            密码
            <input
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder="8–64 位"
              autoComplete="current-password"
            />
          </label>
          {error ? <div className={styles.error}>{error}</div> : null}
          <button type="submit" className="miaoyu-btn-primary" style={{ width: '100%' }} disabled={loading}>
            {loading ? '登录中…' : '登录'}
          </button>
          <p className={styles.hint}>
            演示：演示用户甲（购票）/ 运营小李或运营小王（后台）/ 系统管理员（后台），密码均为 demo123456
          </p>
          <p className={styles.hint}>
            也可直接打开{' '}
            <a href="/admin/login" onClick={(e) => { e.preventDefault(); close(false); history.push('/admin/login'); }}>
              运营后台登录
            </a>
          </p>
        </form>
      </div>
    </div>
  );
};

export default LoginModal;
