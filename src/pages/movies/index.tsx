import React, { useEffect, useState } from 'react';
import { history, useLocation } from 'umi';
import * as catalogApi from '@/api/catalog';
import type { MovieVO } from '@/types';
import styles from './movies.less';

const MoviesPage: React.FC = () => {
  const loc = useLocation();
  const params = new URLSearchParams(loc.search);
  const q = params.get('q') || '';
  const [status, setStatus] = useState<string>('hot_showing');
  const [page, setPage] = useState(1);
  const [data, setData] = useState<{ items: MovieVO[]; total: number }>({ items: [], total: 0 });
  const [loading, setLoading] = useState(true);

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
      <h1 className={styles.h1}>电影{q ? ` · 「${q}」` : ''}</h1>
      <div className={styles.tabs}>
        {[
          ['hot_showing', '热映'],
          ['coming_soon', '待映'],
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
        <div className={styles.loading}>加载中…</div>
      ) : (
        <div className={styles.list}>
          {data.items.map((m) => (
            <div key={m.movieId} className={styles.row} onClick={() => history.push(`/movies/${m.movieId}`)}>
              <img src={m.posterUrl} alt="" />
              <div className={styles.info}>
                <h3>{m.title}</h3>
                <p>
                  {m.rating != null ? `★ ${m.rating}` : '暂无评分'} · {m.genres.join(' / ')} · {m.durationMin}分钟
                </p>
                <p className={styles.desc}>{m.description}</p>
              </div>
              <button
                type="button"
                className="miaoyu-btn-primary"
                onClick={(e) => {
                  e.stopPropagation();
                  history.push(`/booking/cinemas?movieId=${m.movieId}`);
                }}
              >
                购票
              </button>
            </div>
          ))}
        </div>
      )}
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
    </div>
  );
};

export default MoviesPage;
