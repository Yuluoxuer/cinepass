import dayjs from 'dayjs';
import { reachableHost } from '@/utils/lanIp';

/**
 * 后端瞬时时间统一为 ISO-8601 带偏移（通常 +08:00，如 2026-08-11T19:30:00+08:00）。
 * 以下展示函数解析后按浏览器本地时区格式化；禁止 replace('T',' ').slice() 直接截取。
 * 写入后端请用 toApiDateTime；日历日用 YYYY-MM-DD。
 */

/** ISO（带偏移）→ 本地 "YYYY-MM-DD HH:mm"（withSeconds 时到秒）；空值返回 ''，非法值原样返回 */
export function formatDateTime(iso?: string | null, withSeconds = false): string {
  if (!iso) return '';
  const d = dayjs(iso);
  return d.isValid() ? d.format(withSeconds ? 'YYYY-MM-DD HH:mm:ss' : 'YYYY-MM-DD HH:mm') : iso;
}

/** ISO（带偏移）→ 本地日期 "YYYY-MM-DD"；空值返回 '' */
export function formatDate(iso?: string | null): string {
  if (!iso) return '';
  const d = dayjs(iso);
  return d.isValid() ? d.format('YYYY-MM-DD') : iso;
}

/** ISO（带偏移）→ 本地时刻 "HH:mm"；空值返回 '' */
export function formatTime(iso?: string | null): string {
  if (!iso) return '';
  const d = dayjs(iso);
  return d.isValid() ? d.format('HH:mm') : iso;
}

/** 管理端/写接口：dayjs 墙钟 → 东八区 ISO（与后端 DateTimeFormats 一致） */
export function toApiDateTime(d: dayjs.Dayjs): string {
  return d.format('YYYY-MM-DDTHH:mm:ss+08:00');
}

/** 兼容旧引用的轻量 format 工具 */
export function formatMoney(n: number) {
  return `¥${n.toFixed(2)}`;
}

/**
 * 后端 public-base-url 默认指向后端自身（如 http://localhost:8080），
 * 拼出的 payUrl/redeemUrl 需改写为当前前端源，否则二维码扫码打到错误 host。
 * 已指向当前源时原样返回；host 是 localhost 时自动替换为本机局域网 IP，
 * 保证顾客手机扫码能连到这台机器（见 reachableHost）。
 */
export function mobileUrl(raw?: string | null): string {
  if (!raw) return '';
  try {
    const u = new URL(raw, window.location.origin);
    u.protocol = window.location.protocol;
    u.host = reachableHost(window.location.host);
    return u.toString();
  } catch {
    return raw;
  }
}

export function formatDistance(meters: number | null | undefined) {
  if (meters == null) return '';
  return meters >= 1000 ? `${(meters / 1000).toFixed(1)}km` : `${meters}m`;
}

/** 本地日历日 YYYY-MM-DD（勿用 toISOString，UTC+8 会偏一天） */
export function localDateISO(d: Date = new Date()) {
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${y}-${m}-${day}`;
}

/** 相对本地日历日加减天数 */
export function addLocalDays(isoDate: string, days: number) {
  const [y, m, d] = isoDate.split('-').map(Number);
  const dt = new Date(y, m - 1, d);
  dt.setDate(dt.getDate() + days);
  return localDateISO(dt);
}

/**
 * 订单座位展示文案：优先 seatPrices.seatName，其次客户端 seatNameById，最后 seatId。
 */
export function formatOrderSeatLabels(
  order: { seatIds?: string[] | null; seatPrices?: Array<{ seatId: string; seatName?: string | null }> | null },
  seatNameById?: Record<string, string> | null,
): string {
  const ids = order.seatIds || [];
  if (!ids.length && order.seatPrices?.length) {
    return order.seatPrices
      .map((p) => p.seatName || seatNameById?.[p.seatId] || p.seatId)
      .filter(Boolean)
      .join('、');
  }
  const nameFromSnapshot = new Map(
    (order.seatPrices || [])
      .filter((p) => p.seatId && p.seatName)
      .map((p) => [p.seatId, p.seatName as string]),
  );
  return ids
    .map((id) => nameFromSnapshot.get(id) || seatNameById?.[id] || id)
    .join('、');
}
