import React, { useEffect, useState } from 'react';
import { history, useParams } from 'umi';
import * as catalogApi from '@/api/catalog';
import type { MovieVO } from '@/types';
import { useBookingStore } from '@/stores/booking';
import { useAgentStore } from '@/stores/agent';
import { useAuthStore } from '@/stores/auth';
import styles from './detail.less';

const MovieDetailPage: React.FC = () => {
  const { movieId } = useParams<{ movieId: string }>();
  const [movie, setMovie] = useState<MovieVO | null>(null);
  const [wanted, setWanted] = useState(false);
  const [expanded, setExpanded] = useState(false);
  const patchLocal = useBookingStore((s) => s.patchLocal);
  const openDrawer = useAgentStore((s) => s.openDrawer);
  const user = useAuthStore((s) => s.user);
  const openLogin = useAuthStore((s) => s.openLoginModal);

  useEffect(() => {
    if (!movieId) return;
    void catalogApi.getMovie(movieId).then(setMovie);
  }, [movieId]);

  if (!movie) {
    return <div className="miaoyu-container">加载中…</div>;
  }

  const onBuy = async () => {
    await patchLocal(
      { movieId: movie.movieId, filmTitle: movie.title, state: 'SelectCinema' },
      { debounce: false },
    );
    history.push(`/booking/cinemas?movieId=${movie.movieId}`);
  };

  const onWant = async () => {
    if (!user) {
      const ok = await openLogin();
      if (!ok) return;
    }
    if (wanted) {
      await catalogApi.unwantSee(movie.movieId);
      setWanted(false);
      setMovie({ ...movie, wantSeeCount: Math.max(0, movie.wantSeeCount - 1) });
    } else {
      await catalogApi.wantSee(movie.movieId);
      setWanted(true);
      setMovie({ ...movie, wantSeeCount: movie.wantSeeCount + 1 });
    }
  };

  const wantLabel =
    movie.wantSeeCount > 10000
      ? `${(movie.wantSeeCount / 10000).toFixed(1)}万`
      : String(movie.wantSeeCount);

  return (
    <>
      <div className={styles.banner} style={{ backgroundImage: `url(${movie.posterUrl})` }}>
        <div className={styles.bannerMask} />
        <div className={styles.bannerInner}>
          <img src={movie.posterUrl} alt="" className={styles.poster} />
          <div className={styles.meta}>
            <div className={styles.top}>
              <h1>{movie.title}</h1>
              <button type="button" className={styles.want} onClick={onWant}>
                {wanted ? '♥' : '♡'} 想看 {wantLabel}
              </button>
            </div>
            <div className={styles.scoreRow}>
              {movie.rating != null ? (
                <span className={styles.score}>
                  <em>{movie.rating.toFixed(1)}</em> 分
                </span>
              ) : (
                <span className={styles.noScore}>暂无评分</span>
              )}
              <span className={styles.dot}>·</span>
              <span>{movie.genres.join(' / ')}</span>
              <span className={styles.dot}>·</span>
              <span>{movie.durationMin}分钟</span>
              <span className={styles.dot}>·</span>
              <span>{movie.releaseDate} 上映</span>
            </div>
            <p className={styles.cast}>主演：{movie.cast}</p>
            <p className={`${styles.desc} ${expanded ? styles.open : ''}`}>{movie.description}</p>
            <button type="button" className={styles.expand} onClick={() => setExpanded((v) => !v)}>
              {expanded ? '收起' : '展开介绍'}
            </button>
            <div>
              <button
                type="button"
                className={styles.agentLink}
                onClick={() => openDrawer({ message: `帮我订《${movie.title}》` })}
              >
                Agent 帮我订这部 →
              </button>
            </div>
          </div>
        </div>
      </div>
      <div className={styles.bar}>
        <div className={styles.barInner}>
          <div className={styles.barInfo}>
            <strong>{movie.title}</strong>
            <span>
              {movie.rating != null ? `${movie.rating}分` : '暂无评分'} · {movie.genres.join('/')}
            </span>
          </div>
          <button type="button" className="miaoyu-btn-primary" style={{ minWidth: 200 }} onClick={onBuy}>
            {movie.status === 'coming_soon' ? '预售' : '特惠购票'} ¥45起
          </button>
        </div>
      </div>
    </>
  );
};

export default MovieDetailPage;
