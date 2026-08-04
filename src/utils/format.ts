/** 兼容旧引用的轻量 format 工具 */
export function formatMoney(n: number) {
  return `¥${n.toFixed(2)}`;
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
