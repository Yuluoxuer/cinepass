import React from 'react';
import { useMockStore } from '@/stores/mock';
import styles from './MockToggle.less';

const MockToggle: React.FC = () => {
  const enabled = useMockStore((s) => s.enabled);
  const toggle = useMockStore((s) => s.toggle);

  return (
    <button
      type="button"
      className={`${styles.fab} ${enabled ? styles.on : styles.off}`}
      onClick={toggle}
      title={enabled ? '当前使用 Mock 数据，点击切换为真实后端' : '当前使用真实后端，点击切换为 Mock'}
    >
      Mock {enabled ? 'ON' : 'OFF'}
    </button>
  );
};

export default MockToggle;
