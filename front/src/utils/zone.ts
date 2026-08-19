/** 分区展示：MVP 仅 zoneCode；文案「{code}区」；颜色前端调色板（可改） */

const ZONE_PALETTE = [
  '#ffd666',
  '#91caff',
  '#b7eb8f',
  '#ffadd2',
  '#d3adf7',
  '#87e8de',
  '#ffbb96',
  '#adc6ff',
];

/** 兼容旧种子 normal/golden */
const LEGACY_ZONE_COLOR: Record<string, string> = {
  A: '#ffd666',
  B: '#91caff',
  C: '#b7eb8f',
  D: '#d9d9d9',
  golden: '#ffd666',
  normal: '#e8e8e8',
};

export function zoneLabel(zone: string): string {
  if (!zone) return '未分区';
  if (zone === 'golden') return '黄金区';
  if (zone === 'normal') return '普通区';
  // 自定义区名若已带「区」则不再追加
  if (/区$/.test(zone)) return zone;
  return `${zone}区`;
}

export function zoneColor(zone: string): string {
  if (LEGACY_ZONE_COLOR[zone]) return LEGACY_ZONE_COLOR[zone];
  let h = 0;
  for (let i = 0; i < zone.length; i++) h = (h * 31 + zone.charCodeAt(i)) >>> 0;
  return ZONE_PALETTE[h % ZONE_PALETTE.length];
}

/** 座位图出现的区（去重排序） */
export function distinctZones(seats: Array<{ zone: string }>): string[] {
  return Array.from(new Set(seats.map((s) => s.zone).filter(Boolean))).sort();
}

export const DEFAULT_ZONE_PRESETS = ['A', 'B', 'C', 'D'];
