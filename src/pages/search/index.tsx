import React, { useEffect, useState } from 'react';
import { history, useLocation } from 'umi';
import { message } from 'antd';
import * as catalogApi from '@/api/catalog';
import type { MovieVO, CinemaVO } from '@/types';
import { useBookingStore } from '@/stores/booking';
import { useLocationStore } from '@/stores/location';
import BlankPlaceholder from '@/components/BlankPlaceholder';
import styles from './search.less';

const PAGE_SIZE = 10;

type CinemaSort = 'default' | 'distance';

function formatDistance(m: number | null | undefined) {
  if (m == null) return '';
  return m >= 1000 ? `${(m / 1000).toFixed(1)}km` : `${m}m`;
}

const SearchPage: React.FC = () => {
  const loc = useLocation();
  const params = new URLSearchParams(loc.search);
  const q = params.get('q') || '';

  const [tab, setTab] = useState<'movie' | 'cinema'>('movie');

  // ---------- movies ----------
  const [movies, setMovies] = useState<{ items: MovieVO[]; total: number }>({ items: [], total: 0 });
  const [moviePage, setMoviePage] = useState(1);
  const [movieLoading, setMovieLoading] = useState(false);

  // ---------- cinemas ----------
  const [cinemas, setCinemas] = useState<{ items: CinemaVO[]; total: number }>({ items: [], total: 0 });
  const [cinemaPage, setCinemaPage] = useState(1);
  const [cinemaLoading, setCinemaLoading] = useState(false);
  const [cinemaSort, setCinemaSort] = useState<CinemaSort>('default');
  const locationStore = useLocationStore();

  const patchLocal = useBookingStore((s) => s.patchLocal);

  // 每次输入新的搜索词都回到影片首屏，符合搜索栏默认搜索影片的交互。
  useEffect(() => {
    setTab('movie');
    setMoviePage(1);
    setCinemaPage(1);
    setMovies({ items: [], total: 0 });
    setCinemas({ items: [], total: 0 });
  }, [q]);

  // 仅当前 tab 触发请求
  useEffect(() => {
    if (tab !== 'movie') return;
    let c = false;
    (async () => {
      setMovieLoading(true);
      try {
        const res = await catalogApi.listMovies({ q: q || undefined, page: moviePage, size: PAGE_SIZE });
        if (!c) setMovies({ items: res.items, total: res.total });
      } catch {
        if (!c) setMovies({ items: [], total: 0 });
      } finally {
        if (!c) setMovieLoading(false);
      }
    })();
    return () => { c = true; };
  }, [q, moviePage, tab]);

  useEffect(() => {
    if (tab !== 'cinema') return;
    let c = false;
    (async () => {
      setCinemaLoading(true);
      try {
        const params: {
          q?: string;
          page: number;
          size: number;
          lat?: number;
          lng?: number;
          sort?: 'distance';
        } = { q: q || undefined, page: cinemaPage, size: PAGE_SIZE };

        const locData = useLocationStore.getState().ensureLocation();
        if (cinemaSort === 'distance' && locData) {
          params.lat = locData.lat;
          params.lng = locData.lng;
          params.sort = 'distance';
        }

        const res = await catalogApi.listCinemas(params);
        if (!c) setCinemas({ items: res.items, total: res.total });
      } catch {
        if (!c) setCinemas({ items: [], total: 0 });
      } finally {
        if (!c) setCinemaLoading(false);
      }
    })();
    return () => { c = true; };
  }, [q, cinemaPage, cinemaSort, locationStore.lat, locationStore.lng, tab]);

  const onTabChange = (next: 'movie' | 'cinema') => {
    setTab(next);
  };

  const onSortChange = async (sort: CinemaSort) => {
    if (sort === 'distance') {
      const store = useLocationStore.getState();
      if (store.permission === 'idle' || store.permission === 'denied') {
        message.info('请先在顶部导航栏点击「定位」按钮授权定位后，再按距离排序');
        return;
      }
      const locData = store.ensureLocation();
      if (!locData) {
        message.info('定位已过期或不可用，请点击顶部导航栏「定位」按钮刷新位置');
        return;
      }
    }
    setCinemaSort(sort);
    setCinemaPage(1);
  };

  const moviePages = Math.max(1, Math.ceil(movies.total / PAGE_SIZE));
  const cinemaPages = Math.max(1, Math.ceil(cinemas.total / PAGE_SIZE));

  const buyTicket = async (m: MovieVO) => {
    try {
      await patchLocal(
        { movieId: m.movieId, filmTitle: m.title, state: 'SelectCinema' },
        { debounce: false },
      );
    } catch {
      /* 拦截器已提示 */
    }
    history.push(`/booking/cinemas?movieId=${m.movieId}`);
  };

  const goCinema = (cinema: CinemaVO) => {
    history.push(`/cinemas?cinemaId=${encodeURIComponent(cinema.cinemaId)}`);
  };

  // ---------- render helpers ----------
  const renderMovieList = () => {
    if (movieLoading) return <BlankPlaceholder variant="row" count={5} />;
    if (movies.items.length === 0) {
      return <p className={styles.empty}>{q ? `未找到与「${q}」相关的影片` : '请输入关键词搜索'}</p>;
    }
    return (
      <>
        <div className={styles.list}>
          {movies.items.map((m) => (
            <div
              key={m.movieId}
              className={styles.movieRow}
              onClick={() => history.push(`/movies/${m.movieId}`)}
            >
              <img src={m.posterUrl} alt="" />
              <div className={styles.info}>
                <span className={styles.eyebrow}>
                  {m.rating != null && m.rating >= 9
                    ? '编辑推荐'
                    : m.status === 'coming_soon'
                      ? '即将上映'
                      : '热映中'}
                </span>
                <h3>{m.title}</h3>
                <p>
                  {m.genres.join(' / ')} · {m.durationMin} 分钟
                  {m.rating != null ? ` · ${m.rating.toFixed(1)} 分` : ''}
                </p>
                <p className={styles.desc}>{m.description}</p>
              </div>
              <button
                type="button"
                className="miaoyu-btn-primary"
                onClick={(e) => {
                  e.stopPropagation();
                  void buyTicket(m);
                }}
              >
                选场购票
              </button>
            </div>
          ))}
        </div>
        {renderPager(moviePage, moviePages, setMoviePage)}
      </>
    );
  };

  const renderCinemaList = () => {
    if (cinemaLoading || locationStore.locating) return <BlankPlaceholder variant="row" count={5} />;
    if (cinemas.items.length === 0) {
      return <p className={styles.empty}>{q ? `未找到与「${q}」相关的影院` : '请输入关键词搜索'}</p>;
    }
    return (
      <>
        {/* 排序选择器 */}
        <div className={styles.sortBar}>
          {([
            ['default', '综合'],
            ['distance', '距离优先'],
          ] as const).map(([k, label]) => (
            <button
              key={k}
              type="button"
              className={cinemaSort === k ? styles.sortActive : ''}
              onClick={() => onSortChange(k)}
            >
              {label}
            </button>
          ))}
        </div>

        <div className={styles.list}>
          {cinemas.items.map((c) => (
            <div
              key={c.cinemaId}
              className={styles.cinemaRow}
              onClick={() => goCinema(c)}
            >
              <div className={styles.cinemaInfo}>
                <h3>{c.name}</h3>
                <p className={styles.address}>
                  {c.address}
                  {c.distanceMeters != null ? ` · ${formatDistance(c.distanceMeters)}` : ''}
                </p>
                <div className={styles.cinemaMeta}>
                  {c.minPrice != null && (
                    <span className={styles.price}>¥{c.minPrice} 起</span>
                  )}
                  {c.features?.map((f) => (
                    <span key={f} className={styles.tag}>{f}</span>
                  ))}
                </div>
              </div>
              <button
                type="button"
                className="miaoyu-btn-primary"
                onClick={(e) => {
                  e.stopPropagation();
                  goCinema(c);
                }}
              >
                查看排片
              </button>
            </div>
          ))}
        </div>
        {renderPager(cinemaPage, cinemaPages, setCinemaPage)}
      </>
    );
  };

  const renderPager = (
    page: number,
    pages: number,
    setPage: React.Dispatch<React.SetStateAction<number>>,
  ) => (
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
  );

  return (
    <div className="miaoyu-container">
      <span className="miaoyu-eyebrow">SEARCH RESULTS</span>
      <h1 className={styles.h1}>{q ? `搜索「${q}」` : '搜索'}</h1>

      {q ? (
        <>
          <div className={styles.tabs}>
            <button
              type="button"
              className={tab === 'movie' ? styles.active : ''}
              onClick={() => onTabChange('movie')}
            >
              影片
            </button>
            <button
              type="button"
              className={tab === 'cinema' ? styles.active : ''}
              onClick={() => onTabChange('cinema')}
            >
              影院
            </button>
          </div>

          {tab === 'movie' ? renderMovieList() : renderCinemaList()}
        </>
      ) : (
        <p className={styles.empty}>请输入关键词搜索电影或影院</p>
      )}
    </div>
  );
};

export default SearchPage;
