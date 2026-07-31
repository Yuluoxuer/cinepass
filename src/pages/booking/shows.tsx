import React, { useEffect, useMemo, useState } from 'react';
import { history, useLocation } from 'umi';
import * as catalogApi from '@/api/catalog';
import type { ShowVO, MovieVO, CinemaVO } from '@/types';
import BookingProgress from '@/components/BookingProgress';
import { useBookingStore } from '@/stores/booking';
import styles from './booking.less';

const LEVEL_TEXT: Record<string, string> = {
  ample: '充足',
  tight: '紧张',
  almost_full: '即将满座',
};

function datesAhead(n: number) {
  const list: { date: string; label: string }[] = [];
  for (let i = 0; i < n; i++) {
    const d = new Date();
    d.setHours(0, 0, 0, 0);
    d.setDate(d.getDate() + i);
    const date = d.toISOString().slice(0, 10);
    const label =
      i === 0 ? `${d.getMonth() + 1}/${d.getDate()} 今天` : i === 1 ? `${d.getMonth() + 1}/${d.getDate()} 明天` : `${d.getMonth() + 1}/${d.getDate()}`;
    list.push({ date, label });
  }
  return list;
}

const BookingShowsPage: React.FC = () => {
  const loc = useLocation();
  const qs = new URLSearchParams(loc.search);
  const movieId = qs.get('movieId') || '';
  const cinemaId = qs.get('cinemaId') || '';
  const [date, setDate] = useState(qs.get('date') || new Date().toISOString().slice(0, 10));
  const [shows, setShows] = useState<ShowVO[]>([]);
  const [movie, setMovie] = useState<MovieVO | null>(null);
  const [cinema, setCinema] = useState<CinemaVO | null>(null);
  const patchLocal = useBookingStore((s) => s.patchLocal);
  const dateOptions = useMemo(() => datesAhead(5), []);

  useEffect(() => {
    if (movieId) void catalogApi.getMovie(movieId).then(setMovie);
    if (cinemaId) void catalogApi.getCinema(cinemaId).then(setCinema);
  }, [movieId, cinemaId]);

  useEffect(() => {
    if (!movieId || !cinemaId || !date) return;
    void catalogApi.listShows({ movieId, cinemaId, date }).then((r) => setShows(r.items));
  }, [movieId, cinemaId, date]);

  const onSelect = async (s: ShowVO) => {
    await patchLocal({ showId: s.showId, date, state: 'SelectSeat' }, { debounce: false });
    history.push(`/booking/seats?showId=${s.showId}`);
  };

  const fmt = (iso: string) => iso.slice(11, 16);

  return (
    <div>
      <BookingProgress step={3} />
      <div className="miaoyu-container">
        <h2 className={styles.title}>
          {movie?.title} &gt; {cinema?.name} · 选场次
        </h2>
        <div className={styles.dates}>
          {dateOptions.map((d) => (
            <button
              key={d.date}
              type="button"
              className={date === d.date ? styles.dateActive : styles.dateBtn}
              onClick={() => setDate(d.date)}
            >
              {d.label}
            </button>
          ))}
        </div>
        <div className={styles.list}>
          {shows.map((s) => {
            const started = new Date(s.startTime).getTime() < Date.now();
            return (
              <div key={s.showId} className={`${styles.showRow} ${started ? styles.disabled : ''}`}>
                <div className={styles.time}>
                  {fmt(s.startTime)} - {fmt(s.endTime)}
                </div>
                <div className={styles.hall}>
                  {s.hallName}
                  {s.hallName.includes('IMAX') ? ' · IMAX' : ' · 中文 2D'}
                </div>
                <div className={styles.price}>¥{s.price}</div>
                <div className={styles.remain}>{LEVEL_TEXT[s.seatRemainLevel] || s.seatRemain}</div>
                <button
                  type="button"
                  className="miaoyu-btn-primary"
                  style={{ height: 32, padding: '0 14px', fontSize: 13 }}
                  disabled={started}
                  onClick={() => onSelect(s)}
                >
                  选座
                </button>
              </div>
            );
          })}
          {shows.length === 0 ? <div style={{ padding: 24, color: '#999' }}>该日暂无场次</div> : null}
        </div>
      </div>
    </div>
  );
};

export default BookingShowsPage;
