import React, { useState } from 'react';
import { history, useLocation } from 'umi';
import { useAgentStore } from '@/stores/agent';
import { isStaffOrAdmin, useAuthStore } from '@/stores/auth';
import styles from './SiteHeader.less';

const NAV = [
  { path: '/', label: '发现', match: (p: string) => p === '/' },
  { path: '/movies', label: '电影', match: (p: string) => p.startsWith('/movies') },
  { path: '/cinemas', label: '影院', match: (p: string) => p.startsWith('/cinemas') },
  { path: '/me/orders', label: '我的票夹', match: (p: string) => p.startsWith('/me') },
];

const SiteHeader: React.FC = () => {
  const loc = useLocation();
  const [q, setQ] = useState('');
  const openDrawer = useAgentStore((s) => s.openDrawer);
  const user = useAuthStore((s) => s.user);
  const openLogin = useAuthStore((s) => s.openLoginModal);

  const onSearch = (e: React.FormEvent) => {
    e.preventDefault();
    history.push(`/movies?q=${encodeURIComponent(q.trim())}`);
  };

  const avatarChar = user?.nickname?.slice(0, 1) || '登';

  return (
    <header className={styles.header}>
      <div className={styles.inner}>
        <button type="button" className={styles.brand} onClick={() => history.push('/')}>
          <img className={styles.brandMark} src="/app-icon.png" alt="" />
          <span>妙语购票</span>
        </button>
        <nav className={styles.nav} aria-label="主导航">
          {NAV.map((n) => (
            <button
              key={n.path}
              type="button"
              className={`${styles.navLink} ${n.match(loc.pathname) ? styles.active : ''}`}
              onClick={() => history.push(n.path)}
            >
              {n.label}
            </button>
          ))}
        </nav>
        <div className={styles.actions}>
          <form className={styles.search} onSubmit={onSearch}>
            <span aria-hidden>⌕</span>
            <input
              aria-label="搜索电影或影院"
              placeholder="搜索电影或影院"
              value={q}
              onChange={(e) => setQ(e.target.value)}
            />
          </form>
          <button type="button" className={styles.agentBtn} onClick={() => openDrawer()}>
            <span className={styles.spark} aria-hidden>
              ✦
            </span>
            妙语助手
            <span className={styles.agentState}>可对话</span>
          </button>
          {isStaffOrAdmin(user?.role) ? (
            <button type="button" className={styles.opsBtn} onClick={() => history.push('/admin')}>
              运营
            </button>
          ) : null}
          <button
            type="button"
            className={styles.avatar}
            aria-label={user ? '个人账户' : '登录'}
            onClick={() => (user ? history.push('/me') : openLogin())}
          >
            {avatarChar}
          </button>
        </div>
      </div>
    </header>
  );
};

export default SiteHeader;
