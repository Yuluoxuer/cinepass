import React, { useEffect, useState } from 'react';
import * as catalogApi from '@/api/catalog';
import type { MovieVO } from '@/types';
import BlankPlaceholder from '@/components/BlankPlaceholder';
import MoviePosterCard from '@/components/MoviePosterCard';
import { useAuthStore } from '@/stores/auth';
import styles from './wantSee.less';

const SKELETON_COUNT = 6;

const WantSeePage: React.FC = () => {
  const [movies, setMovies] = useState<MovieVO[]>([]);
  const [loading, setLoading] = useState(true);
  const [failed, setFailed] = useState(false);
  const [loginCancelled, setLoginCancelled] = useState(false);
  const user = useAuthStore((s) => s.user);
  const openLogin = useAuthStore((s) => s.openLoginModal);

  useEffect(() => {
    let active = true;

    const loadWantSee = async () => {
      setLoading(true);
      setFailed(false);
      setLoginCancelled(false);

      if (!user) {
        const ok = await openLogin();
        if (!active) return;
        if (!ok) {
          setLoading(false);
          setLoginCancelled(true);
          return;
        }
        return;
      }

      try {
        const res = await catalogApi.listWantSee({ page: 1, size: 50 });
        if (active) {
          setMovies(res.items);
          setFailed(false);
        }
      } catch {
        if (active) {
          setMovies([]);
          setFailed(true);
        }
      } finally {
        if (active) setLoading(false);
      }
    };

    void loadWantSee();
    return () => {
      active = false;
    };
  }, [openLogin, user]);

  return (
    <main className={`miaoyu-container ${styles.page}`}>
      <header className={styles.header}>
        <p className="miaoyu-eyebrow">MY WATCHLIST</p>
        <h1>想看列表</h1>
        <p>收藏想看的影片，随时回来购票。</p>
      </header>

      {loading ? (
        <BlankPlaceholder variant="poster" count={SKELETON_COUNT} className={styles.grid} />
      ) : loginCancelled ? (
        <section className={styles.state}>
          <h2>登录后查看想看列表</h2>
          <p>登录后可管理收藏的影片，并快速进入购票流程。</p>
          <button type="button" className="miaoyu-btn-primary" onClick={() => void openLogin()}>
            去登录
          </button>
        </section>
      ) : failed || movies.length === 0 ? (
        <BlankPlaceholder variant="poster" count={SKELETON_COUNT} className={styles.grid} />
      ) : (
        <div className={styles.grid}>
          {movies.map((movie) => (
            <MoviePosterCard key={movie.movieId} movie={movie} />
          ))}
        </div>
      )}
    </main>
  );
};

export default WantSeePage;
