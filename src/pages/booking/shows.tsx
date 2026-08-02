import React, { useEffect, useMemo, useState } from 'react';
import { history, useLocation } from 'umi';
import * as catalogApi from '@/api/catalog';
import type { ShowVO, MovieVO, CinemaVO } from '@/types';
import BookingProgress from '@/components/BookingProgress';
import { useBookingStore } from '@/stores/booking';
import { useAgentStore } from '@/stores/agent';
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
  const draft = useBookingStore((s) => s.draft);
  const patchLocal = useBookingStore((s) => s.patchLocal);
  const openDrawer = useAgentStore((s) => s.openDrawer);
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
    <div className={styles.showWorkspace}>
      <BookingProgress step={3} />
      <main className={`${styles.showPage} miaoyu-fade-up`}>
        <div className={styles.showHeading}>
          <div>
            <span className="miaoyu-eyebrow">Booking draft · manual</span>
            <h1>{movie?.title || '选择场次'}</h1>
          </div>
          <span className={styles.synced}><i />草稿已同步</span>
        </div>
        <div className={styles.showGrid}>
          <section className={styles.showPanel} aria-label="场次列表">
            <span className={styles.stepLabel}>Step 03</span>
            <div className={styles.panelTopline}>
              <div>
                <h2>选择场次</h2>
                <p>{cinema?.name || '当前影院'} · 选择合适的日期和开场时间</p>
              </div>
              <span className={styles.cinemaNote}>已选影院</span>
            </div>
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
            <div className={styles.showList}>
              {shows.map((s) => {
                const started = new Date(s.startTime).getTime() < Date.now();
                return (
                  <div key={s.showId} className={`${styles.showRow} ${started ? styles.disabled : ''}`}>
                    <div className={styles.time}>
                      {fmt(s.startTime)} - {fmt(s.endTime)}
                    </div>
                    <div className={styles.hall}>
                      <strong>{s.hallName}</strong>
                      <span>{s.hallName.includes('IMAX') ? 'IMAX 巨幕' : '中文 2D'}</span>
                    </div>
                    <div className={styles.price}>¥{s.price}起</div>
                    <div className={styles.remain}>{LEVEL_TEXT[s.seatRemainLevel] || s.seatRemain}</div>
                    <button
                      type="button"
                      className="miaoyu-btn-primary"
                      style={{ height: 36, padding: '0 18px', fontSize: 13 }}
                      disabled={started}
                      onClick={() => onSelect(s)}
                    >
                      选座
                    </button>
                  </div>
                );
              })}
              {shows.length === 0 ? <div className={styles.emptyShows}>该日暂无场次，请换一天看看</div> : null}
            </div>
          </section>
          <aside className={styles.draftPanel} aria-label="当前购票草稿">
            <span className={styles.stepLabel}>共享草稿</span>
            <h2>挑一个合适的场次</h2>
            <p>场次会同步到购票草稿，下一步即可选择座位。</p>
            <dl>
              <div><dt>影片</dt><dd>{movie?.title || '—'}</dd></div>
              <div><dt>影院</dt><dd>{cinema?.name || '—'}</dd></div>
              <div><dt>日期</dt><dd>{date}</dd></div>
              <div><dt>人数</dt><dd>{draft?.count || 2} 人</dd></div>
              <div><dt>来源</dt><dd>{draft?.source === 'agent' ? 'Agent 推荐' : '手动购票'}</dd></div>
            </dl>
            <button
              type="button"
              className={styles.agentPick}
              onClick={() => openDrawer({ message: `帮我挑《${movie?.title || ''}》的场次` })}
            >
              ✦ 打开 Agent 帮我挑场次
            </button>
          </aside>
        </div>
      </main>
    </div>
  );
};

export default BookingShowsPage;
