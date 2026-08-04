import React from 'react';
import styles from './BlankPlaceholder.less';

export type BlankVariant = 'poster' | 'card' | 'row' | 'block';

interface BlankPlaceholderProps {
  variant?: BlankVariant;
  /** 渲染块数量，默认 1 */
  count?: number;
  className?: string;
  style?: React.CSSProperties;
}

/**
 * 资源缺失 / 读接口失败时的基本形状占位（灰块，非内容文案）。
 */
const BlankPlaceholder: React.FC<BlankPlaceholderProps> = ({
  variant = 'block',
  count = 1,
  className,
  style,
}) => {
  const n = Math.max(1, count);
  const items = Array.from({ length: n }, (_, i) => i);
  const wrapClass = [
    styles.wrap,
    variant === 'poster' || variant === 'card' ? styles.grid : null,
    className,
  ]
    .filter(Boolean)
    .join(' ');

  return (
    <div className={wrapClass} style={style} aria-hidden>
      {items.map((i) => (
        <div key={i} className={`${styles.item} ${styles[variant]} miaoyu-skeleton`} />
      ))}
    </div>
  );
};

export default BlankPlaceholder;
