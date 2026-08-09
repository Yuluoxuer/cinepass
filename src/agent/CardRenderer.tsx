import React, { useEffect, useMemo, useRef, useState } from 'react';
import { QRCode } from 'antd';
import type { AgentCardVO, CardAction, MovieVO, CinemaVO, ShowVO, SeatPlanVO, SeatVO, OrderVO } from '@/types';
import * as orderApi from '@/api/order';
import { mobileUrl } from '@/utils/format';
import SeatMap from '@/features/seatmap/SeatMap';
import { zoneLabel } from '@/utils/zone';
import styles from './CardRenderer.less';

interface Props {
  card: AgentCardVO;
  onAction: (actionId: string, itemId?: string, draftPatch?: Record<string, unknown>) => void;
}

const CardRenderer: React.FC<Props> = ({ card, onAction }) => {
  const p = card.payload;

  const invokeAction = (
    itemId: string | undefined,
    fallbackActionId: string,
    fallbackPatch?: Record<string, unknown>,
  ) => {
    const action =
      card.actions.find((candidate) => candidate.itemId === itemId) ||
      card.actions.find(
        (candidate) => candidate.actionId === fallbackActionId && (!candidate.itemId || candidate.itemId === itemId),
      ) ||
      ({ actionId: fallbackActionId, label: '', itemId, draftPatch: fallbackPatch } satisfies CardAction);
    onAction(action.actionId, action.itemId ?? itemId, action.draftPatch ?? fallbackPatch);
  };

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
            <button
              type="button"
              onClick={() =>
                invokeAction(m.movieId, 'select', { movieId: m.movieId, filmTitle: m.title })
              }
            >
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
            <button
              type="button"
              onClick={() =>
                invokeAction(c.cinemaId, 'select', { cinemaId: c.cinemaId, cinemaName: c.name })
              }
            >
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
            <button
              type="button"
              onClick={() => invokeAction(s.showId, 'select', { showId: s.showId })}
            >
              选这场
            </button>
          </div>
        ))}
      </div>
    );
  }

  if (card.type === 'date_show_list') {
    return (
      <DateShowListCard
        card={card}
        onAction={onAction}
        invokeAction={invokeAction}
      />
    );
  }

  if (card.type === 'seat_plans') {
    return (
      <SeatPlansCard
        payload={p}
        onSelect={(seatIds) => invokeAction(undefined, 'confirm', { seatIds })}
      />
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
        <button type="button" onClick={() => invokeAction(order.orderId, 'go_pay')}>
          去支付
        </button>
      </div>
    );
  }

  if (card.type === 'pay_mock') {
    return (
      <PayMockCard
        payload={p}
        onIssued={() => invokeAction(undefined, 'payment_done', { orderId: p.orderId })}
      />
    );
  }

  if (card.type === 'ticket_issued') {
    const order = p.order as OrderVO;
    return (
      <div className={styles.card}>
        <div className={styles.title}>✓ {card.title}</div>
        <p>取票码 {order.ticketCode}</p>
        <button type="button" onClick={() => invokeAction(order.orderId, 'view_order')}>
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
            <button key={s} type="button" onClick={() => invokeAction(s, 'fill_slot')}>
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
        <button type="button" onClick={() => invokeAction(undefined, 'retry')}>
          重试
        </button>
      </div>
    );
  }

  return null;
};

interface ShowDay {
  date: string;
  label: string;
  shows: ShowVO[];
}

function DateShowListCard({
  card,
  onAction,
  invokeAction,
}: {
  card: AgentCardVO;
  onAction: (actionId: string, itemId?: string, draftPatch?: Record<string, unknown>) => void;
  invokeAction: (itemId: string | undefined, fallbackActionId: string, fallbackPatch?: Record<string, unknown>) => void;
}) {
  const p = card.payload;
  const days = (p.days || []) as ShowDay[];
  const [activeDate, setActiveDate] = useState<string>(days[0]?.date || '');

  if (days.length === 0) return null;
  const active = days.find((d) => d.date === activeDate) || days[0];
  const shows = active?.shows || [];

  return (
    <div className={styles.card}>
      <div className={styles.title}>{card.title}</div>
      <div className={styles.dateTabs}>
        {days.map((d) => (
          <button
            key={d.date}
            type="button"
            className={`${styles.dateTab} ${d.date === active?.date ? styles.dateTabActive : ''}`}
            onClick={() => setActiveDate(d.date)}
          >
            {d.label}
            <small>{d.date.slice(5)}</small>
          </button>
        ))}
      </div>
      <div>
        {shows.length === 0 ? (
          <p className={styles.warn}>当天暂无场次</p>
        ) : (
          shows.map((s) => (
            <div key={s.showId} className={styles.row}>
              <div className={styles.info}>
                <strong>{s.startTime.slice(11, 16)} {s.hallName}</strong>
                <span>¥{s.price} · {s.seatRemainLevel}</span>
              </div>
              <button
                type="button"
                onClick={() => {
                  const action = card.actions.find((candidate) => candidate.itemId === s.showId);
                  if (action?.draftPatch) {
                    onAction(action.actionId, action.itemId ?? s.showId, action.draftPatch);
                  } else {
                    invokeAction(s.showId, 'select', { showId: s.showId, date: active?.date });
                  }
                }}
              >
                选这场
              </button>
            </div>
          ))
        )}
      </div>
    </div>
  );
}

function SeatPlansCard({
  payload,
  onSelect,
}: {
  payload: Record<string, unknown>;
  onSelect: (seatIds: string[]) => void;
}) {
  const seatMap = payload.seatMap as { rows: number; cols: number; screenLabel?: string; seats: SeatVO[] } | undefined;
  const plans = (payload.plans || []) as SeatPlanVO[];
  const compromise = payload.compromise as { suggestion?: string } | null;
  const count = Number(payload.count) || 2;
  /** 已锁座的座位（锁座后由后端回填，用于回显，避免用户重新点选） */
  const preselectedIds = (payload.seatIds || []) as string[];

  const seats = seatMap?.seats || [];
  const [selectedIds, setSelectedIds] = useState<string[]>([]);

  // 优先回显已锁座座位；否则自动预选推荐方案
  useEffect(() => {
    if (selectedIds.length > 0) return;
    if (preselectedIds.length > 0) {
      setSelectedIds(preselectedIds.slice(0, count));
    } else if (plans.length > 0) {
      setSelectedIds(plans[0].seatIds.slice(0, count));
    }
  }, [plans, count, preselectedIds]);

  const byId = useMemo(() => {
    const m = new Map<string, SeatVO>();
    seats.forEach((s) => m.set(s.seatId, s));
    return m;
  }, [seats]);

  const selectedSeats = useMemo(
    () => selectedIds.map((id) => byId.get(id)).filter(Boolean) as SeatVO[],
    [selectedIds, byId],
  );

  const toggle = (seat: SeatVO) => {
    setSelectedIds((prev) => {
      if (prev.includes(seat.seatId)) return prev.filter((id) => id !== seat.seatId);
      if (prev.length >= count) {
        const next = [...prev.slice(1), seat.seatId];
        return next;
      }
      return [...prev, seat.seatId];
    });
  };

  const applyPlan = (plan: SeatPlanVO) => {
    setSelectedIds(plan.seatIds.slice(0, count));
  };

  const total = selectedSeats.reduce((sum, s) => {
    const p = s.price != null ? Number(s.price) : 0;
    return sum + (Number.isFinite(p) ? p : 0);
  }, 0);

  return (
    <div className={styles.card} style={{ maxWidth: 420 }}>
      <div className={styles.title}>选择座位（{count}张）</div>

      {seatMap ? (
        <div style={{ overflow: 'auto', maxHeight: 180, padding: '4px 0' }}>
          <SeatMap
            rows={seatMap.rows}
            cols={seatMap.cols}
            screenLabel={seatMap.screenLabel}
            seats={seats}
            selectedIds={selectedIds}
            onToggle={toggle}
            scale={0.42}
          />
        </div>
      ) : (
        <p style={{ color: '#999' }}>座位图加载中…</p>
      )}

      {selectedSeats.length > 0 && (
        <div style={{ margin: '8px 0', fontSize: 13 }}>
          已选：{selectedSeats.map((s) => `${s.seatName}(${zoneLabel(s.zone)} ¥${s.price ?? '?'})`).join('、')}
          {total > 0 && <span style={{ fontWeight: 700, color: '#e54847', marginLeft: 8 }}>合计 ¥{total}</span>}
        </div>
      )}

      {plans.length > 0 && (
        <div style={{ margin: '8px 0' }}>
          <div style={{ fontSize: 12, color: '#999', marginBottom: 4 }}>智能推荐</div>
          {plans.map((plan) => (
            <button
              key={plan.planId}
              type="button"
              style={{ marginRight: 6, marginBottom: 4, fontSize: 12, padding: '2px 8px' }}
              onClick={() => applyPlan(plan)}
            >
              {plan.explain}
            </button>
          ))}
        </div>
      )}

      {compromise?.suggestion ? (
        <p style={{ color: '#faad14', fontSize: 12, margin: '4px 0' }}>{compromise.suggestion}</p>
      ) : null}

      <button
        type="button"
        className="miaoyu-btn-primary"
        style={{ width: '100%', marginTop: 8 }}
        disabled={selectedIds.length === 0}
        onClick={() => onSelect(selectedIds.map((s) => s))}
      >
        {selectedIds.length === count ? '确认选座' : `已选 ${selectedIds.length}/${count} 座`}
      </button>
    </div>
  );
}

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
      {payload.movieTitle ? (
        <div style={{ fontSize: 12, color: '#666', marginBottom: 6 }}>
          <p>{String(payload.movieTitle)}{payload.cinemaName ? ` · ${payload.cinemaName}` : ''}</p>
          {(payload.seatIds as string[] | undefined)?.length ? (
            <p>座位：{((payload.seatLabels as string[] | undefined) || (payload.seatIds as string[])).join('、')} · 共 {String(payload.count || (payload.seatIds as string[]).length)} 张</p>
          ) : null}
        </div>
      ) : null}
      <p style={{ fontWeight: 700, color: '#e54847' }}>金额 ¥{String(payload.amount)}</p>
      {payUrl ? <QRCode value={mobileUrl(payUrl)} size={144} bordered={false} /> : <p>支付链接生成失败</p>}
      <a href={mobileUrl(payUrl)} target="_blank" rel="noreferrer" style={{ fontSize: 12, wordBreak: 'break-all' }}>
        在手机上打开支付页
      </a>
      <p style={{ color: '#999', fontSize: 12 }}>{status === 'issued' ? '已支付' : '等待手机确认…'}</p>
    </div>
  );
}

export default CardRenderer;
