import React, { useEffect, useRef, useState } from 'react';
import { history } from 'umi';
import { Carousel } from 'antd';
import type { CarouselRef } from 'antd/es/carousel';
import { LeftOutlined, RightOutlined } from '@ant-design/icons';
import * as catalogApi from '@/api/catalog';
import type { MovieVO, WeeklyHotItem, PersonalRecoItem } from '@/types';
import { useAgentStore } from '@/stores/agent';
import { useBookingStore } from '@/stores/booking';
import { INTENT_CHIPS } from '@/constants';
import BlankPlaceholder from '@/components/BlankPlaceholder';
import MoviePosterCard from '@/components/MoviePosterCard';
import styles from './home.less';

const PROMPT_PLACEHOLDER = '明天下午，两张科幻片…';

const HomePage: React.FC = () => {
  const [hotShowing, setHotShowing] = useState<MovieVO[]>([]);
  const [comingSoon, setComingSoon] = useState<MovieVO[]>([]);
  const [hot, setHot] = useState<WeeklyHotItem[]>([]);
  const [personal, setPersonal] = useState<PersonalRecoItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [carouselIndex, setCarouselIndex] = useState(0);
  const carouselRef = useRef<CarouselRef>(null);
  const openDrawer = useAgentStore((s) => s.openDrawer);
  const patchLocal = useBookingStore((s) => s.patchLocal);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      setLoading(true);
      try {
        const [hotList, comingList, weekly, reco] = await Promise.all([
          catalogApi.listMovies({ status: 'hot_showing', page: 1, size: 8 }),
          catalogApi.listMovies({ status: 'coming_soon', page: 1, size: 8 }),
          catalogApi.weeklyHot(10),
          catalogApi.personalReco(6).catch(() => ({ mode: 'fallback_hot', items: [] })),
        ]);
        if (cancelled) return;
        setHotShowing(hotList.items);
        setComingSoon(comingList.items);
        setHot(weekly.items);
        setPersonal(reco.items);
      } catch {
        if (!cancelled) {
          setHotShowing([]);
          setComingSoon([]);
          setHot([]);
          setPersonal([]);
        }
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, []);

  const featured = hotShowing[carouselIndex] || hotShowing[0] || hot[0]?.movie;
  const ranking = hot;
  const goDetail = (movie: MovieVO) => history.push(`/movies/${movie.movieId}`);

  const goBuy = async (movie: MovieVO) => {
    try {
      await patchLocal(
        { movieId: movie.movieId, filmTitle: movie.title, state: 'SelectCinema' },
        { debounce: false },
      );
    } catch {
      /* 拦截器已提示；仍允许进入购票流 */
    }
    history.push(`/booking/cinemas?movieId=${movie.movieId}`);
  };

  const renderMovieCard = (m: MovieVO, i: number) => (
    <article
      key={m.movieId}
      className={`${styles.movieCard} miaoyu-fade-up`}
      style={{ animationDelay: `${i * 50}ms` }}
      onClick={() => goDetail(m)}
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
        {m.status === 'hot_showing' ? (
          <button
            type="button"
            className={styles.ticketLink}
            onClick={(e) => {
              e.stopPropagation();
              goBuy(m);
            }}
          >
            购票
          </button>
        ) : null}
      </div>
    </article>
  );

  return (
    <div className={styles.page}>
      <div className={styles.heroShell}>
        <article className={styles.heroFeature}>
          {hotShowing.length > 0 ? (
            <Carousel
              ref={carouselRef}
              autoplay
              autoplaySpeed={5000}
              dots={false}
              fade
              className={styles.heroCarousel}
              beforeChange={(_from, to) => setCarouselIndex(to)}
            >
              {hotShowing.map((m) => (
                <div key={m.movieId} className={styles.heroSlide} onClick={() => goDetail(m)}>
                  <div className={styles.heroCopy}>
                    <span className="miaoyu-eyebrow">本周首映 · 精选场次</span>
                    <h1>
                      {m.title.length > 8 ? (
                        <>
                          {m.title.slice(0, Math.ceil(m.title.length / 2))}
                          <br />
                          {m.title.slice(Math.ceil(m.title.length / 2))}
                        </>
                      ) : (
                        m.title
                      )}
                    </h1>
                    <p>{m.description || '告诉妙语助手你的想法，电影、影院、时间与连座偏好会保存到同一份购票草稿。'}</p>
                    <div className={styles.metaRow}>
                      <span className={styles.score}>{m.rating != null ? m.rating.toFixed(1) : '—'}</span>
                      <span>{m.genres.slice(0, 2).join(' / ') || '影片'}</span>
                      <span>{m.durationMin} 分钟</span>
                    </div>
                    <span className={styles.detailHint}>查看详情 →</span>
                  </div>
                  {m.posterUrl ? (
                    <div className={styles.posterStage}>
                      <img src={m.posterUrl} alt={m.title} />
                    </div>
                  ) : (
                    <div className={styles.posterStage}>
                      <BlankPlaceholder variant="poster" style={{ maxWidth: '100%', height: '100%' }} />
                    </div>
                  )}
                </div>
              ))}
            </Carousel>
          ) : featured ? (
            <div className={styles.heroSlide} onClick={() => goDetail(featured)}>
              <div className={styles.heroCopy}>
                <span className="miaoyu-eyebrow">本周首映 · 精选场次</span>
                <h1>{featured.title}</h1>
                <p>{featured.description || '告诉妙语助手你的想法，电影、影院、时间与连座偏好会保存到同一份购票草稿。'}</p>
                <div className={styles.metaRow}>
                  <span className={styles.score}>{featured.rating != null ? featured.rating.toFixed(1) : '—'}</span>
                  <span>{featured.genres.slice(0, 2).join(' / ') || '影片'}</span>
                  <span>{featured.durationMin} 分钟</span>
                </div>
                <span className={styles.detailHint}>查看详情 →</span>
              </div>
              {featured.posterUrl ? (
                <div className={styles.posterStage}>
                  <img src={featured.posterUrl} alt={featured.title} />
                </div>
              ) : null}
            </div>
          ) : (
            <div className={styles.heroCopy}>
              <span className="miaoyu-eyebrow">本周首映 · 精选场次</span>
              <h1>
                一句话
                <br />
                订好今晚位子
              </h1>
              <p>告诉妙语助手你的想法，电影、影院、时间与连座偏好会保存到同一份购票草稿。</p>
            </div>
          )}
          <div className={styles.beam} aria-hidden />
          {hotShowing.length > 1 ? (
            <>
              <div className={styles.carouselDots} role="tablist" aria-label="轮播影片">
                {hotShowing.map((m, i) => (
                  <button
                    key={m.movieId}
                    type="button"
                    role="tab"
                    aria-selected={i === carouselIndex}
                    className={`${styles.carouselDot} ${i === carouselIndex ? styles.carouselDotActive : ''}`}
                    onClick={() => carouselRef.current?.goTo(i)}
                  />
                ))}
              </div>
              <div className={styles.carouselArrows}>
                <button
                  type="button"
                  className={styles.carouselArrow}
                  aria-label="上一部"
                  onClick={() => carouselRef.current?.prev()}
                >
                  <LeftOutlined />
                </button>
                <button
                  type="button"
                  className={styles.carouselArrow}
                  aria-label="下一部"
                  onClick={() => carouselRef.current?.next()}
                >
                  <RightOutlined />
                </button>
              </div>
            </>
          ) : null}
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
        </div>

        <div className={styles.rowHead}>
          <h3>热映</h3>
        </div>
        {loading ? (
          <BlankPlaceholder variant="card" count={4} className={styles.movieStrip} />
        ) : hotShowing.length === 0 ? (
          <div className={styles.rowEmpty}>暂无热映影片</div>
        ) : (
          <div className={styles.movieStrip}>{hotShowing.slice(0, 4).map(renderMovieCard)}</div>
        )}

        <div className={styles.rowHead}>
          <h3>待映</h3>
        </div>
        {loading ? (
          <BlankPlaceholder variant="card" count={4} className={styles.movieStrip} />
        ) : comingSoon.length === 0 ? (
          <div className={styles.rowEmpty}>暂无待映影片</div>
        ) : (
          <div className={styles.movieStrip}>{comingSoon.slice(0, 4).map(renderMovieCard)}</div>
        )}
      </section>

      <section className={styles.editorialGrid}>
        <article className={styles.ranking}>
          <div className={`${styles.sectionHead} ${styles.compact}`}>
            <div>
              <span className="miaoyu-eyebrow">WEEKLY HOT</span>
              <h2>每周热榜</h2>
            </div>
            <button type="button" className={styles.textBtnDark} onClick={() => history.push('/ranking')}>
              完整榜单 ↗
            </button>
          </div>
          <ol>
            {ranking.length === 0 ? (
              <li className={styles.emptyRank}>
                <BlankPlaceholder variant="row" count={3} />
              </li>
            ) : (
              ranking.map((item) => (
                <li key={item.movie.movieId} onClick={() => goDetail(item.movie)}>
                  <b>{String(item.rank).padStart(2, '0')}</b>
                  <span>
                    {item.movie.title}
                    <small>{item.heatTag || '热度上升'}</small>
                  </span>
                  <em>{item.movie.rating != null ? item.movie.rating.toFixed(1) : '—'}</em>
                </li>
              ))
            )}
          </ol>
        </article>

        <article className={styles.forYou}>
          <div className={styles.sectionHead}>
            <div>
              <span className="miaoyu-eyebrow">FOR YOU</span>
              <h2>为你推荐</h2>
            </div>
          </div>
          {loading ? (
            <BlankPlaceholder variant="poster" count={3} className={styles.forYouGrid} />
          ) : personal.length === 0 ? (
            <div className={styles.rowEmpty}>登录后根据观影记录，为你推荐更合适的电影</div>
          ) : (
            <div className={styles.forYouGrid}>
              {personal.map((item) => (
                <MoviePosterCard
                  key={item.movie.movieId}
                  movie={item.movie}
                  reason={item.reason}
                  showBuy={item.movie.status !== 'coming_soon'}
                />
              ))}
            </div>
          )}
        </article>
      </section>
    </div>
  );
};

export default HomePage;
