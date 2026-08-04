import React, { useEffect, useState } from 'react';
import { useParams, history } from 'umi';
import * as orderApi from '@/api/order';
import type { OrderVO } from '@/types';
import BlankPlaceholder from '@/components/BlankPlaceholder';
import { useBookingStore } from '@/stores/booking';
import { formatOrderSeatLabels } from '@/utils/format';

const OrderDetailPage: React.FC = () => {
  const { orderId } = useParams<{ orderId: string }>();
  const [order, setOrder] = useState<OrderVO | null>(null);
  const [loading, setLoading] = useState(true);
  const [missing, setMissing] = useState(false);
  const seatNameById = useBookingStore((s) => s.seatNameById);

  useEffect(() => {
    if (!orderId) {
      setLoading(false);
      setMissing(true);
      return;
    }
    setLoading(true);
    setMissing(false);
    void orderApi
      .getOrder(orderId)
      .then((o) => {
        setOrder(o);
        setMissing(false);
      })
      .catch(() => {
        setOrder(null);
        setMissing(true);
      })
      .finally(() => setLoading(false));
  }, [orderId]);

  if (loading) {
    return (
      <div className="miaoyu-container">
        <BlankPlaceholder variant="block" />
      </div>
    );
  }

  if (missing || !order) {
    return (
      <div className="miaoyu-container">
        <button type="button" className="miaoyu-btn-text" onClick={() => history.back()}>
          ← 返回
        </button>
        <BlankPlaceholder variant="block" style={{ marginTop: 12 }} />
      </div>
    );
  }

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
        <p>座位：{formatOrderSeatLabels(order, seatNameById)}</p>
        <p style={{ color: '#e54847', fontSize: 20, fontWeight: 700 }}>¥{order.amount}</p>
        {order.ticketCode ? <p>取票码：{order.ticketCode}</p> : null}
        {order.qrPayload ? <p style={{ wordBreak: 'break-all' }}>QR：{order.qrPayload}</p> : null}
      </div>
    </div>
  );
};

export default OrderDetailPage;
