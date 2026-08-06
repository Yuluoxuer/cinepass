import React, { useEffect, useState } from 'react';
import { message } from 'antd';
import { history, useLocation } from 'umi';
import * as catalogApi from '@/api/catalog';
import type { CinemaVO, MovieVO } from '@/types';
import { useBookingStore } from '@/stores/booking';
import BlankPlaceholder from '@/components/BlankPlaceholder';
import { localDateISO } from '@/utils/format';
import styles from './cinemas.less';

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

  useEffect(() => {
    void loadCinemas();
    if (requestedCinemaId) {
      void catalogApi.getCinema(requestedCinemaId)
        .then((cinema) => openCinema(cinema))
        .catch(() => undefined);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [requestedCinemaId]);

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
          {loadingCinemas ? (
            <BlankPlaceholder variant="row" count={4} />
          ) : cinemasError ? (
            <p className={styles.hint}>{cinemasError}</p>
          ) : cinemas.length === 0 ? (
            <p className={styles.hint}>暂无影院</p>
          ) : (
            cinemas.map((c) => (
              <div
                key={c.cinemaId}
                className={`${styles.item} ${selected?.cinemaId === c.cinemaId ? styles.active : ''}`}
                onClick={() => openCinema(c)}
              >
                <h3>{c.name}</h3>
                <p>{c.address}</p>
              </div>
            ))
          )}
        </div>
        <div className={styles.panel}>
          {!selected ? (
            <p className={styles.hint}>请选择左侧影院，查看在售影片</p>
          ) : loadingMovies ? (
            <BlankPlaceholder variant="row" count={3} />
          ) : moviesError ? (
            <p className={styles.hint}>{moviesError}</p>
          ) : movies.length === 0 ? (
            <p className={styles.hint}>当前暂无在售排片</p>
          ) : (
            movies.map((m) => (
              <div key={m.movieId} className={styles.movieRow}>
                <img src={m.posterUrl} alt="" />
                <div>
                  <h3>{m.title}</h3>
                  <p>
                    {m.genres.join(' / ')} · {m.durationMin}分钟
                    {m.nextShowDate && m.nextShowDate !== localDateISO()
                      ? ` · 最近 ${m.nextShowDate}`
                      : ''}
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
