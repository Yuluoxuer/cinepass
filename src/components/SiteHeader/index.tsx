import React, { useState, useRef, useEffect } from 'react';
import { history, useLocation } from 'umi';
import { useAgentStore } from '@/stores/agent';
import { isStaffOrAdmin, useAuthStore } from '@/stores/auth';
import { searchSuggestions } from '@/api/catalog';
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

  const [suggestions, setSuggestions] = useState<string[]>([]);
  const [showDropdown, setShowDropdown] = useState(false);
  const [loading, setLoading] = useState(false);
  const debounceRef = useRef<ReturnType<typeof setTimeout>>();
  const searchContainerRef = useRef<HTMLDivElement>(null);

  // 点击外部关闭下拉
  useEffect(() => {
    const onDown = (e: MouseEvent) => {
      if (searchContainerRef.current && !searchContainerRef.current.contains(e.target as Node)) {
        setShowDropdown(false);
      }
    };
    document.addEventListener('mousedown', onDown);
    return () => document.removeEventListener('mousedown', onDown);
  }, []);

  const onChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const value = e.target.value;
    setQ(value);
    if (debounceRef.current) clearTimeout(debounceRef.current);
    if (!value.trim()) {
      setSuggestions([]);
      setShowDropdown(false);
      return;
    }
    debounceRef.current = setTimeout(async () => {
      setLoading(true);
      setShowDropdown(true);
      try {
        const result = await searchSuggestions(value.trim());
        setSuggestions(result);
        setShowDropdown(result.length > 0);
      } catch {
        setSuggestions([]);
        setShowDropdown(false);
      } finally {
        setLoading(false);
      }
    }, 300);
  };

  // 回车：直接跳搜索页
  const onSearch = (e: React.FormEvent) => {
    e.preventDefault();
    setShowDropdown(false);
    const trimmed = q.trim();
    if (!trimmed) return;
    history.push(`/search?q=${encodeURIComponent(trimmed)}`);
  };

  // 点击联想条目：回填 → 关闭下拉 → 跳搜索
  const onSuggestionClick = (text: string) => {
    setQ(text);
    setShowDropdown(false);
    history.push(`/search?q=${encodeURIComponent(text)}`);
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
          <div className={styles.searchWrap} ref={searchContainerRef}>
            <form className={styles.search} onSubmit={onSearch}>
              <span aria-hidden>⌕</span>
              <input
                aria-label="搜索电影或影院"
                placeholder="搜索电影或影院"
                value={q}
                onChange={onChange}
                onFocus={() => suggestions.length > 0 && setShowDropdown(true)}
              />
            </form>

            {/* 下拉联想弹窗 */}
            {showDropdown && (
              <div className={styles.dropdown}>
                {loading ? (
                  <div className={styles.dropdownLoading}>搜索中…</div>
                ) : (
                  suggestions.map((text, idx) => (
                    <button
                      key={idx}
                      type="button"
                      className={styles.dropdownItem}
                      onMouseDown={() => onSuggestionClick(text)}
                    >
                      {text}
                    </button>
                  ))
                )}
              </div>
            )}
          </div>

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
