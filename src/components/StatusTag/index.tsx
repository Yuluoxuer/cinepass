import { Tag } from 'antd';
import type { ReactNode } from 'react';

/**
 * 入转调离模块通用状态 Tag 组件
 * 传入 status（英文编码）+ module（模块名），自动渲染对应颜色
 */

export type StatusModule = 'onboarding' | 'probation' | 'transfer' | 'resignation';

interface StatusTagProps {
  /** 后端返回的状态编码（英文小写） */
  status: string;
  /** 所属模块 */
  module: StatusModule;
  /** 自定义文案，不传则用内置映射 */
  label?: string;
  /** Tag 额外样式 */
  style?: React.CSSProperties;
}

// ============ 内置状态映射 ============

const STATUS_MAP: Record<StatusModule, Record<string, { label: string; color: string }>> = {
  onboarding: {
    draft: { label: '草稿', color: '#9CA3AF' },
    pending_approval: { label: '审批中', color: '#4C81E8' },
    approved_pending_entry: { label: '待确认到岗', color: '#52C41A' },
    onboarded: { label: '已入职', color: '#1B7340' },
    rejected: { label: '已驳回', color: '#FF4D4F' },
    withdrawn: { label: '已撤回', color: '#FA8C16' },
    abandoned: { label: '已放弃', color: '#FF4D4F' },
  },
  probation: {
    pending_initiate: { label: '待发起', color: '#9CA3AF' },
    pending_approval: { label: '审批中', color: '#4C81E8' },
    waiting_finalize: { label: '待 HR 确认', color: '#FA8C16' },
    approved: { label: '已转正', color: '#52C41A' },
    extended: { label: '已延期', color: '#FA8C16' },
    rejected: { label: '已驳回', color: '#FF4D4F' },
    withdrawn: { label: '已撤回', color: '#8C8C8C' },
  },
  transfer: {
    pending_approval: { label: '审批中', color: '#4C81E8' },
    approved_pending_transfer: { label: '待生效', color: '#FA8C16' },
    transferred: { label: '已调岗', color: '#52C41A' },
    rejected: { label: '已驳回', color: '#FF4D4F' },
    withdrawn: { label: '已撤回', color: '#8C8C8C' },
  },
  resignation: {
    pending_approval: { label: '审批中', color: '#4C81E8' },
    pending_exit: { label: '待离职', color: '#FA8C16' },
    left: { label: '已离职', color: '#8C8C8C' },
    rejected: { label: '已驳回', color: '#FF4D4F' },
    withdrawn: { label: '已撤回', color: '#8C8C8C' },
  },
};

// ============ 组件 ============

export default function StatusTag({ status, module, label, style }: StatusTagProps): ReactNode {
  const meta = STATUS_MAP[module]?.[status];
  if (!meta && !label) {
    return <Tag>{status}</Tag>;
  }
  return (
    <Tag color={meta?.color} style={style}>
      {label ?? meta?.label ?? status}
    </Tag>
  );
}
