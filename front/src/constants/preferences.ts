import type { PreferRow, PreferSide } from '@/types';

/** 观影偏好：排位选项（front / middle / back） */
export const PREFER_ROW_OPTIONS: Array<{ value: PreferRow; label: string }> = [
  { value: 'front', label: '前排' },
  { value: 'middle', label: '中排' },
  { value: 'back', label: '后排' },
];

/** 观影偏好：侧向选项（center / aisle / edge） */
export const PREFER_SIDE_OPTIONS: Array<{ value: PreferSide; label: string }> = [
  { value: 'center', label: '中间' },
  { value: 'aisle', label: '靠过道' },
  { value: 'edge', label: '靠边' },
];
