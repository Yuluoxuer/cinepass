import React, { useEffect, useState } from 'react';
import { history, useLocation } from 'umi';
import { message, QRCode } from 'antd';
import * as orderApi from '@/api/order';
import type { OrderVO, RedeemQrVO } from '@/types';
import BlankPlaceholder from '@/components/BlankPlaceholder';
import { useBookingStore } from '@/stores/booking';
import { formatDateTime, formatOrderSeatLabels, mobileUrl } from '@/utils/format';
import styles from './booking.less';

const TicketPage: React.FC = () => {
  const loc = useLocation();
  const orderId = new URLSearchParams(loc.search).get('orderId') || '';
  const [order, setOrder] = useState<OrderVO | null>(null);
  const [redeemQr, setRedeemQr] = useState<RedeemQrVO | null>(null);
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
    setRedeemQr(null);
    void orderApi
      .getOrder(orderId)
      .then((o) => {
        setOrder(o);
        setMissing(false);
        // 已出票订单生成入场核销二维码
        if (o.status === 'issued') {
          void orderApi
            .getRedeemQrcode(orderId)
            .then(setRedeemQr)
            .catch(() => setRedeemQr(null));
        }
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
        <BlankPlaceholder variant="block" />
      </div>
    );
  }

  const seats = formatOrderSeatLabels(order, seatNameById);
  const redeemed = order.status === 'redeemed';

  return (
    <div className="miaoyu-container">
      <div className={styles.ticketOk}>
        <h1>{redeemed ? '✓ 已核销' : '✓ 购票成功'}</h1>
        {redeemed ? (
          <p>该票券已核销，可正常入场。</p>
        ) : (
          <>
            <div className={styles.qr} style={{ margin: '24px auto' }}>
              {redeemQr?.redeemUrl ? (
                <QRCode value={mobileUrl(redeemQr.redeemUrl)} size={180} bordered={false} />
              ) : (
                <BlankPlaceholder variant="block" style={{ width: 180, height: 180 }} />
              )}
            </div>
            <p style={{ color: '#999', fontSize: 13 }}>入场核销码 · 请到影院出示核销</p>
          </>
        )}
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
          {order.movieTitle} · {order.hallName} · {formatDateTime(order.startTime)}
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
