import React, { useEffect, useState } from 'react';
import * as catalogApi from '@/api/catalog';
import type { MovieVO } from '@/types';
import LoadingView from '@/components/LoadingView';
import StateView from '@/components/StateView';
import MoviePosterCard from '@/components/MoviePosterCard';
import { useAuthStore } from '@/stores/auth';
import styles from './wantSee.less';

const WantSeePage: React.FC = () => {
  const [movies, setMovies] = useState<MovieVO[]>([]);
  const [loading, setLoading] = useState(true);
  const [failed, setFailed] = useState(false);
  const [loginCancelled, setLoginCancelled] = useState(false);
  const [reloadVersion, setReloadVersion] = useState(0);
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
  }, [openLogin, user, reloadVersion]);

  return (
    <main className={`miaoyu-container ${styles.page}`}>
      <header className={styles.header}>
        <p className="miaoyu-eyebrow">MY WATCHLIST</p>
        <h1>想看列表</h1>
        <p>收藏想看的影片，随时回来购票。</p>
      </header>

      {loading ? (
        <LoadingView text="正在加载想看列表…" />
      ) : loginCancelled ? (
        <StateView
          variant="login"
          title="登录后查看想看列表"
          description="登录后可管理收藏的影片，并快速进入购票流程。"
          actionLabel="去登录"
          onAction={() => void openLogin()}
        />
      ) : failed ? (
        <StateView
          variant="error"
          title="加载失败"
          description="想看列表加载失败，请稍后重试。"
          actionLabel="重试"
          onAction={() => setReloadVersion((v) => v + 1)}
        />
      ) : movies.length === 0 ? (
        <StateView
          variant="empty"
          title="暂无想看电影"
          description="收藏喜欢的影片后，随时来这里购票。"
        />
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
