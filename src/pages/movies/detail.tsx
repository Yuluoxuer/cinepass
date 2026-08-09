import React, { useEffect, useMemo, useState } from 'react';
import { message } from 'antd';
import { history, useParams } from 'umi';
import * as catalogApi from '@/api/catalog';
import type { CinemaVO, MovieVO } from '@/types';
import BlankPlaceholder from '@/components/BlankPlaceholder';
import { useBookingStore } from '@/stores/booking';
import { useAgentStore } from '@/stores/agent';
import { useAuthStore } from '@/stores/auth';
import styles from './detail.less';

type CastMember = {
  name: string;
  role?: string;
  avatarUrl?: string | null;
};

type DetailMovie = MovieVO & {
  director?: string;
  castMembers?: CastMember[];
};

type DetailCinema = CinemaVO & {
  features?: string[];
  tags?: string[];
};

function formatWantCount(count: number) {
  return count >= 10000 ? `${(count / 10000).toFixed(1)}万` : String(count);
}

function getCastMembers(movie: DetailMovie): CastMember[] {
  if (movie.castMembers?.length) {
    return movie.castMembers.filter((member) => !member.role?.includes('导演'));
  }
  if (!movie.cast) {
    return [];
  }
  return movie.cast
    .split(/[、,，/]/)
    .map((name) => name.trim())
    .filter(Boolean)
    .map((name) => ({ name, role: '主演' }));
}

const MovieDetailPage: React.FC = () => {
  const { movieId } = useParams<{ movieId: string }>();
  const [movie, setMovie] = useState<DetailMovie | null>(null);
  const [movieLoading, setMovieLoading] = useState(true);
  const [movieError, setMovieError] = useState('');
  const [cinemas, setCinemas] = useState<DetailCinema[]>([]);
  const [cinemaLoading, setCinemaLoading] = useState(true);
  const [cinemaError, setCinemaError] = useState('');
  const [wanted, setWanted] = useState(false);
  const [wantLoading, setWantLoading] = useState(false);
  const [expanded, setExpanded] = useState(false);
  const patchLocal = useBookingStore((s) => s.patchLocal);
  const openDrawer = useAgentStore((s) => s.openDrawer);
  const user = useAuthStore((s) => s.user);
  const openLogin = useAuthStore((s) => s.openLoginModal);

  useEffect(() => {
    if (!movieId) return;
    let active = true;
    setMovieLoading(true);
    setMovieError('');
    setCinemaLoading(true);
    setCinemaError('');
    setExpanded(false);

    void catalogApi
      .getMovie(movieId)
      .then((result) => {
        if (active) setMovie(result as DetailMovie);
      })
      .catch(() => {
        if (active) setMovieError('影片信息加载失败，请稍后重试');
      })
      .finally(() => {
        if (active) setMovieLoading(false);
      });

    void catalogApi
      .listCinemas({ movieId, sort: 'price', page: 1, size: 50 })
      .then((result) => {
        if (active) setCinemas(result.items as DetailCinema[]);
      })
      .catch(() => {
        if (active) setCinemaError('购票信息加载失败，请稍后重试');
      })
      .finally(() => {
        if (active) setCinemaLoading(false);
      });

    return () => {
      active = false;
    };
  }, [movieId]);

  useEffect(() => {
    if (!movieId || !user) {
      setWanted(false);
      return;
    }
    // 提交期间取消旧的列表回填，避免登录后旧列表覆盖乐观更新结果。
    if (wantLoading) return;
    let active = true;
    void catalogApi
      .listWantSee({ page: 1, size: 100 })
      .then((result) => {
        if (active) setWanted(result.items.some((item) => item.movieId === movieId));
      })
      .catch(() => {
        if (active) setWanted(false);
      });
    return () => {
      active = false;
    };
  }, [movieId, user, wantLoading]);

  const purchaseSummary = useMemo(() => {
    const prices = cinemas
      .map((cinema) => cinema.minPrice)
      .filter((price): price is number => price != null);
    const features = Array.from(new Set(cinemas.flatMap((cinema) => cinema.tags || cinema.features || [])));
    return { minPrice: prices.length ? Math.min(...prices) : null, features };
  }, [cinemas]);

  const castMembers = useMemo(() => (movie ? getCastMembers(movie) : []), [movie]);

  const onBuy = async () => {
    if (!movie) return;
    try {
      await patchLocal(
        { movieId: movie.movieId, filmTitle: movie.title, state: 'SelectCinema' },
        { debounce: false },
      );
      history.push(`/booking/cinemas?movieId=${movie.movieId}`);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '暂时无法开始购票，请稍后重试');
    }
  };

  const onOpenAgent = () => {
    if (!movie) return;
    openDrawer({ message: `帮我订《${movie.title}》` });
  };

  const onWant = async () => {
    if (!movie || wantLoading) return;
    let currentUser = user;
    if (!currentUser) {
      const ok = await openLogin();
      if (!ok) return;
      currentUser = useAuthStore.getState().user;
      if (!currentUser) return;
    }

    const previousWanted = wanted;
    const delta = previousWanted ? -1 : 1;
    setWantLoading(true);
    setWanted(!previousWanted);
    setMovie((current) =>
      current ? { ...current, wantSeeCount: Math.max(0, current.wantSeeCount + delta) } : current,
    );
    try {
      const result = previousWanted
        ? await catalogApi.unwantSee(movie.movieId)
        : await catalogApi.wantSee(movie.movieId);
      if (result.wanted !== !previousWanted) {
        setWanted(result.wanted);
        setMovie((current) =>
          current
            ? {
                ...current,
                wantSeeCount: Math.max(0, current.wantSeeCount + (result.wanted ? 1 : -1)),
              }
            : current,
        );
      }
    } catch (error) {
      setWanted(previousWanted);
      setMovie((current) =>
        current ? { ...current, wantSeeCount: Math.max(0, current.wantSeeCount - delta) } : current,
      );
      message.error(error instanceof Error ? error.message : '操作失败，请稍后重试');
    } finally {
      setWantLoading(false);
    }
  };

  if (movieLoading) {
    return (
      <div className="miaoyu-container" style={{ paddingTop: 24 }}>
        <BlankPlaceholder variant="block" />
      </div>
    );
  }

  if (!movie) {
    return (
      <div className="miaoyu-container" style={{ paddingTop: 24 }}>
        <BlankPlaceholder variant="block" />
      </div>
    );
  }

  const ctaLabel = movie.status === 'coming_soon' ? '预售' : '选影院购票';

  return (
    <div className={styles.page}>
      <div className={styles.banner} style={{ backgroundImage: `url(${movie.posterUrl})` }}>
        <div className={styles.bannerMask} />
        <div className={styles.bannerInner}>
          <img src={movie.posterUrl} alt={`${movie.title}海报`} className={styles.poster} />
          <div className={styles.meta}>
            <div className={styles.top}>
              <h1>{movie.title}</h1>
              <button type="button" className={styles.want} onClick={onWant} disabled={wantLoading}>
                {wanted ? '♥' : '♡'} 想看 {formatWantCount(movie.wantSeeCount)}
              </button>
            </div>
            <div className={styles.scoreRow}>
              {movie.rating != null ? (
                <span className={styles.score}><em>{movie.rating.toFixed(1)}</em> 分</span>
              ) : <span className={styles.noScore}>暂无评分</span>}
              <span className={styles.dot}>·</span>
              <span>{movie.genres.join(' / ')}</span>
              <span className={styles.dot}>·</span>
              <span>{movie.durationMin}分钟</span>
              <span className={styles.dot}>·</span>
              <span>{movie.releaseDate} 上映</span>
            </div>
            <p className={styles.cast}>导演：{movie.director || '待公布'}　主演：{movie.cast || '待公布'}</p>
            <p className={styles.heroDesc}>{movie.description}</p>
            <button type="button" className={styles.agentLink} onClick={onOpenAgent}>
              Agent 帮我订这部 →
            </button>
          </div>
        </div>
      </div>

      <main className={`miaoyu-container ${styles.content}`}>
        <section className={styles.mainColumn}>
          <article className={styles.infoCard}>
            <h2>剧情简介</h2>
            <p className={`${styles.synopsis} ${expanded ? styles.open : ''}`}>{movie.description}</p>
            {movie.description.length > 90 ? (
              <button type="button" className={styles.expand} onClick={() => setExpanded((value) => !value)}>
                {expanded ? '收起' : '展开全部'}
              </button>
            ) : null}
          </article>
          <article className={styles.infoCard}>
            <h2>演职人员</h2>
            <div className={styles.peopleList}>
              {movie.director ? (
                <div className={styles.person}>
                  <div className={styles.avatar}>{movie.director.slice(0, 1)}</div>
                  <strong>{movie.director}</strong>
                  <span>导演</span>
                </div>
              ) : null}
              {castMembers.length ? castMembers.map((member, index) => (
                <div className={styles.person} key={`${member.name}-${index}`}>
                  {member.avatarUrl ? (
                    <img className={styles.avatar} src={member.avatarUrl} alt="" />
                  ) : <div className={styles.avatar}>{member.name.slice(0, 1)}</div>}
                  <strong>{member.name}</strong>
                  <span>{member.role || '主演'}</span>
                </div>
              )) : <p className={styles.emptyText}>演职人员信息待公布</p>}
            </div>
          </article>
        </section>

        <aside className={styles.purchaseCard}>
          <h2>购票信息</h2>
          {cinemaLoading ? <div className={styles.summaryLoading}>正在加载可购影院…</div> : null}
          {!cinemaLoading && cinemaError ? <p className={styles.summaryError}>{cinemaError}</p> : null}
          {!cinemaLoading && !cinemaError && cinemas.length === 0 ? (
            <p className={styles.emptyText}>暂未找到可购影院，请稍后再来看看。</p>
          ) : null}
          {!cinemaLoading && !cinemaError && cinemas.length > 0 ? (
            <>
              <div className={styles.priceBlock}>
                <span>最低票价</span>
                <strong>{purchaseSummary.minPrice != null ? `¥${purchaseSummary.minPrice} 起` : '价格待公布'}</strong>
                <small>共 {cinemas.length} 家影院可购</small>
              </div>
              {purchaseSummary.features.length ? (
                <div className={styles.features}>
                  <span>特色影厅</span>
                  <div>{purchaseSummary.features.map((feature) => <i key={feature}>{feature}</i>)}</div>
                </div>
              ) : null}
            </>
          ) : null}
          <button type="button" className="miaoyu-btn-primary" onClick={onBuy}>{ctaLabel}</button>
          <button type="button" className={styles.agentButton} onClick={onOpenAgent}>✦ 打开 Agent 订票</button>
        </aside>
      </main>

      <div className={styles.bar}>
        <div className={styles.barInner}>
          <div className={styles.barInfo}>
            <strong>{movie.title}</strong>
            <span>{movie.rating != null ? `${movie.rating}分` : '暂无评分'} · {movie.genres.join('/')}</span>
          </div>
          <button type="button" className="miaoyu-btn-primary" style={{ minWidth: 200 }} onClick={onBuy}>
            {ctaLabel}{purchaseSummary.minPrice != null ? ` ¥${purchaseSummary.minPrice}起` : ''}
          </button>
        </div>
      </div>
    </div>
  );
};

export default MovieDetailPage;
