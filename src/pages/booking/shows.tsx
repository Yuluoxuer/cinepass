import React, { useEffect, useMemo, useState } from 'react';
import { message } from 'antd';
import { history, useLocation } from 'umi';
import * as catalogApi from '@/api/catalog';
import type { ShowVO, MovieVO, CinemaVO } from '@/types';
import BookingProgress from '@/components/BookingProgress';
import { useBookingStore } from '@/stores/booking';
import { useAgentStore } from '@/stores/agent';
import { addLocalDays, localDateISO } from '@/utils/format';
import styles from './booking.less';

const LEVEL_TEXT: Record<string, string> = {
  ample: '充足',
  tight: '紧张',
  almost_full: '即将满座',
};

function datesAhead(n: number) {
  const list: { date: string; label: string }[] = [];
  const today = localDateISO();
  for (let i = 0; i < n; i++) {
    const date = addLocalDays(today, i);
    const [, m, d] = date.split('-').map(Number);
    const label =
      i === 0 ? `${m}/${d} 今天` : i === 1 ? `${m}/${d} 明天` : `${m}/${d}`;
    list.push({ date, label });
  }
  return list;
}

const BookingShowsPage: React.FC = () => {
  const loc = useLocation();
  const qs = new URLSearchParams(loc.search);
  const movieId = qs.get('movieId') || '';
  const cinemaId = qs.get('cinemaId') || '';
  const [date, setDate] = useState(qs.get('date') || localDateISO());
  const [shows, setShows] = useState<ShowVO[]>([]);
  const [movie, setMovie] = useState<MovieVO | null>(null);
  const [cinema, setCinema] = useState<CinemaVO | null>(null);
  const [showLoading, setShowLoading] = useState(false);
  const [contextError, setContextError] = useState('');
  const [showsError, setShowsError] = useState('');
  const [reloadVersion, setReloadVersion] = useState(0);
  const draft = useBookingStore((s) => s.draft);
  const patchLocal = useBookingStore((s) => s.patchLocal);
  const openDrawer = useAgentStore((s) => s.openDrawer);
  const dateOptions = useMemo(() => datesAhead(5), []);

  useEffect(() => {
    let active = true;
    const loadContext = async () => {
      setContextError('');
      try {
        const [loadedMovie, loadedCinema] = await Promise.all([
          movieId ? catalogApi.getMovie(movieId) : Promise.resolve(null),
          cinemaId ? catalogApi.getCinema(cinemaId) : Promise.resolve(null),
        ]);
        if (!active) return;
        setMovie(loadedMovie);
        setCinema(loadedCinema);
      } catch (error) {
        if (active) setContextError(error instanceof Error ? error.message : '影片或影院信息加载失败，请稍后重试');
      }
    };
    void loadContext();
    return () => { active = false; };
  }, [movieId, cinemaId, reloadVersion]);

  useEffect(() => {
    if (!movieId || !cinemaId || !date) return;
    let active = true;
    const loadShows = async () => {
      setShowLoading(true);
      setShowsError('');
      try {
        const result = await catalogApi.listShows({ movieId, cinemaId, date });
        if (active) {
          // 防御：仅展示可售且未开场的场次（后端也应过滤 cancelled）
          const now = Date.now();
          setShows(
            (result.items || []).filter(
              (s) => s.status !== 'cancelled' && s.status !== 'off_sale' && new Date(s.startTime).getTime() >= now,
            ),
          );
        }
      } catch (error) {
        if (active) {
          setShows([]);
          setShowsError(error instanceof Error ? error.message : '场次加载失败，请稍后重试');
        }
      } finally {
        if (active) setShowLoading(false);
      }
    };
    void loadShows();
    return () => { active = false; };
  }, [movieId, cinemaId, date, reloadVersion]);

  const onSelect = async (s: ShowVO) => {
    try {
      await patchLocal({ showId: s.showId, date, state: 'SelectSeat' }, { debounce: false });
      history.push(`/booking/seats?showId=${s.showId}`);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '暂时无法同步购票草稿，请重试');
    }
  };

  const fmt = (iso: string) => iso.slice(11, 16);

  return (
    <div className={styles.showWorkspace}>
      <main className={`${styles.showPage} miaoyu-fade-up`}>
        <div className={styles.showHeading}>
          <div>
            <span className="miaoyu-eyebrow">Booking draft · manual</span>
            <h1>{movie?.title || '选择场次'}</h1>
          </div>
          <span className={styles.synced}><i />草稿已同步</span>
        </div>
        <BookingProgress step={3} />
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
              {contextError ? <div className={styles.emptyShows}>影片或影院信息加载失败，请检查网络后重试。<button type="button" className="miaoyu-btn-secondary" onClick={() => setReloadVersion((version) => version + 1)}>重新加载</button></div> : null}
              {showLoading ? <div className={styles.emptyShows}>正在加载场次…</div> : showsError ? <div className={styles.emptyShows}>场次加载失败，请检查网络后重试。<button type="button" className="miaoyu-btn-secondary" onClick={() => setReloadVersion((version) => version + 1)}>重新加载</button></div> : shows.map((s) => (
                  <div key={s.showId} className={styles.showRow}>
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
                      onClick={() => onSelect(s)}
                    >
                      选座
                    </button>
                  </div>
              ))}
              {!contextError && !showLoading && !showsError && shows.length === 0 ? <div className={styles.emptyShows}>该日暂无可售场次，请换一天看看</div> : null}
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
