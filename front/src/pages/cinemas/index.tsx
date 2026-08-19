import React, { useEffect, useState } from 'react';
import { message } from 'antd';
import { history, useLocation } from 'umi';
import * as catalogApi from '@/api/catalog';
import type { CinemaVO, MovieVO } from '@/types';
import { useBookingStore } from '@/stores/booking';
import { useLocationStore } from '@/stores/location';
import LoadingView from '@/components/LoadingView';
import StateView from '@/components/StateView';
import { localDateISO } from '@/utils/format';
import styles from './cinemas.less';

function formatDistance(m: number | null | undefined) {
  if (m == null) return '';
  return m >= 1000 ? `${(m / 1000).toFixed(1)}km` : `${m}m`;
}

const CinemasPage: React.FC = () => {
  const location = useLocation();
  const requestedCinemaId = new URLSearchParams(location.search).get('cinemaId');
  const [cinemas, setCinemas] = useState<CinemaVO[]>([]);
  const [selected, setSelected] = useState<CinemaVO | null>(null);
  const [movies, setMovies] = useState<MovieVO[]>([]);
  const [loadingMovies, setLoadingMovies] = useState(false);
  const [loadingCinemas, setLoadingCinemas] = useState(true);
  const [cinemasError, setCinemasError] = useState('');
  const [moviesError, setMoviesError] = useState('');
  const [sort, setSort] = useState<'price' | 'distance'>('price');
  const locationStore = useLocationStore();
  const patchLocal = useBookingStore((s) => s.patchLocal);

  const loadCinemas = async () => {
    setLoadingCinemas(true);
    setCinemasError('');
    try {
      const params: {
        page: number;
        size: number;
        sort: 'price' | 'distance';
        lat?: number;
        lng?: number;
      } = { sort, page: 1, size: 20 };
      const loc = useLocationStore.getState().ensureLocation();
      if (sort === 'distance' && loc) {
        params.lat = loc.lat;
        params.lng = loc.lng;
      }
      const result = await catalogApi.listCinemas(params);
      setCinemas(result.items);
    } catch (error) {
      setCinemas([]);
      setCinemasError(error instanceof Error ? error.message : '影院列表加载失败，请稍后重试');
    } finally {
      setLoadingCinemas(false);
    }
  };

  const openCinema = async (c: CinemaVO) => {
    setSelected(c);
    setLoadingMovies(true);
    setMoviesError('');
    setMovies([]);
    try {
      const result = await catalogApi.listCinemaMovies(c.cinemaId);
      setMovies(result.items);
    } catch (error) {
      setMovies([]);
      setMoviesError(error instanceof Error ? error.message : '在售影片加载失败，请稍后重试');
    } finally {
      setLoadingMovies(false);
    }
  };

  const onSortChange = async (nextSort: 'price' | 'distance') => {
    if (nextSort === 'distance') {
      const store = useLocationStore.getState();
      if (store.permission === 'idle' || store.permission === 'denied') {
        message.info('请先在顶部导航栏点击「定位」按钮授权定位后，再按距离排序');
        return;
      }
      const loc = store.ensureLocation();
      if (!loc) {
        message.info('定位已过期或不可用，请点击顶部导航栏「定位」按钮刷新位置');
        return;
      }
    }
    setSort(nextSort);
  };

  useEffect(() => {
    void loadCinemas();
    if (requestedCinemaId) {
      void catalogApi.getCinema(requestedCinemaId)
        .then((cinema) => openCinema(cinema))
        .catch(() => undefined);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [requestedCinemaId, sort, locationStore.lat, locationStore.lng]);

  const selectShow = async (movie: MovieVO) => {
    if (!selected) return;
    const date = movie.nextShowDate || localDateISO();
    try {
      await patchLocal(
        {
          movieId: movie.movieId,
          filmTitle: movie.title,
          cinemaId: selected.cinemaId,
          date,
          state: 'SelectShow',
        },
        { debounce: false },
      );
      history.push(`/booking/shows?movieId=${movie.movieId}&cinemaId=${selected.cinemaId}&date=${date}`);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '暂时无法同步购票草稿，请重试');
    }
  };

  return (
    <div className="miaoyu-container">
      <h1>影院</h1>
      <div className={styles.layout}>
        <div className={styles.list}>
          <div className={styles.sortBar}>
            {([
              ['price', '价格优先'],
              ['distance', '距离优先'],
            ] as const).map(([k, label]) => (
              <button
                key={k}
                type="button"
                className={sort === k ? styles.sortActive : ''}
                onClick={() => onSortChange(k)}
              >
                {label}
              </button>
            ))}
          </div>
          {loadingCinemas ? (
            <LoadingView />
          ) : cinemasError ? (
            <StateView variant="error" title="影院加载失败" description={cinemasError} />
          ) : cinemas.length === 0 ? (
            <StateView variant="empty" title="暂无影院" />
          ) : (
            cinemas.map((c) => (
              <div
                key={c.cinemaId}
                className={`${styles.item} ${selected?.cinemaId === c.cinemaId ? styles.active : ''}`}
                onClick={() => openCinema(c)}
              >
                <div className={styles.itemHead}>
                  <h3>{c.name}</h3>
                  {sort === 'price'
                    ? c.minPrice != null
                      ? <span className={styles.priceLabel}>¥{c.minPrice}起</span>
                      : null
                    : c.distanceMeters != null
                      ? <span className={styles.distanceLabel}>{formatDistance(c.distanceMeters)}</span>
                      : null}
                </div>
                <p>{c.address}</p>
              </div>
            ))
          )}
        </div>
        <div className={styles.panel}>
          {!selected ? (
            <StateView variant="empty" title="请选择影院" description="从左侧选择一个影院，查看在售影片" />
          ) : loadingMovies ? (
            <LoadingView text="正在加载在售影片…" />
          ) : moviesError ? (
            <StateView variant="error" title="影片加载失败" description={moviesError} />
          ) : movies.length === 0 ? (
            <StateView variant="empty" title="暂无在售影片" description="当前影院暂无可售排片，请换一家看看" />
          ) : (
            movies.map((m) => (
              <div
                key={m.movieId}
                className={styles.movieRow}
                onClick={() => history.push(`/movies/${m.movieId}`)}
              >
                <img src={m.posterUrl} alt="" />
                <div className={styles.movieInfo}>
                  <div>
                    <h3>{m.title}</h3>
                    <p>
                      {m.genres.join(' / ')} · {m.durationMin}分钟
                      {m.nextShowDate && m.nextShowDate !== localDateISO()
                        ? ` · 最近 ${m.nextShowDate}`
                        : ''}
                    </p>
                  </div>
                  <button
                    type="button"
                    className="miaoyu-btn-primary"
                    style={{ height: 32, marginTop: 8 }}
                    onClick={(e) => {
                      e.stopPropagation();
                      void selectShow(m);
                    }}
                  >
                    选场次
                  </button>
                </div>
              </div>
            ))
          )}
        </div>
      </div>
    </div>
  );
};

export default CinemasPage;
