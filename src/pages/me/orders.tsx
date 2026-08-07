import React, { useEffect, useState } from 'react';
import { history } from 'umi';
import { message } from 'antd';
import * as orderApi from '@/api/order';
import type { OrderVO } from '@/types';
import { useAuthStore } from '@/stores/auth';
import { useBookingStore } from '@/stores/booking';
import { useAgentStore } from '@/stores/agent';
import BlankPlaceholder from '@/components/BlankPlaceholder';
import { formatOrderSeatLabels } from '@/utils/format';
import styles from './me.less';

const TABS = [
  { key: '', label: '全部' },
  { key: 'pending_pay', label: '待支付' },
  { key: 'issued', label: '已出票' },
  { key: 'redeemed', label: '已核销' },
  { key: 'cancelled', label: '已取消' },
  { key: 'expired', label: '已过期' },
];

const STATUS_CLASS: Record<string, string> = {
  pending_pay: styles.statusPending,
  issued: styles.statusIssued,
  redeemed: styles.statusIssued,
  cancelled: styles.statusCancelled,
  expired: styles.statusCancelled,
};

const STATUS_LABEL: Record<string, string> = {
  pending_pay: '待支付',
  issued: '已出票',
  redeemed: '已核销',
  cancelled: '已取消',
  expired: '已过期',
};

const MONTHS = ['JAN', 'FEB', 'MAR', 'APR', 'MAY', 'JUN', 'JUL', 'AUG', 'SEP', 'OCT', 'NOV', 'DEC'];

/**
 * 票根时间：取订单开场时间（order.startTime）的墙钟月/日/时分。
 * 按 ISO 字符串解析，避免 new Date() 受浏览器时区偏移。
 */
function stubParts(iso?: string | null) {
  if (!iso) return { month: '—', day: '—', time: '', label: '' };
  const matched = iso.match(/^(\d{4})-(\d{2})-(\d{2})[T ](\d{2}):(\d{2})/);
  if (matched) {
    const monthIdx = Number(matched[2]) - 1;
    return {
      month: MONTHS[monthIdx] || '—',
      day: matched[3],
      time: `${matched[4]}:${matched[5]}`,
      label: `${matched[1]}-${matched[2]}-${matched[3]} ${matched[4]}:${matched[5]}`,
    };
  }
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return { month: '—', day: '—', time: '', label: '' };
  return {
    month: MONTHS[d.getMonth()],
    day: String(d.getDate()).padStart(2, '0'),
    time: `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`,
    label: '',
  };
}

const OrdersPage: React.FC = () => {
  const [status, setStatus] = useState('');
  const [orders, setOrders] = useState<OrderVO[]>([]);
  const user = useAuthStore((s) => s.user);
  const openLogin = useAuthStore((s) => s.openLoginModal);
  const seatNameById = useBookingStore((s) => s.seatNameById);
  const openDrawer = useAgentStore((s) => s.openDrawer);

  const load = async () => {
    if (!user) {
      const ok = await openLogin();
      if (!ok) return;
    }
    try {
      const res = await orderApi.listOrders({ status: status || undefined, page: 1, size: 20 });
      setOrders(res.items);
    } catch {
      setOrders([]);
    }
  };

  useEffect(() => {
    void load();
  }, [status, user]);

  const pendingCount = orders.filter((o) => o.status === 'pending_pay').length;

  return (
    <div className="miaoyu-container" style={{ maxWidth: 1040 }}>
      <div className={styles.pageTitle}>
        <div>
          <span className="miaoyu-eyebrow">MY TICKETS</span>
          <h1>我的票夹</h1>
        </div>
        <button
          type="button"
          className="miaoyu-btn-secondary"
          onClick={() => openDrawer({ message: '帮我看看订单' })}
        >
          向 Agent 问订单
        </button>
      </div>

      <div className={styles.orderTabs}>
        {TABS.map((t) => (
          <button
            key={t.key}
            type="button"
            className={status === t.key ? styles.active : ''}
            onClick={() => setStatus(t.key)}
          >
            {t.label}
            {t.key === 'pending_pay' && pendingCount > 0 ? ` ${pendingCount}` : ''}
          </button>
        ))}
      </div>

      {orders.map((o) => {
        // 票根展示场次开场时间（非下单时间）
        const stub = stubParts(o.startTime);
        const muted = o.status === 'cancelled';
        return (
          <div key={o.orderId} className={`${styles.orderCard} ${muted ? styles.muted : ''}`}>
            <div className={styles.ticketStub} title="电影开场时间">
              <span>{stub.month}</span>
              <strong>{stub.day}</strong>
              <small>{stub.time || '—'}</small>
            </div>
            <div className={styles.orderInfo}>
              <span className={`${styles.status} ${STATUS_CLASS[o.status] || ''}`}>
                {STATUS_LABEL[o.status] || o.status}
              </span>
              <h2>{o.movieTitle}</h2>
              <p>
                {o.cinemaName ? `${o.cinemaName} · ` : ''}
                {o.hallName}
              </p>
              <p>开场 {stub.label || stub.time || '—'}</p>
              <p>{formatOrderSeatLabels(o, seatNameById) || '座位信息待确认'}</p>
            </div>
            <div className={styles.orderPrice}>
              <strong>¥{Number(o.amount).toFixed(2)}</strong>
              <div className={styles.orderActions}>
                {o.status === 'pending_pay' ? (
                  <>
                    <button
                      type="button"
                      className="miaoyu-btn-primary"
                      style={{ height: 36 }}
                      onClick={() => history.push(`/booking/pay?orderId=${o.orderId}`)}
                    >
                      继续支付
                    </button>
                    <button
                      type="button"
                      className="miaoyu-btn-ghost"
                      onClick={async () => {
                        await orderApi.cancelOrder(o.orderId);
                        message.success('已取消');
                        void load();
                      }}
                    >
                      取消
                    </button>
                  </>
                ) : null}
                {o.status === 'issued' ? (
                  <button
                    type="button"
                    className="miaoyu-btn-secondary"
                    style={{ height: 36 }}
                    onClick={() => history.push(`/booking/ticket?orderId=${o.orderId}`)}
                  >
                    查看取票码
                  </button>
                ) : null}
                <button
                  type="button"
                  className="miaoyu-btn-text"
                  onClick={() => history.push(`/me/orders/${o.orderId}`)}
                >
                  详情
                </button>
              </div>
            </div>
          </div>
        );
      })}
      {!orders.length ? <BlankPlaceholder variant="row" count={3} /> : null}
    </div>
  );
};

export default OrdersPage;
