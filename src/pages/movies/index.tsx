import React, { useEffect, useState } from 'react';
import { history, useLocation } from 'umi';
import { message } from 'antd';
import * as catalogApi from '@/api/catalog';
import type { MovieVO } from '@/types';
import { useBookingStore } from '@/stores/booking';
import { useAuthStore } from '@/stores/auth';
import LoadingView from '@/components/LoadingView';
import StateView from '@/components/StateView';
import styles from './movies.less';

const MoviesPage: React.FC = () => {
  const loc = useLocation();
  const params = new URLSearchParams(loc.search);
  const q = params.get('q') || '';
  const [status, setStatus] = useState<string>('hot_showing');
  const [page, setPage] = useState(1);
  const [data, setData] = useState<{ items: MovieVO[]; total: number }>({ items: [], total: 0 });
  const [loading, setLoading] = useState(true);
  const patchLocal = useBookingStore((s) => s.patchLocal);
  const user = useAuthStore((s) => s.user);
  const openLogin = useAuthStore((s) => s.openLoginModal);
  const [wantedIds, setWantedIds] = useState<Set<string>>(new Set());

  // 加载当前用户「想看」的影片，用于即将上映影片的「想看」按钮状态
  useEffect(() => {
    if (!user) {
      setWantedIds(new Set());
      return;
    }
    let active = true;
    void catalogApi
      .listWantSee({ page: 1, size: 100 })
      .then((res) => {
        if (active) setWantedIds(new Set(res.items.map((m) => m.movieId)));
      })
      .catch(() => {});
    return () => {
      active = false;
    };
  }, [user]);

  const toggleWant = async (movie: MovieVO) => {
    let currentUser = user;
    if (!currentUser) {
      const ok = await openLogin();
      if (!ok) return;
      currentUser = useAuthStore.getState().user;
      if (!currentUser) return;
    }
    const wasWanted = wantedIds.has(movie.movieId);
    // 乐观更新，失败回滚
    setWantedIds((prev) => {
      const next = new Set(prev);
      if (wasWanted) next.delete(movie.movieId);
      else next.add(movie.movieId);
      return next;
    });
    try {
      const result = wasWanted
        ? await catalogApi.unwantSee(movie.movieId)
        : await catalogApi.wantSee(movie.movieId);
      // 以服务端返回为准
      setWantedIds((prev) => {
        const next = new Set(prev);
        if (result.wanted) next.add(movie.movieId);
        else next.delete(movie.movieId);
        return next;
      });
    } catch {
      setWantedIds((prev) => {
        const next = new Set(prev);
        if (wasWanted) next.add(movie.movieId);
        else next.delete(movie.movieId);
        return next;
      });
      message.error('操作失败，请稍后重试');
    }
  };

  useEffect(() => {
    let c = false;
    (async () => {
      setLoading(true);
      try {
        const res = await catalogApi.listMovies({
          status: status === 'all' ? undefined : status,
          q: q || undefined,
          page,
          size: 10,
        });
        if (!c) setData({ items: res.items, total: res.total });
      } catch {
        if (!c) setData({ items: [], total: 0 });
      } finally {
        if (!c) setLoading(false);
      }
    })();
    return () => {
      c = true;
    };
  }, [status, q, page]);

  const pages = Math.max(1, Math.ceil(data.total / 10));

  return (
    <div className="miaoyu-container">
      <span className="miaoyu-eyebrow">FILM ARCHIVE</span>
      <h1 className={styles.h1}>
        {q ? `搜索「${q}」` : '找一部值得出门的电影'}
      </h1>
      {!q ? <p className={styles.sub}>不是无尽滑动。先说类型、时间，或你今天的心情。</p> : null}
      <div className={styles.tabs}>
        {[
          ['hot_showing', '正在热映'],
          ['coming_soon', '即将上映'],
          ['all', '全部'],
        ].map(([k, label]) => (
          <button
            key={k}
            type="button"
            className={status === k ? styles.active : ''}
            onClick={() => {
              setStatus(k);
              setPage(1);
            }}
          >
            {label}
          </button>
        ))}
      </div>
      {loading ? (
        <LoadingView text="正在加载影片…" />
      ) : data.items.length === 0 ? (
        <StateView variant="empty" title={q ? `未找到「${q}」` : '暂无影片'} description={q ? '换个关键词试试，或看看正在热映的电影' : '当前暂无影片，请稍后再来看看'} />
      ) : (
        <div className={styles.list}>
          {data.items.map((m) => (
            <div key={m.movieId} className={styles.row} onClick={() => history.push(`/movies/${m.movieId}`)}>
              <img src={m.posterUrl} alt="" />
              <div className={styles.info}>
                <span className={styles.eyebrow}>
                  {m.status === 'coming_soon' ? '即将上映' : m.rating != null && m.rating >= 9 ? '编辑推荐' : '热映中'}
                </span>
                <h3>{m.title}</h3>
                <p>
                  {m.genres.join(' / ')} · {m.durationMin} 分钟
                  {m.rating != null ? ` · ${m.rating.toFixed(1)} 分` : ''}
                </p>
                <p className={styles.desc}>{m.description}</p>
              </div>
              {m.status === 'coming_soon' ? (
                <button
                  type="button"
                  className={wantedIds.has(m.movieId) ? `${styles.wantBtn} ${styles.wantActive}` : styles.wantBtn}
                  onClick={(e) => {
                    e.stopPropagation();
                    void toggleWant(m);
                  }}
                >
                  {wantedIds.has(m.movieId) ? '♥ 已想看' : '想看'}
                </button>
              ) : (
                <button
                  type="button"
                  className="miaoyu-btn-primary"
                  onClick={async (e) => {
                    e.stopPropagation();
                    try {
                      await patchLocal(
                        { movieId: m.movieId, filmTitle: m.title, state: 'SelectCinema' },
                        { debounce: false },
                      );
                    } catch {
                      /* 拦截器已提示 */
                    }
                    history.push(`/booking/cinemas?movieId=${m.movieId}`);
                  }}
                >
                  选场购票
                </button>
              )}
            </div>
          ))}
        </div>
      )}
      {data.items.length > 0 ? (
        <div className={styles.pager}>
          <button type="button" disabled={page <= 1} onClick={() => setPage((p) => p - 1)}>
            上一页
          </button>
          <span>
            {page} / {pages}
          </span>
          <button type="button" disabled={page >= pages} onClick={() => setPage((p) => p + 1)}>
            下一页
          </button>
        </div>
      ) : null}
    </div>
  );
};

export default MoviesPage;
