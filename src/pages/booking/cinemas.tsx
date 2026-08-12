import React, { useEffect, useState } from 'react';
import { message } from 'antd';
import { history, useLocation } from 'umi';
import * as catalogApi from '@/api/catalog';
import type { CinemaVO, MovieVO } from '@/types';
import BookingProgress from '@/components/BookingProgress';
import LoadingView from '@/components/LoadingView';
import StateView from '@/components/StateView';
import { useBookingStore } from '@/stores/booking';
import { useLocationStore } from '@/stores/location';
import { localDateISO } from '@/utils/format';
import styles from './booking.less';

function formatDistance(m: number | null) {
  if (m == null) return '';
  return m >= 1000 ? `${(m / 1000).toFixed(1)}km` : `${m}m`;
}

const BookingCinemasPage: React.FC = () => {
  const loc = useLocation();
  const movieId = new URLSearchParams(loc.search).get('movieId') || '';
  const [cinemas, setCinemas] = useState<CinemaVO[]>([]);
  const [movie, setMovie] = useState<MovieVO | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [reloadVersion, setReloadVersion] = useState(0);
  const [sort, setSort] = useState<'price' | 'distance'>('price');
  const locationStore = useLocationStore();
  const patchLocal = useBookingStore((s) => s.patchLocal);

  useEffect(() => {
    if (!movieId) return;
    let active = true;
    const load = async () => {
      setLoading(true);
      setError('');
      try {
        const params: {
          movieId: string;
          page: number;
          size: number;
          sort: 'price' | 'distance';
          lat?: number;
          lng?: number;
        } = { movieId, page: 1, size: 20, sort };
        const locData = useLocationStore.getState().ensureLocation();
        if (sort === 'distance' && locData) {
          params.lat = locData.lat;
          params.lng = locData.lng;
        }
        const [loadedMovie, result] = await Promise.all([
          catalogApi.getMovie(movieId),
          catalogApi.listCinemas(params),
        ]);
        if (!active) return;
        setMovie(loadedMovie);
        setCinemas(result.items);
        try {
          await patchLocal({ movieId: loadedMovie.movieId, filmTitle: loadedMovie.title, state: 'SelectCinema' }, { debounce: false });
        } catch {
          // 草稿同步失败不影响用户查看可购影院，后续选影院时仍会再次同步。
        }
      } catch (requestError) {
        if (!active) return;
        setCinemas([]);
        setError(requestError instanceof Error ? requestError.message : '影院信息加载失败，请稍后重试');
      } finally {
        if (active) setLoading(false);
      }
    };
    void load();
    return () => { active = false; };
  }, [movieId, patchLocal, reloadVersion, sort, locationStore.lat, locationStore.lng]);

  const onSortChange = async (nextSort: 'price' | 'distance') => {
    if (nextSort === 'distance') {
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
    setSort(nextSort);
  };

  const onSelect = async (c: CinemaVO) => {
    const date = localDateISO();
    try {
      await patchLocal({ cinemaId: c.cinemaId, movieId, state: 'SelectShow' }, { debounce: false });
      history.push(`/booking/shows?movieId=${movieId}&cinemaId=${c.cinemaId}&date=${date}`);
    } catch (requestError) {
      message.error(requestError instanceof Error ? requestError.message : '暂时无法同步购票草稿，请重试');
    }
  };

  return (
    <div>
      <div className="miaoyu-container">
        <h2 className={styles.title}>
          {movie?.title || '选影院'} · 选影院
        </h2>
        <p className={styles.hint}>日期请在下一页「选场次」中选择</p>
        <BookingProgress step={2} />
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
          {loading ? <LoadingView text="正在加载可购影院…" /> : error ? <StateView variant="error" title="影院信息加载失败" description={error} actionLabel="重新加载" onAction={() => setReloadVersion((version) => version + 1)} /> : cinemas.length === 0 ? <StateView variant="empty" title="暂无影院上映该电影" /> : cinemas.map((c) => (
            <div key={c.cinemaId} className={styles.row}>
              <div className={styles.info}>
                <h3>{c.name}</h3>
                <p>
                  {c.address}
                  {c.distanceMeters != null ? ` · ${formatDistance(c.distanceMeters)}` : ''}
                </p>
              </div>
              <div className={styles.right}>
                {c.minPrice != null ? <span className={styles.price}>¥{c.minPrice}起</span> : null}
            <button
              type="button"
              className="miaoyu-btn-primary"
              style={{ height: 32, padding: '0 14px', fontSize: 13 }}
              onClick={() => onSelect(c)}
            >
              选座
            </button>
              </div>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
};

export default BookingCinemasPage;
