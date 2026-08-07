import React, { useCallback, useEffect, useRef, useState } from 'react';
import { Modal, QRCode } from 'antd';
import { history } from 'umi';
import * as orderApi from '@/api/order';
import { qrFlowErrorMessage } from '@/api/error';
import type { OrderVO, PayQrVO, RedeemQrVO } from '@/types';
import { ApiError } from '@/types';
import { mobileUrl } from '@/utils/format';
import BlankPlaceholder from '@/components/BlankPlaceholder';
import { useLockCountdown } from '@/features/seatmap/useLockCountdown';

interface OrderQrModalProps {
  open: boolean;
  orderId: string;
  mode: 'pay' | 'redeem';
  onClose: () => void;
  /** pay 模式订单离开 pending_pay、redeem 模式订单已核销时回调（用于关闭弹窗并刷新列表） */
  onDone?: () => void;
}

/**
 * 订单二维码弹窗：pay 模式生成支付二维码（扫码后轮询订单直至离开待支付，触发 onDone）；
 * redeem 模式生成入场核销二维码（扫码核销成功后轮询到 redeemed，触发 onDone 自动关闭）。
 * 均免登录，扫码直接进入手机 H5。
 */
const OrderQrModal: React.FC<OrderQrModalProps> = ({ open, orderId, mode, onClose, onDone }) => {
  const [qr, setQr] = useState<PayQrVO | RedeemQrVO | null>(null);
  const [order, setOrder] = useState<OrderVO | null>(null);
  const [error, setError] = useState('');
  const timer = useRef<ReturnType<typeof setInterval> | null>(null);
  const doneRef = useRef(onDone);
  doneRef.current = onDone;

  const load = useCallback(async () => {
    setError('');
    setQr(null);
    setOrder(null);
    if (!orderId) {
      setError('订单缺失');
      return;
    }
    try {
      if (mode === 'pay') {
        setQr(await orderApi.getPayQrcode(orderId));
      } else {
        setQr(await orderApi.getRedeemQrcode(orderId));
      }
    } catch (e) {
      setError(e instanceof ApiError ? qrFlowErrorMessage(e, mode) : '二维码生成失败');
    }
  }, [orderId, mode]);

  useEffect(() => {
    if (!open) return;
    void load();
    return () => {
      if (timer.current) clearInterval(timer.current);
    };
  }, [open, load]);

  const payExpireAt = mode === 'pay' && qr && 'expireAt' in qr ? qr.expireAt : null;
  const { text, expired } = useLockCountdown(payExpireAt);

  // pay 模式：按后端建议间隔轮询订单，状态离开待支付后回调 onDone 关闭并刷新
  useEffect(() => {
    if (!open || mode !== 'pay' || !qr || !('pollIntervalMs' in qr)) return;
    if (expired) {
      if (timer.current) clearInterval(timer.current);
      return;
    }
    const interval = qr.pollIntervalMs || 2000;
    timer.current = setInterval(async () => {
      try {
        const o = await orderApi.getOrder(orderId);
        setOrder(o);
        if (o.status !== 'pending_pay') {
          if (timer.current) clearInterval(timer.current);
          doneRef.current?.();
        }
      } catch {
        /* ignore */
      }
    }, interval);
    return () => {
      if (timer.current) clearInterval(timer.current);
    };
  }, [open, mode, orderId, qr, expired]);

  // redeem 模式：轮询订单，状态变为已核销后回调 onDone 自动关闭并刷新；
  // 已取消/已过期时停止轮询，保留弹窗展示对应状态文案。
  useEffect(() => {
    if (!open || mode !== 'redeem' || !qr) return;
    timer.current = setInterval(async () => {
      try {
        const o = await orderApi.getOrder(orderId);
        setOrder(o);
        if (o.status === 'redeemed') {
          if (timer.current) clearInterval(timer.current);
          doneRef.current?.();
        } else if (o.status === 'cancelled' || o.status === 'expired') {
          if (timer.current) clearInterval(timer.current);
        }
      } catch {
        /* ignore */
      }
    }, 2000);
    return () => {
      if (timer.current) clearInterval(timer.current);
    };
  }, [open, mode, orderId, qr]);

  const title = mode === 'pay' ? '支付二维码' : '核销二维码';

  const renderBody = () => {
    if (error) {
      return <div style={{ textAlign: 'center', padding: '24px 0', color: '#d9423a' }}>{error}</div>;
    }
    if (!qr) {
      return (
        <div style={{ padding: '24px 0' }}>
          <BlankPlaceholder variant="block" />
        </div>
      );
    }
    if (order?.status === 'issued' || order?.status === 'redeemed') {
      return (
        <div style={{ textAlign: 'center', padding: '8px 0' }}>
          <p style={{ color: '#16806f', fontSize: 16, fontWeight: 700 }}>
            ✓ {order.status === 'issued' ? '已支付' : '已核销'}
          </p>
          <p>{order.status === 'issued' ? '订单已出票，可前往查看取票/核销码。' : '该票券已核销，可正常入场。'}</p>
          {order.status === 'issued' ? (
            <button
              type="button"
              className="miaoyu-btn-primary"
              onClick={() => {
                onClose();
                history.push(`/booking/ticket?orderId=${orderId}`);
              }}
            >
              查看取票码
            </button>
          ) : null}
        </div>
      );
    }
    if (order?.status === 'cancelled') {
      return <div style={{ textAlign: 'center', padding: '24px 0', color: '#999' }}>订单已取消</div>;
    }
    const url = 'redeemUrl' in qr ? qr.redeemUrl : qr.payUrl;
    return (
      <div style={{ textAlign: 'center', padding: '8px 0' }}>
        <QRCode value={mobileUrl(url)} size={220} bordered={false} />
        <p style={{ marginTop: 12, color: '#666' }}>
          {mode === 'pay' ? '请使用手机扫码完成支付' : '请使用手机扫码核销入场'}
        </p>
        {'ticketCode' in qr && qr.ticketCode ? <p>取票码 {qr.ticketCode}</p> : null}
        {mode === 'pay' && 'amount' in qr ? (
          <>
            <p style={{ color: '#e54847', fontWeight: 700 }}>¥{qr.amount.toFixed(2)}</p>
            <p style={{ color: expired ? '#d9423a' : '#999' }}>
              {expired ? '支付已超时，请重新下单' : `支付截止剩余 ${text}`}
            </p>
          </>
        ) : null}
      </div>
    );
  };

  return (
    <Modal open={open} title={title} onCancel={onClose} footer={null} width={360}>
      {renderBody()}
    </Modal>
  );
};

export default OrderQrModal;
