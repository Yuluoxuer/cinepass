import React from 'react';
import styles from './LoadingView.less';

interface LoadingViewProps {
  /** 可选提示文案 */
  text?: string;
  /** 垂直内边距，默认 64px */
  padding?: number;
}

const LoadingView: React.FC<LoadingViewProps> = ({ text, padding = 64 }) => (
  <div className={styles.wrap} style={{ paddingTop: padding, paddingBottom: padding }}>
    <span className={styles.spinner} aria-hidden />
    {text ? <p className={styles.text}>{text}</p> : null}
  </div>
);

export default LoadingView;
