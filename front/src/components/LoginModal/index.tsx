import React, { useState } from 'react';
import { login as loginApi, register as registerApi } from '@/api/auth';
import { getCinemaIdFromAccessToken, isStaffOrAdmin, useAuthStore } from '@/stores/auth';
import { NICKNAME_MAX_LEN, PASSWORD_MAX_LEN, PASSWORD_MIN_LEN, PHONE_REGEX } from '@/constants/account';
import type { LoginResult, UserVO } from '@/types';
import type { LoginModalMode, RegisterForm } from './interface';
import styles from './LoginModal.less';

const EMPTY_REGISTER: RegisterForm = { nickname: '', phone: '', password: '', confirmPassword: '' };

const LoginModal: React.FC = () => {
  const open = useAuthStore((s) => s.loginModalOpen);
  const close = useAuthStore((s) => s.closeLoginModal);
  const setLogin = useAuthStore((s) => s.setLogin);
  const clearAuth = useAuthStore((s) => s.clearAuth);
  const [mode, setMode] = useState<LoginModalMode>('login');
  const [account, setAccount] = useState('演示用户甲');
  const [password, setPassword] = useState('demo123456');
  const [registerForm, setRegisterForm] = useState<RegisterForm>(EMPTY_REGISTER);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  if (!open) return null;

  const switchMode = (next: LoginModalMode) => {
    setMode(next);
    setError('');
    setRegisterForm(EMPTY_REGISTER);
  };

  const updateRegisterField =
    (key: keyof RegisterForm) =>
    (e: React.ChangeEvent<HTMLInputElement>) =>
      setRegisterForm((prev) => ({ ...prev, [key]: e.target.value }));

  const applyLogin = (res: LoginResult) => {
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
  };

  const onSubmitLogin = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    if (!account.trim() || account.trim().length > NICKNAME_MAX_LEN) {
      setError('账号长度 1–64');
      return;
    }
    if (password.length < PASSWORD_MIN_LEN || password.length > PASSWORD_MAX_LEN) {
      setError('密码长度 8–64');
      return;
    }
    setLoading(true);
    try {
      const res = await loginApi(account.trim(), password, { silent: true, skipAuthRedirect: true });
      // 拦截：staff/admin角色不能在购票页面登录，只能在 /admin/login 登录
      if (isStaffOrAdmin(res.role)) {
        // 响应拦截器已把返回 token 写入 store，需一并清除，避免残留半登录态
        clearAuth();
        setError('账号或密码错误');
        setLoading(false);
        return;
      }
      applyLogin(res);
    } catch (err) {
      setError(err instanceof Error ? err.message : '登录失败');
    } finally {
      setLoading(false);
    }
  };

  const onSubmitRegister = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    const { nickname, phone, password: pwd, confirmPassword } = registerForm;
    const nicknameTrimmed = nickname.trim();
    if (!nicknameTrimmed || nicknameTrimmed.length > NICKNAME_MAX_LEN) {
      setError('昵称长度 1–64');
      return;
    }
    if (!PHONE_REGEX.test(phone)) {
      setError('手机号格式不正确');
      return;
    }
    if (pwd.length < PASSWORD_MIN_LEN || pwd.length > PASSWORD_MAX_LEN) {
      setError('密码长度 8–64');
      return;
    }
    if (pwd !== confirmPassword) {
      setError('两次输入的密码不一致');
      return;
    }
    setLoading(true);
    try {
      const res = await registerApi(
        { nickname: nicknameTrimmed, phone, password: pwd },
        { silent: true, skipAuthRedirect: true },
      );
      applyLogin(res);
    } catch (err) {
      setError(err instanceof Error ? err.message : '注册失败');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className={styles.mask} onClick={() => close(false)}>
      <div className={styles.modal} onClick={(e) => e.stopPropagation()}>
        <div className={styles.head}>
          <h3>妙语购票</h3>
          <button type="button" className={styles.close} onClick={() => close(false)}>
            ×
          </button>
        </div>
        <div className={styles.tabs}>
          <button
            type="button"
            className={mode === 'login' ? `${styles.tab} ${styles.tabActive}` : styles.tab}
            onClick={() => switchMode('login')}
          >
            登录
          </button>
          <button
            type="button"
            className={mode === 'register' ? `${styles.tab} ${styles.tabActive}` : styles.tab}
            onClick={() => switchMode('register')}
          >
            注册
          </button>
        </div>
        {mode === 'login' ? (
          <form onSubmit={onSubmitLogin}>
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
            <button
              type="submit"
              className={`miaoyu-btn-primary ${styles.submit}`}
              disabled={loading}
            >
              {loading ? '登录中…' : '登录'}
            </button>
            <p className={styles.hint}>
              演示：演示用户甲（购票）/ 运营小李或运营小王（后台）/ 系统管理员（后台），密码均为 demo123456
            </p>
          </form>
        ) : (
          <form onSubmit={onSubmitRegister}>
            <label>
              昵称
              <input
                value={registerForm.nickname}
                onChange={updateRegisterField('nickname')}
                placeholder="1–64 位，将作为账号昵称"
                autoComplete="nickname"
              />
            </label>
            <label>
              手机号
              <input
                value={registerForm.phone}
                onChange={updateRegisterField('phone')}
                placeholder="11 位手机号"
                autoComplete="tel"
              />
            </label>
            <label>
              密码
              <input
                type="password"
                value={registerForm.password}
                onChange={updateRegisterField('password')}
                placeholder="8–64 位"
                autoComplete="new-password"
              />
            </label>
            <label>
              确认密码
              <input
                type="password"
                value={registerForm.confirmPassword}
                onChange={updateRegisterField('confirmPassword')}
                placeholder="再次输入密码"
                autoComplete="new-password"
              />
            </label>
            {error ? <div className={styles.error}>{error}</div> : null}
            <button
              type="submit"
              className={`miaoyu-btn-primary ${styles.submit}`}
              disabled={loading}
            >
              {loading ? '注册中…' : '注册并登录'}
            </button>
            <p className={styles.hint}>
              注册后即可购票；运营/管理员账号请使用后台登录页。
            </p>
          </form>
        )}
      </div>
    </div>
  );
};

export default LoginModal;
