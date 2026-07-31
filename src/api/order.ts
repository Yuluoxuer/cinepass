import { get, post, del, idempotencyKey } from './client';
import type {
  LockVO,
  OrderVO,
  PageResult,
  PayQrVO,
  PaySessionVO,
} from '@/types';

export function lockSeats(body: { showId: string; seatIds: string[]; sessionId?: string }) {
  return post<LockVO>('/locks', body, {
    headers: { 'Idempotency-Key': idempotencyKey('lock') },
  });
}

export function unlockSeats(lockId: string, sessionId?: string) {
  return del<{ released: boolean }>(`/locks/${lockId}`, { sessionId });
}

export function createOrder(body: { lockId: string; sessionId?: string }) {
  return post<OrderVO>('/orders', body, {
    headers: { 'Idempotency-Key': idempotencyKey('order') },
  });
}

export function listOrders(params?: { status?: string; page?: number; size?: number }) {
  return get<PageResult<OrderVO>>('/orders', params);
}

export function getOrder(orderId: string) {
  return get<OrderVO>(`/orders/${orderId}`);
}

export function cancelOrder(orderId: string, reason?: string) {
  return post<OrderVO>(
    `/orders/${orderId}/cancel`,
    reason ? { reason } : {},
    { headers: { 'Idempotency-Key': idempotencyKey('cancel') } },
  );
}

export function getPayQrcode(orderId: string) {
  return get<PayQrVO>(`/orders/${orderId}/pay-qrcode`);
}

export function getPaySession(orderId: string, t: string) {
  return get<PaySessionVO>(`/orders/${orderId}/pay-session`, { t }, { skipAuth: true });
}

export function payOrder(
  orderId: string,
  body: { channel: string; sessionId?: string },
  payToken?: string,
) {
  const headers: Record<string, string> = {
    'Idempotency-Key': idempotencyKey('pay'),
  };
  if (payToken) headers['X-Pay-Token'] = payToken;
  return post<OrderVO>(`/orders/${orderId}/pay`, body, {
    headers,
    skipAuth: !!payToken,
  });
}
