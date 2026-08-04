import React from 'react';
import { history } from 'umi';
import { isStaffOrAdmin, useAuthStore } from '@/stores/auth';
import * as authApi from '@/api/auth';
import styles from './me.less';

const MePage: React.FC = () => {
  const user = useAuthStore((s) => s.user);
  const openLogin = useAuthStore((s) => s.openLoginModal);
  const clearAuth = useAuthStore((s) => s.clearAuth);
  const isOps = isStaffOrAdmin(user?.role);

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
          className="miaoyu-btn-primary"
          style={{ width: '100%', marginTop: 10, height: 36 }}
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
          <div className={styles.item} onClick={() => openLogin()}>
            <span>修改密码</span>
            <span>›</span>
          </div>
        ) : null}
      </div>
      {user ? (
        <button type="button" className="miaoyu-btn-text" style={{ marginTop: 24 }} onClick={logout}>
          退出登录
        </button>
      ) : null}
    </div>
  );
};

export default MePage;
