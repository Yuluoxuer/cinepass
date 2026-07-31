import React from 'react';
import styles from './BookingProgress.less';

const STEPS = ['选片', '选影院', '选场次', '选座', '支付'];

interface Props {
  step: number; // 1-5
}

const BookingProgress: React.FC<Props> = ({ step }) => {
  return (
    <div className={styles.bar}>
      {STEPS.map((label, i) => {
        const n = i + 1;
        const done = n < step;
        const current = n === step;
        return (
          <React.Fragment key={label}>
            {i > 0 ? <div className={`${styles.line} ${done || current ? styles.lineActive : ''}`} /> : null}
            <div className={styles.item}>
              <div
                className={`${styles.dot} ${done ? styles.done : ''} ${current ? styles.current : ''}`}
              />
              <span className={current || done ? styles.labelActive : styles.label}>{label}</span>
            </div>
          </React.Fragment>
        );
      })}
    </div>
  );
};

export default BookingProgress;
