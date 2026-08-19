import React, { useState } from 'react';
import { history } from 'umi';
import { isStaffOrAdmin, useAuthStore } from '@/stores/auth';
import * as authApi from '@/api/auth';
import ChangePasswordModal from '@/components/ChangePasswordModal';
import ProfileEditModal from '@/components/ProfileEditModal';
import styles from './me.less';

const MePage: React.FC = () => {
  const user = useAuthStore((s) => s.user);
  const openLogin = useAuthStore((s) => s.openLoginModal);
  const clearAuth = useAuthStore((s) => s.clearAuth);
  const isOps = isStaffOrAdmin(user?.role);
  const [changePwdOpen, setChangePwdOpen] = useState(false);
  const [profileOpen, setProfileOpen] = useState(false);

  const logout = async () => {
    try {
      await authApi.logout();
    } catch {
      /* ignore */
    }
    clearAuth();
    history.push('/');
  };

  const menu: Array<[string, string]> = [
    ['/me/orders', '我的票夹'],
    ['/me/want-see', '想看列表'],
    ['/agent', '妙语助手'],
  ];
  if (isOps) {
    menu.push(['/admin', '运营后台']);
  }

  return (
    <div className="miaoyu-container">
      <div className={styles.card} onClick={() => !user && openLogin()}>
        <div className={styles.avatar}>{user ? user.nickname.slice(0, 1) : '?'}</div>
        <div>
          <h2>{user ? user.nickname : '未登录请点击登录'}</h2>
          {user?.phone ? <p>{user.phone}</p> : null}
          {isOps ? <p className={styles.roleTag}>已识别为运营账号</p> : null}
        </div>
      </div>
      {isOps ? (
        <button
          type="button"
          className={`miaoyu-btn-primary ${styles.adminEntry}`}
          onClick={() => history.push('/admin')}
        >
          进入运营后台
        </button>
      ) : null}
      <div className={styles.menu}>
        {menu.map(([path, label]) => (
          <div key={path} className={styles.item} onClick={() => history.push(path)}>
            <span>{label}</span>
            <span>›</span>
          </div>
        ))}
        {user ? (
          <>
            <div className={styles.item} onClick={() => setProfileOpen(true)}>
              <span>观影偏好</span>
              <span>›</span>
            </div>
            <div className={styles.item} onClick={() => setChangePwdOpen(true)}>
              <span>修改密码</span>
              <span>›</span>
            </div>
          </>
        ) : null}
      </div>
      {user ? (
        <button
          type="button"
          className={`miaoyu-btn-text ${styles.logout}`}
          onClick={logout}
        >
          退出登录
        </button>
      ) : null}
      <ChangePasswordModal
        open={changePwdOpen}
        account={user?.nickname || user?.phone || ''}
        onClose={() => setChangePwdOpen(false)}
        onChanged={() => {
          // 后端已吊销旧会话，清空本地登录态并引导用新密码重新登录
          clearAuth();
          setChangePwdOpen(false);
          openLogin();
        }}
      />
      <ProfileEditModal open={profileOpen} onClose={() => setProfileOpen(false)} />
    </div>
  );
};

export default MePage;
