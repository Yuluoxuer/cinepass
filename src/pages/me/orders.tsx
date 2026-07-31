import React, { useEffect, useState } from 'react';
import { history } from 'umi';
import { message } from 'antd';
import * as orderApi from '@/api/order';
import type { OrderVO } from '@/types';
import { useAuthStore } from '@/stores/auth';
import { useBookingStore } from '@/stores/booking';

const TABS = [
  { key: '', label: '全部' },
  { key: 'pending_pay', label: '待支付' },
  { key: 'issued', label: '已出票' },
  { key: 'cancelled', label: '已取消' },
];

const STATUS: Record<string, string> = {
  pending_pay: '待支付',
  issued: '已出票',
  cancelled: '已取消',
};

const OrdersPage: React.FC = () => {
  const [status, setStatus] = useState('');
  const [orders, setOrders] = useState<OrderVO[]>([]);
  const user = useAuthStore((s) => s.user);
  const openLogin = useAuthStore((s) => s.openLoginModal);
  const seatNameById = useBookingStore((s) => s.seatNameById);

  const load = async () => {
    if (!user) {
      const ok = await openLogin();
      if (!ok) return;
    }
    const res = await orderApi.listOrders({ status: status || undefined, page: 1, size: 20 });
    setOrders(res.items);
  };

  useEffect(() => {
    void load();
  }, [status, user]);

  return (
    <div className="miaoyu-container">
      <h1>我的订单</h1>
      <div style={{ display: 'flex', gap: 16, margin: '16px 0' }}>
        {TABS.map((t) => (
          <button
            key={t.key}
            type="button"
            className={status === t.key ? 'miaoyu-btn-primary' : 'miaoyu-btn-ghost'}
            onClick={() => setStatus(t.key)}
          >
            {t.label}
          </button>
        ))}
      </div>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
        {orders.map((o) => (
          <div
            key={o.orderId}
            style={{ background: '#fff', borderRadius: 8, padding: 16 }}
          >
            <div style={{ display: 'flex', justifyContent: 'space-between' }}>
              <strong>{o.movieTitle}</strong>
              <span>{STATUS[o.status]}</span>
            </div>
            <p style={{ color: '#666', fontSize: 13 }}>
              {o.startTime.replace('T', ' ').slice(0, 16)} · {o.hallName} ·{' '}
              {o.seatIds.map((id) => seatNameById[id] || id).join('/')}
            </p>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <span style={{ color: '#e54847', fontWeight: 600 }}>¥{o.amount}</span>
              <div style={{ display: 'flex', gap: 8 }}>
                {o.status === 'pending_pay' ? (
                  <>
                    <button
                      type="button"
                      className="miaoyu-btn-primary"
                      style={{ height: 32 }}
                      onClick={() => history.push(`/booking/pay?orderId=${o.orderId}`)}
                    >
                      去支付
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
                    style={{ height: 32 }}
                    onClick={() => history.push(`/booking/ticket?orderId=${o.orderId}`)}
                  >
                    查看取票
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
        ))}
        {!orders.length ? <p style={{ color: '#999' }}>暂无订单</p> : null}
      </div>
    </div>
  );
};

export default OrdersPage;
