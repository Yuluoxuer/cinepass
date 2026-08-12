import React, { useEffect, useState } from 'react';
import { useParams, useLocation } from 'umi';
import { message } from 'antd';
import * as orderApi from '@/api/order';
import { qrFlowErrorMessage } from '@/api/error';
import type { OrderVO, PaySessionVO } from '@/types';
import { ApiError } from '@/types';
import { formatDateTime } from '@/utils/format';
import '@/styles/tokens.css';
import styles from './pay.less';

const STATUS_TEXT: Record<PaySessionVO['status'], string> = {
  pending_pay: '待支付',
  issued: '已出票',
  cancelled: '已取消',
  redeemed: '已核销',
  expired: '已过期',
};

const MobileRedeemPage: React.FC = () => {
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
      .getRedeemSession(orderId, token)
      .then(setSession)
      .catch((e) => {
        setError(e instanceof ApiError ? qrFlowErrorMessage(e, 'redeem') : '加载失败');
      });
  }, [orderId, token]);

  const redeem = async () => {
    if (!orderId || !token) return;
    setLoading(true);
    try {
      const o = await orderApi.redeemOrder(orderId, token);
      setOrder(o);
      message.success('核销成功');
    } catch (e) {
      message.error(e instanceof ApiError ? qrFlowErrorMessage(e, 'redeem') : '核销失败');
    } finally {
      setLoading(false);
    }
  };

  if (error) {
    return (
      <div className={styles.page}>
        <header>妙语购票 · 入场核销</header>
        <div className={styles.box}>
          <p className={styles.err}>{error}</p>
        </div>
      </div>
    );
  }

  if (!session && !order) {
    return (
      <div className={styles.page}>
        <header>妙语购票 · 入场核销</header>
        <div className={styles.box}>加载中…</div>
      </div>
    );
  }

  const status = (order?.status || session?.status) as PaySessionVO['status'];
  const isRedeemed = status === 'redeemed';
  const notRedeemable = status === 'pending_pay' || status === 'cancelled' || status === 'expired';
  const movieTitle = order?.movieTitle || session?.movieTitle;
  const cinemaName = order?.cinemaName || session?.cinemaName;
  const hallName = order?.hallName || session?.hallName;
  const startTime = order?.startTime || session?.startTime;
  const ticketCode = order?.ticketCode || session?.ticketCode;
  const seatText =
    session?.seatNames && session.seatNames.length
      ? session.seatNames.join('、')
      : session?.seatIds?.join('、') || '—';

  return (
    <div className={styles.page}>
      <header>妙语购票 · 入场核销</header>
      <div className={styles.box}>
        <h2>{isRedeemed ? '✓ 核销成功' : notRedeemable ? '无法核销' : '确认核销'}</h2>
        {movieTitle ? (
          <p>
            {movieTitle} · {cinemaName} · {hallName}
          </p>
        ) : null}
        <p>订单号 {session?.orderId || order?.orderId}</p>
        {startTime ? <p>{formatDateTime(startTime)}</p> : null}
        {session?.seatNames ? <p>座位：{seatText}</p> : null}
        {ticketCode ? (
          <p>
            取票码 {ticketCode}{' '}
            <button
              type="button"
              onClick={() => {
                void navigator.clipboard?.writeText(ticketCode);
                message.success('已复制');
              }}
            >
              复制
            </button>
          </p>
        ) : null}
        {isRedeemed ? (
          <p>该票券已核销，可正常入场。</p>
        ) : notRedeemable ? (
          <p>仅已出票订单可核销，当前状态：{STATUS_TEXT[status] || status}。</p>
        ) : (
          <button type="button" className={styles.payBtn} disabled={loading} onClick={redeem}>
            {loading ? '核销中…' : '确认核销'}
          </button>
        )}
      </div>
    </div>
  );
};

export default MobileRedeemPage;
