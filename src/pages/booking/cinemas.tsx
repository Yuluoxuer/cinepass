import React, { useEffect, useState } from 'react';
import { history, useLocation } from 'umi';
import * as catalogApi from '@/api/catalog';
import type { CinemaVO, MovieVO } from '@/types';
import BookingProgress from '@/components/BookingProgress';
import { useBookingStore } from '@/stores/booking';
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
  const patchLocal = useBookingStore((s) => s.patchLocal);

  useEffect(() => {
    if (!movieId) return;
    void catalogApi.getMovie(movieId).then(setMovie);
    void catalogApi
      .listCinemas({ movieId, sort: 'distance', page: 1, size: 20 })
      .then((r) => setCinemas(r.items));
  }, [movieId]);

  const onSelect = async (c: CinemaVO) => {
    const date = new Date().toISOString().slice(0, 10);
    await patchLocal({ cinemaId: c.cinemaId, movieId, state: 'SelectShow' }, { debounce: false });
    history.push(
      `/booking/shows?movieId=${movieId}&cinemaId=${c.cinemaId}&date=${date}`,
    );
  };

  return (
    <div>
      <BookingProgress step={2} />
      <div className="miaoyu-container">
        <h2 className={styles.title}>
          {movie?.title || '选影院'} · 选影院
        </h2>
        <p className={styles.hint}>日期请在下一页「选场次」中选择</p>
        <div className={styles.list}>
          {cinemas.map((c) => (
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
