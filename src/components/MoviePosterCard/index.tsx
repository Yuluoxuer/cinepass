import React from 'react';
import { history } from 'umi';
import type { MovieVO } from '@/types';
import styles from './MoviePosterCard.less';

interface Props {
  movie: MovieVO;
  rank?: number;
  heatTag?: string;
  reason?: string;
  showBuy?: boolean;
  compact?: boolean;
}

const MoviePosterCard: React.FC<Props> = ({
  movie,
  rank,
  heatTag,
  reason,
  showBuy = true,
  compact,
}) => {
  const goDetail = () => history.push(`/movies/${movie.movieId}`);
  const goBuy = (e: React.MouseEvent) => {
    e.stopPropagation();
    if (movie.status === 'coming_soon') {
      history.push(`/movies/${movie.movieId}`);
    } else {
      history.push(`/booking/cinemas?movieId=${movie.movieId}`);
    }
  };

  return (
    <div className={`${styles.card} ${compact ? styles.compact : ''}`} onClick={goDetail}>
      <div className={styles.posterWrap}>
        <img src={movie.posterUrl} alt={movie.title} className={styles.poster} loading="lazy" />
        <div className={styles.posterShade} />
        {rank != null ? (
          <span className={`${styles.rank} ${rank <= 3 ? styles[`r${rank}`] : ''}`}>{rank}</span>
        ) : null}
        {heatTag ? <span className={styles.tag}>{heatTag}</span> : null}
        {movie.rating != null ? (
          <span className={styles.scoreOnPoster}>{movie.rating.toFixed(1)}</span>
        ) : (
          <span className={styles.scoreOnPosterMuted}>暂无</span>
        )}
      </div>
      <div className={styles.title} title={movie.title}>
        {movie.title}
      </div>
      <div className={styles.meta}>
        <span className={styles.genres}>{movie.genres.slice(0, 2).join(' / ')}</span>
      </div>
      {reason ? <div className={styles.reason}>{reason}</div> : null}
      {showBuy ? (
        <button type="button" className={styles.buy} onClick={goBuy}>
          {movie.status === 'coming_soon' ? '预售' : '购票'}
        </button>
      ) : null}
    </div>
  );
};

export default MoviePosterCard;
