import React, { useEffect, useState } from 'react';
import { useParams, useLocation } from 'umi';
import { message, QRCode } from 'antd';
import * as orderApi from '@/api/order';
import { qrFlowErrorMessage } from '@/api/error';
import type { OrderVO, PaySessionVO } from '@/types';
import { ApiError } from '@/types';
import { useLockCountdown } from '@/features/seatmap/useLockCountdown';
import { formatDateTime } from '@/utils/format';
import '@/styles/tokens.css';
import styles from './pay.less';

const STATUS_TEXT: Record<PaySessionVO['status'], string> = {
  pending_pay: '待支付',
  issued: '已支付',
  cancelled: '已取消',
  redeemed: '已核销',
  expired: '已过期',
};

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
        setError(e instanceof ApiError ? qrFlowErrorMessage(e, 'pay') : '加载失败');
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
      message.error(e instanceof ApiError ? qrFlowErrorMessage(e, 'pay') : '支付失败');
    } finally {
      setLoading(false);
    }
  };

  // Hook 必须无条件、每次渲染按相同顺序调用，必须在下方所有条件早退之前。
  // session 为空时传 undefined 安全（useLockCountdown 内部会置 0）。
  const { text, expired } = useLockCountdown(session?.expireAt);

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
          <p>
            {order.movieTitle} · {order.cinemaName} · {order.hallName}
          </p>
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
          {order.payAt ? <p>支付时间 {formatDateTime(order.payAt, true)}</p> : null}
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

  const seatText =
    session.seatNames && session.seatNames.length
      ? session.seatNames.join('、')
      : session.seatIds.join('、');

  return (
    <div className={styles.page}>
      <header>妙语购票 · 扫码支付</header>
      <div className={styles.box}>
        <h2>{session.movieTitle}</h2>
        <p>订单号 {session.orderId}</p>
        <p>
          {session.cinemaName} · {session.hallName}
        </p>
        <p>
          {formatDateTime(session.startTime)} · 座位：{seatText || '—'}
        </p>
        {session.ticketCode ? <p>取票码 {session.ticketCode}</p> : null}
        <hr />
        <div className={styles.amountLabel}>应付金额</div>
        <div className={styles.amount}>¥ {session.amount.toFixed(2)}</div>
        {session.status === 'pending_pay' ? (
          <>
            <p style={{ textAlign: 'center', color: expired ? '#d9423a' : '#999', marginBottom: 12 }}>
              {expired ? '支付已超时，请重新下单' : `支付剩余 ${text}`}
            </p>
            <button type="button" className={styles.payBtn} disabled={loading || expired} onClick={pay}>
              {loading ? '支付中…' : '确认付款'}
            </button>
          </>
        ) : (
          <p style={{ textAlign: 'center', color: '#999' }}>
            订单状态：{STATUS_TEXT[session.status] || session.status}
          </p>
        )}
      </div>
    </div>
  );
};

export default MobilePayPage;
