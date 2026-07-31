import React, { useEffect, useState } from 'react';
import { history, useLocation } from 'umi';
import { message } from 'antd';
import * as orderApi from '@/api/order';
import type { OrderVO } from '@/types';
import { useBookingStore } from '@/stores/booking';
import styles from './booking.less';

const TicketPage: React.FC = () => {
  const loc = useLocation();
  const orderId = new URLSearchParams(loc.search).get('orderId') || '';
  const [order, setOrder] = useState<OrderVO | null>(null);
  const seatNameById = useBookingStore((s) => s.seatNameById);

  useEffect(() => {
    if (!orderId) return;
    void orderApi.getOrder(orderId).then(setOrder);
  }, [orderId]);

  if (!order) return <div className="miaoyu-container">加载中…</div>;

  const seats = order.seatIds.map((id) => seatNameById[id] || id).join('、');

  return (
    <div className="miaoyu-container">
      <div className={styles.ticketOk}>
        <h1>✓ 购票成功</h1>
        <div className={styles.qr} style={{ margin: '24px auto' }}>
          {order.qrPayload || '取票码'}
        </div>
        <p>
          取票码 {order.ticketCode}{' '}
          <button
            type="button"
            className="miaoyu-btn-text"
            onClick={() => {
              void navigator.clipboard?.writeText(order.ticketCode || '');
              message.success('已复制');
            }}
          >
            复制
          </button>
        </p>
        <hr style={{ border: 'none', borderTop: '1px solid #eee', margin: '24px 0' }} />
        <p>
          {order.movieTitle} · {order.hallName} · {order.startTime.replace('T', ' ').slice(0, 16)}
        </p>
        <p>
          {seats} · ¥{order.amount}
        </p>
        <div style={{ marginTop: 24, display: 'flex', gap: 16, justifyContent: 'center' }}>
          <button
            type="button"
            className="miaoyu-btn-secondary"
            onClick={() => history.push(`/me/orders/${order.orderId}`)}
          >
            查看订单详情
          </button>
          <button type="button" className="miaoyu-btn-ghost" onClick={() => history.push('/')}>
            返回首页
          </button>
        </div>
      </div>
    </div>
  );
};

export default TicketPage;
