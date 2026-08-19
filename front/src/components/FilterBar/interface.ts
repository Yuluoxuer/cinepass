import type { ReactNode } from 'react';

/** 单个筛选项配置 */
export interface FilterCell {
  label: string;
  children: ReactNode;
  /** 占据列数（默认 1） */
  span?: number;
}

/** FilterBar 公共属性 */
export interface FilterBarProps {
  fields: FilterCell[];
  /** 左侧功能按钮（如新增、导入导出等） */
  actions?: ReactNode;
  /** 点击查询回调 */
  onSearch?: () => void;
  /** 点击重置回调 */
  onReset?: () => void;
  /** 每行列数，默认 4 */
  columns?: 1 | 2 | 3 | 4;
}
