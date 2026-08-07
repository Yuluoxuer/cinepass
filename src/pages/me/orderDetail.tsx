import React, { useCallback, useEffect, useState } from 'react';
import { useParams, history } from 'umi';
import * as orderApi from '@/api/order';
import type { OrderVO } from '@/types';
import LoadingView from '@/components/LoadingView';
import StateView from '@/components/StateView';
import OrderQrModal from '@/components/OrderQrModal';
import { useBookingStore } from '@/stores/booking';
import { formatOrderSeatLabels } from '@/utils/format';

const STATUS_LABEL: Record<string, string> = {
  pending_pay: '待支付',
  issued: '已出票',
  redeemed: '已核销',
  cancelled: '已取消',
  expired: '已过期',
};

const OrderDetailPage: React.FC = () => {
  const { orderId } = useParams<{ orderId: string }>();
  const [order, setOrder] = useState<OrderVO | null>(null);
  const [loading, setLoading] = useState(true);
  const [missing, setMissing] = useState(false);
  const [qrOpen, setQrOpen] = useState(false);
  const [qrMode, setQrMode] = useState<'pay' | 'redeem'>('pay');
  const seatNameById = useBookingStore((s) => s.seatNameById);

  const loadOrder = useCallback(async () => {
    if (!orderId) {
      setLoading(false);
      setMissing(true);
      return;
    }
    setLoading(true);
    setMissing(false);
    try {
      const o = await orderApi.getOrder(orderId);
      setOrder(o);
      setMissing(false);
    } catch {
      setOrder(null);
      setMissing(true);
    } finally {
      setLoading(false);
    }
  }, [orderId]);

  useEffect(() => {
    void loadOrder();
  }, [loadOrder]);

  if (loading) {
    return (
      <div className="miaoyu-container">
        <LoadingView text="正在加载订单…" />
      </div>
    );
  }

  if (missing || !order) {
    return (
      <div className="miaoyu-container">
        <button type="button" className="miaoyu-btn-text" onClick={() => history.back()}>
          ← 返回
        </button>
        <StateView
          variant="error"
          title="订单不存在"
          description="订单可能已过期或已被删除，请返回查看其他订单。"
          actionLabel="返回"
          onAction={() => history.back()}
        />
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
        <p>状态：{STATUS_LABEL[order.status] || order.status}</p>
        <p>
          {order.cinemaName} · {order.hallName}
        </p>
        <p>{order.startTime.replace('T', ' ').slice(0, 16)}</p>
        <p>座位：{formatOrderSeatLabels(order, seatNameById)}</p>
        <p style={{ color: '#e54847', fontSize: 20, fontWeight: 700 }}>¥{order.amount}</p>
        {order.ticketCode ? <p>取票码：{order.ticketCode}</p> : null}
        <div style={{ marginTop: 20, display: 'flex', gap: 12, flexWrap: 'wrap' }}>
          {order.status === 'pending_pay' ? (
            <button
              type="button"
              className="miaoyu-btn-primary"
              onClick={() => {
                setQrMode('pay');
                setQrOpen(true);
              }}
            >
              支付二维码
            </button>
          ) : null}
          {order.status === 'issued' ? (
            <button
              type="button"
              className="miaoyu-btn-secondary"
              onClick={() => {
                setQrMode('redeem');
                setQrOpen(true);
              }}
            >
              核销二维码
            </button>
          ) : null}
          <button type="button" className="miaoyu-btn-ghost" onClick={() => history.back()}>
            返回
          </button>
        </div>
      </div>
      <OrderQrModal
        open={qrOpen}
        orderId={order.orderId}
        mode={qrMode}
        onClose={() => setQrOpen(false)}
        onDone={() => {
          setQrOpen(false);
          void loadOrder();
        }}
      />
    </div>
  );
};

export default OrderDetailPage;
