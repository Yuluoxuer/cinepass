import React, { useEffect, useRef, useState } from 'react';
import type { AgentCardVO, MovieVO, CinemaVO, ShowVO, SeatPlanVO, OrderVO } from '@/types';
import * as orderApi from '@/api/order';
import styles from './CardRenderer.less';

interface Props {
  card: AgentCardVO;
  onAction: (actionId: string, itemId?: string, draftPatch?: Record<string, unknown>) => void;
}

const CardRenderer: React.FC<Props> = ({ card, onAction }) => {
  const p = card.payload;

  if (card.type === 'movie_list') {
    const movies = (p.movies || []) as MovieVO[];
    return (
      <div className={styles.card}>
        <div className={styles.title}>{card.title}</div>
        {movies.map((m) => (
          <div key={m.movieId} className={styles.row}>
            <img src={m.posterUrl} alt="" />
            <div className={styles.info}>
              <strong>{m.title}</strong>
              <span>{m.rating != null ? `★ ${m.rating}` : '暂无评分'} · {m.genres.join('/')}</span>
            </div>
            <button type="button" onClick={() => onAction('select', m.movieId)}>
              选这部
            </button>
          </div>
        ))}
      </div>
    );
  }

  if (card.type === 'cinema_list') {
    const cinemas = (p.cinemas || []) as CinemaVO[];
    return (
      <div className={styles.card}>
        <div className={styles.title}>{card.title}</div>
        {cinemas.map((c) => (
          <div key={c.cinemaId} className={styles.row}>
            <div className={styles.info}>
              <strong>{c.name}</strong>
              <span>
                {c.distanceMeters != null ? `${(c.distanceMeters / 1000).toFixed(1)}km` : ''}{' '}
                {c.minPrice != null ? `¥${c.minPrice}起` : ''}
              </span>
            </div>
            <button type="button" onClick={() => onAction('select', c.cinemaId)}>
              选这家
            </button>
          </div>
        ))}
      </div>
    );
  }

  if (card.type === 'show_list') {
    const shows = (p.shows || []) as ShowVO[];
    return (
      <div className={styles.card}>
        <div className={styles.title}>{card.title}</div>
        {shows.map((s) => (
          <div key={s.showId} className={styles.row}>
            <div className={styles.info}>
              <strong>{s.startTime.slice(11, 16)} {s.hallName}</strong>
              <span>¥{s.price} · {s.seatRemainLevel}</span>
            </div>
            <button type="button" onClick={() => onAction('select', s.showId)}>
              选这场
            </button>
          </div>
        ))}
      </div>
    );
  }

  if (card.type === 'seat_plans') {
    const plans = (p.plans || []) as SeatPlanVO[];
    const compromise = p.compromise as { suggestion?: string } | null;
    return (
      <div className={styles.card}>
        <div className={styles.title}>{card.title}</div>
        {plans.map((plan) => (
          <div key={plan.planId} className={styles.row}>
            <div className={styles.info}>
              <strong>{plan.explain}</strong>
              <span>{plan.seats?.map((s) => s.seatName).join('、') || plan.seatIds.join('、')}</span>
            </div>
            <button
              type="button"
              onClick={() => onAction('confirm', plan.planId, { seatIds: plan.seatIds })}
            >
              确认方案
            </button>
          </div>
        ))}
        {compromise?.suggestion ? <p className={styles.warn}>{compromise.suggestion}</p> : null}
        <button type="button" className={styles.link} onClick={() => onAction('manual')}>
          自己选
        </button>
      </div>
    );
  }

  if (card.type === 'order_confirm') {
    const order = p.order as OrderVO;
    return (
      <div className={styles.card}>
        <div className={styles.title}>{card.title}</div>
        <p>{order.movieTitle}</p>
        <p>
          {order.cinemaName} · {order.hallName}
        </p>
        <p style={{ color: '#e54847', fontWeight: 700 }}>¥{order.amount}</p>
        <button type="button" onClick={() => onAction('go_pay', order.orderId)}>
          去支付
        </button>
      </div>
    );
  }

  if (card.type === 'pay_mock') {
    return <PayMockCard payload={p} onIssued={() => onAction('payment_done', undefined, { orderId: p.orderId })} />;
  }

  if (card.type === 'ticket_issued') {
    const order = p.order as OrderVO;
    return (
      <div className={styles.card}>
        <div className={styles.title}>✓ {card.title}</div>
        <p>取票码 {order.ticketCode}</p>
        <button type="button" onClick={() => onAction('view_order', order.orderId)}>
          查看订单
        </button>
      </div>
    );
  }

  if (card.type === 'ask') {
    const suggestions = (p.suggestions || []) as string[];
    return (
      <div className={styles.card}>
        <div className={styles.title}>{(p.prompt as string) || card.title}</div>
        <div className={styles.chips}>
          {suggestions.map((s) => (
            <button key={s} type="button" onClick={() => onAction('fill_slot', s)}>
              {s}
            </button>
          ))}
        </div>
      </div>
    );
  }

  if (card.type === 'error') {
    return (
      <div className={styles.card}>
        <div className={styles.title}>⚠ {card.title}</div>
        <p>{(p.message as string) || '出错了'}</p>
        <button type="button" onClick={() => onAction('retry')}>
          重试
        </button>
      </div>
    );
  }

  return null;
};

function PayMockCard({
  payload,
  onIssued,
}: {
  payload: Record<string, unknown>;
  onIssued: () => void;
}) {
  const orderId = String(payload.orderId || '');
  const payUrl = String(payload.payUrl || '');
  const interval = Number(payload.pollIntervalMs) || 2000;
  const [status, setStatus] = useState('waiting');
  const ref = useRef<ReturnType<typeof setInterval> | null>(null);

  useEffect(() => {
    if (!orderId) return;
    ref.current = setInterval(async () => {
      try {
        const o = await orderApi.getOrder(orderId);
        if (o.status === 'issued') {
          setStatus('issued');
          if (ref.current) clearInterval(ref.current);
          onIssued();
        }
      } catch {
        /* ignore */
      }
    }, interval);
    return () => {
      if (ref.current) clearInterval(ref.current);
    };
  }, [orderId, interval]);

  return (
    <div className={styles.card}>
      <div className={styles.title}>扫码支付（不会代付）</div>
      <p>金额 ¥{String(payload.amount)}</p>
      <a href={payUrl} target="_blank" rel="noreferrer" style={{ fontSize: 12, wordBreak: 'break-all' }}>
        {payUrl}
      </a>
      <p style={{ color: '#999', fontSize: 12 }}>{status === 'issued' ? '已支付' : '等待手机确认…'}</p>
    </div>
  );
}

export default CardRenderer;
