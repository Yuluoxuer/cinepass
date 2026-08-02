import React, { useEffect, useState } from 'react';
import { history, useLocation } from 'umi';
import { message } from 'antd';
import * as catalogApi from '@/api/catalog';
import * as orderApi from '@/api/order';
import type { SeatMapVO, SeatVO, SeatPlanVO } from '@/types';
import { ApiError } from '@/types';
import BookingProgress from '@/components/BookingProgress';
import SeatMap from '@/features/seatmap/SeatMap';
import { useBookingStore } from '@/stores/booking';
import { getSessionId } from '@/stores/booking';
import { useAuthStore } from '@/stores/auth';
import { useAgentStore } from '@/stores/agent';
import styles from './booking.less';

const BookingSeatsPage: React.FC = () => {
  const loc = useLocation();
  const showId = new URLSearchParams(loc.search).get('showId') || '';
  const [map, setMap] = useState<SeatMapVO | null>(null);
  const [selected, setSelected] = useState<SeatVO[]>([]);
  const [plans, setPlans] = useState<SeatPlanVO[]>([]);
  const [compromise, setCompromise] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const draft = useBookingStore((s) => s.draft);
  const mergeSeatNames = useBookingStore((s) => s.mergeSeatNames);
  const ensureSession = useBookingStore((s) => s.ensureSession);
  const user = useAuthStore((s) => s.user);
  const openLogin = useAuthStore((s) => s.openLoginModal);
  const openDrawer = useAgentStore((s) => s.openDrawer);

  const refresh = async () => {
    if (!showId) return;
    const sm = await catalogApi.getSeatMap(showId);
    setMap(sm);
  };

  useEffect(() => {
    void refresh();
    void catalogApi
      .recommendSeats({ showId, count: draft?.count || 2, preferRow: 'middle', preferSide: 'center', together: true })
      .then((r) => {
        setPlans(r.plans);
        setCompromise(r.compromise?.suggestion || null);
      })
      .catch(() => {});
  }, [showId]);

  const toggle = (seat: SeatVO) => {
    setSelected((prev) => {
      const exists = prev.find((s) => s.seatId === seat.seatId);
      if (exists) {
        if (seat.couplePairId) {
          return prev.filter((s) => s.couplePairId !== seat.couplePairId);
        }
        return prev.filter((s) => s.seatId !== seat.seatId);
      }
      let next = [...prev];
      if (seat.couplePairId && map) {
        const pair = map.seats.filter((s) => s.couplePairId === seat.couplePairId);
        next = next.filter((s) => s.couplePairId !== seat.couplePairId);
        next = [...next, ...pair.filter((s) => s.status === 'available')];
      } else {
        next.push(seat);
      }
      if (next.length > 4) {
        message.warning('最多选择 4 个座位');
        return prev;
      }
      return next;
    });
  };

  const applyPlan = (plan: SeatPlanVO) => {
    if (!map) return;
    const seats = map.seats.filter((s) => plan.seatIds.includes(s.seatId));
    setSelected(seats);
  };

  const confirm = async () => {
    if (!selected.length || !showId) return;
    if (!user) {
      const ok = await openLogin();
      if (!ok) return;
    }
    setLoading(true);
    let lockId: string | null = null;
    try {
      const session = await ensureSession();
      const lock = await orderApi.lockSeats({
        showId,
        seatIds: selected.map((s) => s.seatId),
        sessionId: session.sessionId,
      });
      lockId = lock.lockId;
      const nameMap: Record<string, string> = {};
      selected.forEach((s) => {
        nameMap[s.seatId] = s.seatName;
      });
      mergeSeatNames(nameMap);
      const order = await orderApi.createOrder({
        lockId: lock.lockId,
        sessionId: session.sessionId,
      });
      history.push(`/booking/confirm?orderId=${order.orderId}`);
    } catch (e) {
      if (lockId) {
        try {
          await orderApi.unlockSeats(lockId, getSessionId() || undefined);
        } catch {
          // 锁座由服务端 TTL 兜底释放，不能覆盖原始下单错误。
        }
      }
      if (e instanceof ApiError && e.errorCode === 'SEAT_TAKEN') {
        message.error('座位已被抢，请重新选择');
        setSelected([]);
        await refresh();
      } else {
        message.error(e instanceof Error ? e.message : '下单失败，请稍后重试');
      }
    } finally {
      setLoading(false);
    }
  };

  const total = selected.reduce((sum, seat) => sum + (seat.price ?? map?.price ?? 0), 0);

  return (
    <div>
      <BookingProgress step={4} />
      <div className="miaoyu-container">
        <h2 className={styles.title}>在线选座</h2>
        <div className={styles.seatLayout}>
          <div style={{ background: '#fff', borderRadius: 8 }}>
            {map ? (
              <SeatMap
                rows={map.rows}
                cols={map.cols}
                screenLabel={map.screenLabel}
                seats={map.seats}
                selectedIds={selected.map((s) => s.seatId)}
                onToggle={toggle}
              />
            ) : (
              <div style={{ padding: 40 }}>加载座位图…</div>
            )}
          </div>
          <div className={styles.side}>
            <h3>智能选座</h3>
            {plans.map((p) => (
              <div key={p.planId} className={styles.plan}>
                <div>{p.explain}</div>
                <div style={{ fontSize: 12, color: '#666' }}>
                  {p.seats?.map((s) => s.seatName).join('、') || p.seatIds.join('、')} · 评分 {p.score}
                </div>
                <button type="button" className="miaoyu-btn-secondary" style={{ height: 32, padding: '0 12px' }} onClick={() => applyPlan(p)}>
                  选用
                </button>
              </div>
            ))}
            {compromise ? <p style={{ color: '#faad14', fontSize: 13 }}>{compromise}</p> : null}
            <button type="button" className="miaoyu-btn-text" onClick={() => openDrawer({ message: '帮我选座' })}>
              🤖 打开 Agent 帮我选
            </button>
          </div>
        </div>
      </div>
      <div className={styles.seatBar}>
        <div>
          已选 {selected.map((s) => s.seatName).join('、') || '—'}
          {selected.length ? ` · ¥${total}` : ''}
        </div>
        <button type="button" className="miaoyu-btn-primary" disabled={!selected.length || loading} onClick={confirm}>
          {loading ? '提交中…' : '确认选座'}
        </button>
      </div>
    </div>
  );
};

export default BookingSeatsPage;
