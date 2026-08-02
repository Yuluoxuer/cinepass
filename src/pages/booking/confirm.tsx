import React, { useEffect, useState } from 'react';
import { history, useLocation } from 'umi';
import { Modal, message } from 'antd';
import * as orderApi from '@/api/order';
import type { OrderVO } from '@/types';
import BookingProgress from '@/components/BookingProgress';
import { useLockCountdown } from '@/features/seatmap/useLockCountdown';
import { useBookingStore } from '@/stores/booking';
import styles from './booking.less';

const ConfirmPage: React.FC = () => {
  const loc = useLocation();
  const orderId =
    new URLSearchParams(loc.search).get('orderId') ||
    useBookingStore.getState().draft?.orderId ||
    '';
  const [order, setOrder] = useState<OrderVO | null>(null);
  const seatNameById = useBookingStore((s) => s.seatNameById);
  const patchLocal = useBookingStore((s) => s.patchLocal);
  const rollbackDependent = useBookingStore((s) => s.rollbackDependent);
  const { text, warning, expired } = useLockCountdown(order?.expireAt);

  useEffect(() => {
    if (!orderId) return;
    void orderApi.getOrder(orderId).then(setOrder);
  }, [orderId]);

  if (!order) return <div className="miaoyu-container">加载订单…</div>;

  const seatText = order.seatIds.map((id) => seatNameById[id] || id).join('、');

  const onCancel = () => {
    Modal.confirm({
      title: '确定取消？座位将释放',
      onOk: async () => {
        await orderApi.cancelOrder(order.orderId);
        rollbackDependent('SelectSeat');
        await patchLocal({ state: 'SelectSeat', seatIds: [] }, { debounce: false });
        message.success('座位已释放，请重新选择');
        history.push(`/booking/seats?showId=${order.showId}`);
      },
    });
  };

  return (
    <div>
      <BookingProgress step={5} />
      <div className="miaoyu-container">
        <h2 className={styles.title}>确认订单</h2>
        <div className={styles.confirmGrid}>
          <div className={styles.card}>
            <h3>订单信息</h3>
            <p>影片：{order.movieTitle}</p>
            <p>影院：{order.cinemaName}</p>
            <p>影厅：{order.hallName}</p>
            <p>场次：{order.startTime.replace('T', ' ').slice(0, 16)}</p>
            <p style={{ color: '#faad14', marginTop: 16 }}>⚠ 快照已锁定，改座需取消重选</p>
          </div>
          <div className={styles.card}>
            <h3>支付信息</h3>
            <p>座位：{seatText}</p>
            <p>
              单价：¥{order.unitPrice} × {order.seatIds.length}
            </p>
            <div className={styles.amount}>合计：¥{order.amount}</div>
            <div className={`${styles.countdown} ${warning ? styles.countdownWarn : ''}`}>
              ⏱ 剩余支付时间 {expired ? '00:00' : text}
            </div>
            <button
              type="button"
              className="miaoyu-btn-primary"
              style={{ width: '100%', marginBottom: 12 }}
              disabled={expired}
              onClick={() => history.push(`/booking/pay?orderId=${order.orderId}`)}
            >
              去支付
            </button>
            <button type="button" className="miaoyu-btn-text" onClick={onCancel}>
              取消并重新选座
            </button>
          </div>
        </div>
      </div>
    </div>
  );
};

export default ConfirmPage;
