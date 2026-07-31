import React, { useEffect, useState } from 'react';
import * as catalogApi from '@/api/catalog';
import type { MovieVO, WeeklyHotItem, PersonalRecoItem } from '@/types';
import MoviePosterCard from '@/components/MoviePosterCard';
import { useAgentStore } from '@/stores/agent';
import { INTENT_CHIPS } from '@/constants';
import styles from './home.less';

const HomePage: React.FC = () => {
  const [tab, setTab] = useState<'hot_showing' | 'coming_soon'>('hot_showing');
  const [movies, setMovies] = useState<MovieVO[]>([]);
  const [hot, setHot] = useState<WeeklyHotItem[]>([]);
  const [personal, setPersonal] = useState<PersonalRecoItem[]>([]);
  const [personalMode, setPersonalMode] = useState('');
  const [loading, setLoading] = useState(true);
  const openDrawer = useAgentStore((s) => s.openDrawer);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      setLoading(true);
      try {
        const [list, weekly, reco] = await Promise.all([
          catalogApi.listMovies({ status: tab, page: 1, size: 20 }),
          catalogApi.weeklyHot(10),
          catalogApi.personalReco(10).catch(() => ({ mode: 'fallback_hot', items: [] })),
        ]);
        if (cancelled) return;
        setMovies(list.items);
        setHot(weekly.items);
        setPersonal(reco.items);
        setPersonalMode(reco.mode);
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [tab]);

  const featured = movies[0];

  return (
    <div className={styles.page}>
      <section
        className={styles.hero}
        style={
          featured?.posterUrl
            ? { ['--hero-poster' as string]: `url(${featured.posterUrl})` }
            : undefined
        }
      >
        <div className={styles.heroVeil} />
        <div className={styles.heroGlow} />
        <div className={styles.heroInner}>
          <p className={styles.brand}>妙语</p>
          <h1 className={styles.headline}>一句话，订好今晚的位子</h1>
          <p className={styles.heroDesc}>
            {featured
              ? `正在热映《${featured.title}》· 告诉 Agent 你的想法，自动完成选片到出票`
              : '告诉 Agent 你的想法，自动完成选片到出票'}
          </p>
          <div className={styles.heroActions}>
            <button type="button" className={styles.ctaPrimary} onClick={() => openDrawer()}>
              问 Agent 订票
            </button>
            {featured ? (
              <button
                type="button"
                className={styles.ctaGhost}
                onClick={() => openDrawer({ message: `帮我订《${featured.title}》` })}
              >
                订《{featured.title}》
              </button>
            ) : null}
          </div>
        </div>
      </section>

      <div className={styles.tabsWrap}>
        <div className="miaoyu-container" style={{ paddingTop: 0, paddingBottom: 0 }}>
          <div className={styles.tabs}>
            <button
              type="button"
              className={tab === 'hot_showing' ? styles.tabActive : styles.tab}
              onClick={() => setTab('hot_showing')}
            >
              正在热映
            </button>
            <button
              type="button"
              className={tab === 'coming_soon' ? styles.tabActive : styles.tab}
              onClick={() => setTab('coming_soon')}
            >
              即将上映
            </button>
          </div>
        </div>
      </div>

      <div className="miaoyu-container">
        {loading ? (
          <div className={styles.grid}>
            {Array.from({ length: 10 }).map((_, i) => (
              <div key={i} className={`miaoyu-skeleton ${styles.sk}`} />
            ))}
          </div>
        ) : movies.length === 0 ? (
          <div className={styles.empty}>暂无{tab === 'hot_showing' ? '热映' : '待映'}影片</div>
        ) : (
          <div className={styles.grid}>
            {movies.map((m, i) => (
              <div
                key={m.movieId}
                className="miaoyu-fade-up"
                style={{ animationDelay: `${i * 45}ms` }}
              >
                <MoviePosterCard movie={m} />
              </div>
            ))}
          </div>
        )}

        <section className={styles.section}>
          <div className={styles.sectionHead}>
            <h2>每周热门</h2>
            <span className={styles.sectionSub}>近 7 日热度榜</span>
          </div>
          <div className={styles.rail}>
            {hot.map((item) => (
              <MoviePosterCard
                key={item.movie.movieId}
                movie={item.movie}
                rank={item.rank}
                heatTag={item.heatTag}
                compact
              />
            ))}
          </div>
        </section>

        <section className={styles.section}>
          <div className={styles.sectionHead}>
            <h2>为你推荐</h2>
          </div>
          {personalMode === 'fallback_hot' && personal.every((p) => !p.reason) ? (
            <p className={styles.loginHint}>登录后获取个性化推荐</p>
          ) : null}
          <div className={styles.rail}>
            {personal.map((item) => (
              <MoviePosterCard
                key={item.movie.movieId}
                movie={item.movie}
                reason={item.reason}
                compact
              />
            ))}
          </div>
        </section>

        <section className={styles.intent}>
          <div className={styles.intentLeft}>
            <p className={styles.intentBrand}>妙语 · Agent</p>
            <h2>一句话帮你订</h2>
            <p>从选片到出票，对话完成</p>
          </div>
          <div className={styles.chips}>
            {INTENT_CHIPS.map((c) => (
              <button key={c} type="button" className={styles.chip} onClick={() => openDrawer({ message: c })}>
                {c}
              </button>
            ))}
          </div>
        </section>
      </div>
    </div>
  );
};

export default HomePage;
