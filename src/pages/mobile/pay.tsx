import React, { useEffect, useState } from 'react';
import { useParams, useLocation } from 'umi';
import { message, QRCode } from 'antd';
import * as orderApi from '@/api/order';
import type { OrderVO, PaySessionVO } from '@/types';
import { ApiError } from '@/types';
import '@/styles/tokens.css';
import styles from './pay.less';

const MobilePayPage: React.FC = () => {
  const { orderId } = useParams<{ orderId: string }>();
  const loc = useLocation();
  const token = new URLSearchParams(loc.search).get('t') || '';
  const [session, setSession] = useState<PaySessionVO | null>(null);
  const [order, setOrder] = useState<OrderVO | null>(null);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (!orderId || !token) {
      setError('链接无效');
      return;
    }
    void orderApi
      .getPaySession(orderId, token)
      .then(setSession)
      .catch((e) => {
        if (e instanceof ApiError) {
          if (e.errorCode === 'LOCK_EXPIRED') setError('锁座已过期，请回电脑重新选座');
          else if (e.errorCode === 'PAY_TOKEN_INVALID' || e.errorCode === 'PAY_TOKEN_EXPIRED')
            setError('链接已失效');
          else setError(e.message);
        } else setError('加载失败');
      });
  }, [orderId, token]);

  const pay = async () => {
    if (!orderId || !token) return;
    setLoading(true);
    try {
      const o = await orderApi.payOrder(orderId, { channel: 'mobile_qr' }, token);
      setOrder(o);
      message.success('支付成功');
    } catch (e) {
      if (e instanceof ApiError && e.errorCode === 'LOCK_EXPIRED') {
        setError('锁座已过期，请回电脑重新选座');
      } else {
        message.error(e instanceof Error ? e.message : '支付失败');
      }
    } finally {
      setLoading(false);
    }
  };

  if (error) {
    return (
      <div className={styles.page}>
        <header>妙语购票 · 确认支付</header>
        <div className={styles.box}>
          <p className={styles.err}>{error}</p>
        </div>
      </div>
    );
  }

  if (order?.status === 'issued') {
    return (
      <div className={styles.page}>
        <header>妙语购票 · 支付成功</header>
        <div className={styles.box}>
          <h2>✓ 支付成功</h2>
          {order.qrPayload ? <QRCode value={order.qrPayload} size={180} bordered={false} /> : null}
          <p>
            取票码 {order.ticketCode}{' '}
            <button
              type="button"
              onClick={() => {
                void navigator.clipboard?.writeText(order.ticketCode || '');
                message.success('已复制');
              }}
            >
              复制
            </button>
          </p>
        </div>
      </div>
    );
  }

  if (!session) {
    return (
      <div className={styles.page}>
        <header>妙语购票 · 确认支付</header>
        <div className={styles.box}>加载中…</div>
      </div>
    );
  }

  return (
    <div className={styles.page}>
      <header>妙语购票 · 确认支付</header>
      <div className={styles.box}>
        <h2>{session.movieTitle}</h2>
        <p>
          {session.cinemaName} · {session.hallName}
        </p>
        <p>
          {session.startTime.replace('T', ' ').slice(0, 16)} · 座位：{session.seatIds.join('、')}
        </p>
        <hr />
        <div className={styles.amountLabel}>应付金额</div>
        <div className={styles.amount}>¥ {session.amount.toFixed(2)}</div>
        <button type="button" className={styles.payBtn} disabled={loading} onClick={pay}>
          {loading ? '支付中…' : '确认付款'}
        </button>
      </div>
    </div>
  );
};

export default MobilePayPage;
