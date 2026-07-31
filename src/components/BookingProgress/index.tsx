import React from 'react';
import { useBookingStore } from '@/stores/booking';
import { completedStepFlags, progressFromDraft } from '@/utils/bookingProgress';
import styles from './BookingProgress.less';

const STEPS = ['选片', '影院', '场次', '选座', '支付'];

interface Props {
  /** 1–5；不传则按 BookingDraft 完备度推导 */
  step?: number;
}

const BookingProgress: React.FC<Props> = ({ step }) => {
  const draft = useBookingStore((s) => s.draft);
  const derived = progressFromDraft(draft);
  const currentIndex = step != null ? Math.max(0, step - 1) : derived.currentIndex;
  const doneFlags = completedStepFlags(
    draft || {
      movieId: undefined,
      cinemaId: undefined,
      showId: undefined,
      lockId: undefined,
      orderId: undefined,
      state: 'Idle',
    },
  );

  return (
    <div className={styles.bar} aria-label="购票进度">
      {STEPS.map((label, i) => {
        // 字段已完备 → done；当前焦点 → current；二者可并存时优先 current 样式
        const fieldDone = doneFlags[i] || i < currentIndex;
        const current = i === currentIndex && currentIndex < 5;
        const done = fieldDone && !current;
        return (
          <div
            key={label}
            className={`${styles.step} ${done ? styles.done : ''} ${current ? styles.current : ''}`}
          >
            <i>{String(i + 1).padStart(2, '0')}</i>
            <span>{label}</span>
          </div>
        );
      })}
    </div>
  );
};

export default BookingProgress;
