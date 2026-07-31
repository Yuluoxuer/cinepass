import React, { useEffect, useState } from 'react';
import { useParams, history } from 'umi';
import * as orderApi from '@/api/order';
import type { OrderVO } from '@/types';
import { useBookingStore } from '@/stores/booking';

const OrderDetailPage: React.FC = () => {
  const { orderId } = useParams<{ orderId: string }>();
  const [order, setOrder] = useState<OrderVO | null>(null);
  const seatNameById = useBookingStore((s) => s.seatNameById);

  useEffect(() => {
    if (orderId) void orderApi.getOrder(orderId).then(setOrder);
  }, [orderId]);

  if (!order) return <div className="miaoyu-container">加载中…</div>;

  return (
    <div className="miaoyu-container">
      <button type="button" className="miaoyu-btn-text" onClick={() => history.back()}>
        ← 返回
      </button>
      <div style={{ background: '#fff', borderRadius: 12, padding: 24, marginTop: 12 }}>
        <h1>{order.movieTitle}</h1>
        <p>状态：{order.status}</p>
        <p>
          {order.cinemaName} · {order.hallName}
        </p>
        <p>{order.startTime.replace('T', ' ').slice(0, 16)}</p>
        <p>座位：{order.seatIds.map((id) => seatNameById[id] || id).join('、')}</p>
        <p style={{ color: '#e54847', fontSize: 20, fontWeight: 700 }}>¥{order.amount}</p>
        {order.ticketCode ? <p>取票码：{order.ticketCode}</p> : null}
        {order.qrPayload ? <p style={{ wordBreak: 'break-all' }}>QR：{order.qrPayload}</p> : null}
      </div>
    </div>
  );
};

export default OrderDetailPage;
