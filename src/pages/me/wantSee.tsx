import React, { useEffect, useState } from 'react';
import { history } from 'umi';
import * as catalogApi from '@/api/catalog';
import type { MovieVO } from '@/types';
import MoviePosterCard from '@/components/MoviePosterCard';
import { useAuthStore } from '@/stores/auth';
import styles from './wantSee.less';

const SKELETON_COUNT = 6;

const WantSeePage: React.FC = () => {
  const [movies, setMovies] = useState<MovieVO[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [loginCancelled, setLoginCancelled] = useState(false);
  const [reloadVersion, setReloadVersion] = useState(0);
  const user = useAuthStore((s) => s.user);
  const openLogin = useAuthStore((s) => s.openLoginModal);

  useEffect(() => {
    let active = true;

    const loadWantSee = async () => {
      setLoading(true);
      setError('');
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
        if (active) setMovies(res.items);
      } catch {
        if (active) setError('想看列表加载失败，请稍后重试');
      } finally {
        if (active) setLoading(false);
      }
    };

    void loadWantSee();
    return () => {
      active = false;
    };
  }, [openLogin, reloadVersion, user]);

  const retry = () => setReloadVersion((version) => version + 1);

  return (
    <main className={`miaoyu-container ${styles.page}`}>
      <header className={styles.header}>
        <p className="miaoyu-eyebrow">MY WATCHLIST</p>
        <h1>想看列表</h1>
        <p>收藏想看的影片，随时回来购票。</p>
      </header>

      {loading ? (
        <div className={styles.grid} aria-label="正在加载想看影片">
          {Array.from({ length: SKELETON_COUNT }, (_, index) => (
            <div className={styles.skeletonCard} key={index}>
              <div className={`miaoyu-skeleton ${styles.skeletonPoster}`} />
              <div className={`miaoyu-skeleton ${styles.skeletonTitle}`} />
              <div className={`miaoyu-skeleton ${styles.skeletonMeta}`} />
            </div>
          ))}
        </div>
      ) : error ? (
        <section className={styles.state}>
          <h2>暂时无法加载</h2>
          <p>{error}</p>
          <button type="button" className="miaoyu-btn-primary" onClick={retry}>
            重新加载
          </button>
        </section>
      ) : loginCancelled ? (
        <section className={styles.state}>
          <h2>登录后查看想看列表</h2>
          <p>登录后可管理收藏的影片，并快速进入购票流程。</p>
          <button type="button" className="miaoyu-btn-primary" onClick={() => void openLogin()}>
            去登录
          </button>
        </section>
      ) : movies.length ? (
        <div className={styles.grid}>
          {movies.map((movie) => (
            <MoviePosterCard key={movie.movieId} movie={movie} />
          ))}
        </div>
      ) : (
        <section className={styles.state}>
          <h2>还没有想看的影片</h2>
          <p>去电影频道挑选感兴趣的影片，点击“想看”即可收藏。</p>
          <button type="button" className="miaoyu-btn-primary" onClick={() => history.push('/movies')}>
            去电影频道
          </button>
        </section>
      )}
    </main>
  );
};

export default WantSeePage;
