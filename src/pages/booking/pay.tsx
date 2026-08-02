import React, { useEffect, useRef, useState } from 'react';
import { history, useLocation } from 'umi';
import { message, QRCode } from 'antd';
import * as orderApi from '@/api/order';
import type { OrderVO, PayQrVO } from '@/types';
import BookingProgress from '@/components/BookingProgress';
import { useLockCountdown } from '@/features/seatmap/useLockCountdown';
import { getSessionId } from '@/stores/booking';
import styles from './booking.less';

const PayPage: React.FC = () => {
  const loc = useLocation();
  const orderId = new URLSearchParams(loc.search).get('orderId') || '';
  const [order, setOrder] = useState<OrderVO | null>(null);
  const [qr, setQr] = useState<PayQrVO | null>(null);
  const timer = useRef<ReturnType<typeof setInterval> | null>(null);
  const { text, expired } = useLockCountdown(qr?.expireAt || order?.expireAt);

  useEffect(() => {
    if (!orderId) return;
    void orderApi.getOrder(orderId).then(setOrder);
    void orderApi.getPayQrcode(orderId).then(setQr);
  }, [orderId]);

  useEffect(() => {
    if (!orderId || !qr) return;
    const interval = qr.pollIntervalMs || 2000;
    timer.current = setInterval(async () => {
      try {
        const o = await orderApi.getOrder(orderId);
        setOrder(o);
        if (o.status === 'issued') {
          if (timer.current) clearInterval(timer.current);
          history.replace(`/booking/ticket?orderId=${orderId}`);
        }
      } catch {
        /* ignore */
      }
    }, interval);
    return () => {
      if (timer.current) clearInterval(timer.current);
    };
  }, [orderId, qr]);

  const desktopPay = async () => {
    const sid = getSessionId() || undefined;
    const o = await orderApi.payOrder(orderId, { channel: 'desktop_button', sessionId: sid });
    message.success('支付成功');
    history.replace(`/booking/ticket?orderId=${o.orderId}`);
  };

  return (
    <div>
      <BookingProgress step={5} />
      <div className="miaoyu-container">
        <h2 className={styles.title}>扫码支付</h2>
        <div className={styles.confirmGrid}>
          <div className={styles.card}>
            <h3>订单摘要</h3>
            {order ? (
              <>
                <p>{order.movieTitle}</p>
                <p>
                  {order.cinemaName} · {order.hallName}
                </p>
                <p>{order.startTime.replace('T', ' ').slice(0, 16)}</p>
                <div className={styles.amount}>¥{order.amount}</div>
              </>
            ) : null}
          </div>
          <div className={`${styles.card} ${styles.payBox}`}>
            {expired ? (
              <div>
                <p>锁座已过期</p>
                <button type="button" className="miaoyu-btn-primary" onClick={() => history.push('/')}>
                  回首页重选
                </button>
              </div>
            ) : qr ? (
              <>
                <div className={styles.qr}>
                  <QRCode
                    value={qr.payUrl}
                    type="svg"
                    size={224}
                    errorLevel="M"
                    bordered={false}
                  />
                </div>
                <a href={qr.payUrl} target="_blank" rel="noreferrer">
                  在手机上打开支付页
                </a>
                <p>请使用手机扫码完成支付 · 等待确认中…</p>
                <p>⏱ 锁座剩余 {text}</p>
                <button type="button" className="miaoyu-btn-ghost" onClick={desktopPay}>
                  我已在电脑完成支付（演示）
                </button>
              </>
            ) : (
              <div className="miaoyu-skeleton" style={{ width: 200, height: 200 }} />
            )}
          </div>
        </div>
      </div>
    </div>
  );
};

export default PayPage;
