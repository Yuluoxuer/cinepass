import React, { useEffect, useState } from 'react';
import { message } from 'antd';
import { history } from 'umi';
import * as catalogApi from '@/api/catalog';
import type { CinemaVO, MovieVO } from '@/types';
import { useBookingStore } from '@/stores/booking';
import styles from './cinemas.less';

const CinemasPage: React.FC = () => {
  const [cinemas, setCinemas] = useState<CinemaVO[]>([]);
  const [selected, setSelected] = useState<CinemaVO | null>(null);
  const [movies, setMovies] = useState<MovieVO[]>([]);
  const [loadingMovies, setLoadingMovies] = useState(false);
  const [loadingCinemas, setLoadingCinemas] = useState(true);
  const [cinemasError, setCinemasError] = useState('');
  const [moviesError, setMoviesError] = useState('');
  const patchLocal = useBookingStore((s) => s.patchLocal);

  const loadCinemas = async () => {
    setLoadingCinemas(true);
    setCinemasError('');
    try {
      const result = await catalogApi.listCinemas({ sort: 'price', page: 1, size: 20 });
      setCinemas(result.items);
    } catch (error) {
      setCinemas([]);
      setCinemasError(error instanceof Error ? error.message : '影院列表加载失败，请稍后重试');
    } finally {
      setLoadingCinemas(false);
    }
  };

  useEffect(() => { void loadCinemas(); }, []);

  const openCinema = async (c: CinemaVO) => {
    setSelected(c);
    setLoadingMovies(true);
    setMoviesError('');
    try {
      const date = new Date().toISOString().slice(0, 10);
      const hot = await catalogApi.listMovies({ status: 'hot_showing', page: 1, size: 20 });
      const withShows: MovieVO[] = [];
      let failedShowRequests = 0;
      for (const m of hot.items) {
        try {
          const shows = await catalogApi.listShows({
            cinemaId: c.cinemaId,
            movieId: m.movieId,
            date,
          });
          if (shows.items.length) withShows.push(m);
        } catch {
          failedShowRequests += 1;
        }
      }
      if (failedShowRequests > 0 && withShows.length === 0) {
        setMovies([]);
        setMoviesError('场次加载失败，请检查网络后重试');
      } else {
        setMovies(withShows);
      }
    } catch (error) {
      setMovies([]);
      setMoviesError(error instanceof Error ? error.message : '在售影片加载失败，请稍后重试');
    } finally {
      setLoadingMovies(false);
    }
  };

  const selectShow = async (movie: MovieVO) => {
    if (!selected) return;
    const date = new Date().toISOString().slice(0, 10);
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
          {loadingCinemas ? <p className={styles.hint}>正在加载影院…</p> : cinemasError ? <div className={styles.hint}><p>影院列表加载失败，请检查网络后重试。</p><button type="button" className="miaoyu-btn-secondary" onClick={() => void loadCinemas()}>重新加载</button></div> : cinemas.map((c) => (
            <div
              key={c.cinemaId}
              className={`${styles.item} ${selected?.cinemaId === c.cinemaId ? styles.active : ''}`}
              onClick={() => openCinema(c)}
            >
              <h3>{c.name}</h3>
              <p>{c.address}</p>
            </div>
          ))}
        </div>
        <div className={styles.panel}>
          {!selected ? (
            <p className={styles.hint}>选择影院查看在售影片</p>
          ) : loadingMovies ? (
            <p>加载中…</p>
          ) : moviesError ? (
            <div className={styles.hint}><p>在售影片加载失败，请稍后重试。</p><button type="button" className="miaoyu-btn-secondary" onClick={() => void openCinema(selected)}>重新加载</button></div>
          ) : movies.length === 0 ? (
            <p className={styles.hint}>今日暂无场次</p>
          ) : (
            movies.map((m) => (
              <div key={m.movieId} className={styles.movieRow}>
                <img src={m.posterUrl} alt="" />
                <div>
                  <h3>{m.title}</h3>
                  <p>
                    {m.genres.join(' / ')} · {m.durationMin}分钟
                  </p>
                  <button
                    type="button"
                    className="miaoyu-btn-primary"
                    style={{ height: 32, marginTop: 8 }}
                    onClick={() => void selectShow(m)}
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
