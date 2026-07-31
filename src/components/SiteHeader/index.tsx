import React, { useState } from 'react';
import { history, useLocation } from 'umi';
import { useAgentStore } from '@/stores/agent';
import { isStaffOrAdmin, useAuthStore } from '@/stores/auth';
import styles from './SiteHeader.less';

const NAV = [
  { path: '/', label: '首页', match: (p: string) => p === '/' },
  { path: '/movies', label: '电影', match: (p: string) => p.startsWith('/movies') },
  { path: '/cinemas', label: '影院', match: (p: string) => p.startsWith('/cinemas') },
  { path: '/me', label: '我的', match: (p: string) => p.startsWith('/me') },
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

  return (
    <header className={styles.header}>
      <div className={styles.inner}>
        <div className={styles.logo} onClick={() => history.push('/')}>
          <span className={styles.logoMark} aria-hidden>
            <svg viewBox="0 0 32 32" width="28" height="28">
              <rect width="32" height="32" rx="3" fill="#0f8f84" />
              <path
                d="M8 22V10l6 7.5L20 10v12h-2.4v-7.2L14 19.6 10.4 14.8V22H8z"
                fill="#f2f0eb"
              />
            </svg>
          </span>
          <span className={styles.logoText}>
            妙语<em>购票</em>
          </span>
        </div>
        <nav className={styles.nav}>
          {NAV.map((n) => (
            <a
              key={n.path}
              className={n.match(loc.pathname) ? styles.active : undefined}
              onClick={() => history.push(n.path)}
            >
              {n.label}
            </a>
          ))}
        </nav>
        <span className={styles.city}>上海</span>
        <form className={styles.searchForm} onSubmit={onSearch}>
          <span className={styles.searchIcon}>⌕</span>
          <input
            className={styles.search}
            placeholder="搜影片、影院"
            value={q}
            onChange={(e) => setQ(e.target.value)}
          />
        </form>
        {user ? (
          <button type="button" className={styles.userBtn} onClick={() => history.push('/me')}>
            {user.nickname}
          </button>
        ) : (
          <button type="button" className={styles.loginBtn} onClick={() => openLogin()}>
            登录
          </button>
        )}
        {isStaffOrAdmin(user?.role) ? (
          <button type="button" className={styles.opsBtn} onClick={() => history.push('/admin')}>
            后台
          </button>
        ) : null}
        <button type="button" className={styles.agentBtn} onClick={() => openDrawer()}>
          问 Agent
        </button>
      </div>
    </header>
  );
};

export default SiteHeader;
