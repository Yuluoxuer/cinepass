import React, { useEffect, useState } from 'react';
import { history } from 'umi';
import * as catalogApi from '@/api/catalog';
import type { MovieVO, WeeklyHotItem, PersonalRecoItem } from '@/types';
import { useAgentStore } from '@/stores/agent';
import { useBookingStore } from '@/stores/booking';
import { INTENT_CHIPS } from '@/constants';
import styles from './home.less';

const PROMPT_PLACEHOLDER = '明天下午，两张科幻片…';

const HomePage: React.FC = () => {
  const [tab, setTab] = useState<'hot_showing' | 'coming_soon'>('hot_showing');
  const [movies, setMovies] = useState<MovieVO[]>([]);
  const [hot, setHot] = useState<WeeklyHotItem[]>([]);
  const [personal, setPersonal] = useState<PersonalRecoItem[]>([]);
  const [loading, setLoading] = useState(true);
  const openDrawer = useAgentStore((s) => s.openDrawer);
  const patchLocal = useBookingStore((s) => s.patchLocal);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      setLoading(true);
      try {
        const [list, weekly, reco] = await Promise.all([
          catalogApi.listMovies({ status: tab, page: 1, size: 8 }),
          catalogApi.weeklyHot(10),
          catalogApi.personalReco(10).catch(() => ({ mode: 'fallback_hot', items: [] })),
        ]);
        if (cancelled) return;
        setMovies(list.items);
        setHot(weekly.items);
        setPersonal(reco.items);
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [tab]);

  const featured = movies[0] || hot[0]?.movie;
  const strip = movies.slice(0, 4);
  const ranking = hot.slice(0, 3);
  const recoReason =
    personal.find((p) => p.reason)?.reason ||
    '你喜欢的不是科幻，是人在未知里仍然选择靠近。';
  const recoCount = personal.filter((p) => p.reason).length || personal.length || 3;

  const goBuy = async (movie: MovieVO) => {
    await patchLocal(
      { movieId: movie.movieId, filmTitle: movie.title, state: 'SelectCinema' },
      { debounce: false },
    );
    history.push(`/booking/cinemas?movieId=${movie.movieId}`);
  };

  return (
    <div className={styles.page}>
      <div className={styles.heroShell}>
        <article className={styles.heroFeature}>
          <div className={styles.heroCopy}>
            <span className="miaoyu-eyebrow">本周首映 · 精选场次</span>
            <h1>
              {featured ? (
                <>
                  {featured.title.length > 8 ? (
                    <>
                      {featured.title.slice(0, Math.ceil(featured.title.length / 2))}
                      <br />
                      {featured.title.slice(Math.ceil(featured.title.length / 2))}
                    </>
                  ) : (
                    featured.title
                  )}
                </>
              ) : (
                <>
                  一句话
                  <br />
                  订好今晚位子
                </>
              )}
            </h1>
            <p>
              {featured?.description ||
                '告诉妙语助手你的想法，电影、影院、时间与连座偏好会保存到同一份购票草稿。'}
            </p>
            {featured ? (
              <div className={styles.metaRow}>
                <span className={styles.score}>
                  {featured.rating != null ? featured.rating.toFixed(1) : '—'}
                </span>
                <span>{featured.genres.slice(0, 2).join(' / ') || '影片'}</span>
                <span>{featured.durationMin} 分钟</span>
              </div>
            ) : null}
            <div className={styles.heroActions}>
              {featured ? (
                <button type="button" className={styles.primary} onClick={() => goBuy(featured)}>
                  选场购票
                </button>
              ) : null}
              <button
                type="button"
                className={styles.textBtn}
                onClick={() =>
                  openDrawer({
                    message: featured ? `帮我订《${featured.title}》` : undefined,
                  })
                }
              >
                让 Agent 帮我订 ↗
              </button>
            </div>
          </div>
          {featured?.posterUrl ? (
            <div className={styles.posterStage}>
              <img src={featured.posterUrl} alt={featured.title} />
            </div>
          ) : (
            <div className={`${styles.posterStage} ${styles.posterFallback}`}>
              <strong>
                妙语
                <br />
                购票
              </strong>
            </div>
          )}
          <div className={styles.beam} aria-hidden />
        </article>

        <aside className={styles.agentCard}>
          <span className="miaoyu-eyebrow teal">✦ 一句话订票</span>
          <h2>
            你说想法，
            <br />
            我来排片。
          </h2>
          <p>电影、影院、时间和连座偏好，会保存到同一份购票草稿。</p>
          <button
            type="button"
            className={styles.promptInput}
            onClick={() => openDrawer({ message: PROMPT_PLACEHOLDER.replace('…', '') })}
          >
            <span>{PROMPT_PLACEHOLDER}</span>
            <b>↗</b>
          </button>
          <div className={styles.promptChips}>
            {INTENT_CHIPS.map((c) => (
              <button key={c} type="button" onClick={() => openDrawer({ message: c })}>
                {c}
              </button>
            ))}
          </div>
          {featured ? (
            <div className={styles.nextShow}>
              <span className={styles.liveDot} />
              <div>
                <b>最近可购</b>
                <small>
                  《{featured.title}》 · {featured.durationMin} 分钟
                </small>
              </div>
              <strong>购票</strong>
            </div>
          ) : null}
        </aside>
      </div>

      <section className={styles.contentSection}>
        <div className={styles.sectionHead}>
          <div>
            <span className="miaoyu-eyebrow">NOW SHOWING</span>
            <h2>正在热映</h2>
          </div>
          <div className={styles.segmented}>
            <button
              type="button"
              className={tab === 'hot_showing' ? styles.segActive : ''}
              onClick={() => setTab('hot_showing')}
            >
              热映
            </button>
            <button
              type="button"
              className={tab === 'coming_soon' ? styles.segActive : ''}
              onClick={() => setTab('coming_soon')}
            >
              待映
            </button>
          </div>
        </div>

        {loading ? (
          <div className={styles.movieStrip}>
            {Array.from({ length: 4 }).map((_, i) => (
              <div key={i} className={`miaoyu-skeleton ${styles.skCard}`} />
            ))}
          </div>
        ) : strip.length === 0 ? (
          <div className={styles.empty}>暂无{tab === 'hot_showing' ? '热映' : '待映'}影片</div>
        ) : (
          <div className={styles.movieStrip}>
            {strip.map((m, i) => (
              <article
                key={m.movieId}
                className={`${styles.movieCard} miaoyu-fade-up`}
                style={{ animationDelay: `${i * 50}ms` }}
                onClick={() => history.push(`/movies/${m.movieId}`)}
              >
                <div className={styles.miniPoster}>
                  <img src={m.posterUrl} alt="" />
                  <span>{String(i + 1).padStart(2, '0')}</span>
                  {m.rating != null ? <i>{m.rating.toFixed(1)}</i> : null}
                </div>
                <div className={styles.movieInfo}>
                  <h3>{m.title}</h3>
                  <p>
                    {m.genres.slice(0, 2).join(' · ') || '影片'} · {m.durationMin}分钟
                  </p>
                  <button
                    type="button"
                    className={styles.ticketLink}
                    onClick={(e) => {
                      e.stopPropagation();
                      if (m.status === 'coming_soon') {
                        history.push(`/movies/${m.movieId}`);
                      } else {
                        goBuy(m);
                      }
                    }}
                  >
                    {m.status === 'coming_soon' ? '预约提醒' : '购票'}
                  </button>
                </div>
              </article>
            ))}
          </div>
        )}
      </section>

      <section className={styles.editorialGrid}>
        <article className={styles.ranking}>
          <div className={`${styles.sectionHead} ${styles.compact}`}>
            <div>
              <span className="miaoyu-eyebrow">WEEKLY HOT</span>
              <h2>城市热榜</h2>
            </div>
            <button type="button" className={styles.textBtnDark} onClick={() => history.push('/movies')}>
              完整榜单 ↗
            </button>
          </div>
          <ol>
            {ranking.map((item) => (
              <li
                key={item.movie.movieId}
                onClick={() => history.push(`/movies/${item.movie.movieId}`)}
              >
                <b>{String(item.rank).padStart(2, '0')}</b>
                <span>
                  {item.movie.title}
                  <small>{item.heatTag || '热度上升'}</small>
                </span>
                <em>{item.movie.rating != null ? item.movie.rating.toFixed(1) : '—'}</em>
              </li>
            ))}
            {ranking.length === 0 ? <li className={styles.emptyRank}>暂无热榜数据</li> : null}
          </ol>
        </article>

        <article className={styles.recommendNote}>
          <span className="miaoyu-eyebrow teal">FOR YOU</span>
          <blockquote>“{recoReason}”</blockquote>
          <p>
            {personal.length
              ? `根据你的观影偏好，为你挑出 ${recoCount} 部。`
              : '登录后根据观影记录，为你挑出更合适的电影。'}
          </p>
          <button
            type="button"
            className={styles.secondary}
            onClick={() => openDrawer({ message: '为什么推荐这些电影？' })}
          >
            看看 Agent 的理由
          </button>
        </article>
      </section>
    </div>
  );
};

export default HomePage;
