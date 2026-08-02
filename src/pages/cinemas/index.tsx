import React, { useEffect, useState } from 'react';
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
  const patchLocal = useBookingStore((s) => s.patchLocal);

  useEffect(() => {
    void catalogApi.listCinemas({ page: 1, size: 20 }).then((r) => setCinemas(r.items));
  }, []);

  const openCinema = async (c: CinemaVO) => {
    setSelected(c);
    setLoadingMovies(true);
    try {
      const date = new Date().toISOString().slice(0, 10);
      const hot = await catalogApi.listMovies({ status: 'hot_showing', page: 1, size: 20 });
      const withShows: MovieVO[] = [];
      for (const m of hot.items) {
        try {
          const shows = await catalogApi.listShows({
            cinemaId: c.cinemaId,
            movieId: m.movieId,
            date,
          });
          if (shows.items.length) withShows.push(m);
        } catch {
          /* skip */
        }
      }
      setMovies(withShows);
    } finally {
      setLoadingMovies(false);
    }
  };

  return (
    <div className="miaoyu-container">
      <h1>影院</h1>
      <div className={styles.layout}>
        <div className={styles.list}>
          {cinemas.map((c) => (
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
                    onClick={async () => {
                      const date = new Date().toISOString().slice(0, 10);
                      await patchLocal(
                        {
                          movieId: m.movieId,
                          filmTitle: m.title,
                          cinemaId: selected.cinemaId,
                          date,
                          state: 'SelectShow',
                        },
                        { debounce: false },
                      );
                      history.push(
                        `/booking/shows?movieId=${m.movieId}&cinemaId=${selected.cinemaId}&date=${date}`,
                      );
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
