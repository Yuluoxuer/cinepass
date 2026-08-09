import React, { useEffect, useState } from 'react';
import { history } from 'umi';
import * as catalogApi from '@/api/catalog';
import type { WeeklyHotItem } from '@/types';
import LoadingView from '@/components/LoadingView';
import StateView from '@/components/StateView';
import styles from './ranking.less';

const RankingPage: React.FC = () => {
  const [items, setItems] = useState<WeeklyHotItem[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let active = true;
    (async () => {
      try {
        const res = await catalogApi.weeklyHot(20);
        if (active) setItems(res.items);
      } catch {
        if (active) setItems([]);
      } finally {
        if (active) setLoading(false);
      }
    })();
    return () => {
      active = false;
    };
  }, []);

  return (
    <div className="miaoyu-container">
      <span className="miaoyu-eyebrow">WEEKLY HOT</span>
      <h1 className={styles.h1}>每周热榜</h1>
      <p className={styles.sub}>本周热度最高的影片排行，综合票房、口碑与关注度。</p>

      {loading ? (
        <LoadingView text="正在加载热榜…" />
      ) : items.length === 0 ? (
        <StateView variant="empty" title="暂无热榜数据" description="榜单暂未生成，请稍后再来看看" />
      ) : (
        <ol className={styles.list}>
          {items.map((item) => {
            const m = item.movie;
            const rankClass =
              item.rank === 1 ? styles.r1 : item.rank === 2 ? styles.r2 : item.rank === 3 ? styles.r3 : '';
            return (
              <li
                key={m.movieId}
                className={styles.row}
                onClick={() => history.push(`/movies/${m.movieId}`)}
              >
                <b className={`${styles.rank} ${rankClass}`}>{String(item.rank).padStart(2, '0')}</b>
                {m.posterUrl ? (
                  <img className={styles.poster} src={m.posterUrl} alt="" loading="lazy" />
                ) : (
                  <div className={`${styles.poster} miaoyu-skeleton`} aria-hidden />
                )}
                <div className={styles.info}>
                  <span className={styles.eyebrow}>{item.heatTag || `本周第 ${item.rank} 名`}</span>
                  <h3>{m.title}</h3>
                  <p>
                    {m.genres.slice(0, 3).join(' / ') || '影片'} · {m.durationMin} 分钟
                    {m.releaseDate ? ` · ${m.releaseDate} 上映` : ''}
                  </p>
                </div>
                <span className={styles.score}>{m.rating != null ? m.rating.toFixed(1) : '—'}</span>
              </li>
            );
          })}
        </ol>
      )}
    </div>
  );
};

export default RankingPage;
