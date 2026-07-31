/** 兼容旧引用的轻量 format 工具 */
export function formatMoney(n: number) {
  return `¥${n.toFixed(2)}`;
}

export function formatDistance(meters: number | null | undefined) {
  if (meters == null) return '';
  return meters >= 1000 ? `${(meters / 1000).toFixed(1)}km` : `${meters}m`;
}
