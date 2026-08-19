import React from 'react';
import { InboxOutlined, CloseCircleOutlined, UserOutlined } from '@ant-design/icons';
import styles from './StateView.less';

export interface StateViewProps {
  /** 空数据 / 加载失败 / 需要登录 */
  variant: 'empty' | 'error' | 'login';
  /** 主标题 */
  title: string;
  /** 说明文案（可选） */
  description?: string;
  /** 操作按钮文字（可选，不传则不显示按钮） */
  actionLabel?: string;
  /** 操作按钮回调 */
  onAction?: () => void;
}

const ICONS = {
  empty: <InboxOutlined />,
  error: <CloseCircleOutlined />,
  login: <UserOutlined />,
} as const;

const StateView: React.FC<StateViewProps> = ({
  variant,
  title,
  description,
  actionLabel,
  onAction,
}) => (
  <div className={`${styles.wrap} miaoyu-fade-up`}>
    <span className={styles.icon}>{ICONS[variant]}</span>
    <h3 className={styles.title}>{title}</h3>
    {description ? <p className={styles.desc}>{description}</p> : null}
    {actionLabel && onAction ? (
      <button type="button" className="miaoyu-btn-primary" style={{ height: 36, padding: '0 18px' }} onClick={onAction}>
        {actionLabel}
      </button>
    ) : null}
  </div>
);

export default StateView;
