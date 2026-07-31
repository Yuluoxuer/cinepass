import React, { useEffect, useState } from 'react';
import * as catalogApi from '@/api/catalog';
import type { MovieVO } from '@/types';
import MoviePosterCard from '@/components/MoviePosterCard';
import { useAuthStore } from '@/stores/auth';

const WantSeePage: React.FC = () => {
  const [movies, setMovies] = useState<MovieVO[]>([]);
  const user = useAuthStore((s) => s.user);
  const openLogin = useAuthStore((s) => s.openLoginModal);

  useEffect(() => {
    (async () => {
      if (!user) {
        const ok = await openLogin();
        if (!ok) return;
      }
      const res = await catalogApi.listWantSee({ page: 1, size: 50 });
      setMovies(res.items);
    })();
  }, [user]);

  return (
    <div className="miaoyu-container">
      <h1>想看列表</h1>
      <div style={{ display: 'flex', flexWrap: 'wrap', gap: 16, marginTop: 16 }}>
        {movies.map((m) => (
          <MoviePosterCard key={m.movieId} movie={m} />
        ))}
        {!movies.length ? <p style={{ color: '#999' }}>暂无想看影片</p> : null}
      </div>
    </div>
  );
};

export default WantSeePage;
