import React from 'react';
import type { SeatVO, SeatStatus } from '@/types';
import styles from './SeatMap.less';

export interface SeatMapProps {
  rows: number;
  cols: number;
  screenLabel?: string;
  seats: SeatVO[];
  selectedIds: string[];
  onToggle?: (seat: SeatVO) => void;
  readonly?: boolean;
  scale?: number;
}

function statusClass(status?: SeatStatus, selected?: boolean, zone?: string, type?: string) {
  if (selected) return styles.selected;
  if (status === 'sold') return styles.sold;
  if (status === 'locked') return styles.locked;
  if (status === 'unavailable') return styles.unavailable;
  if (zone === 'golden') return styles.golden;
  if (type === 'couple') return styles.couple;
  return styles.available;
}

const SeatMap: React.FC<SeatMapProps> = ({
  rows,
  cols,
  screenLabel = '银幕',
  seats,
  selectedIds,
  onToggle,
  readonly,
  scale = 1,
}) => {
  const byPos = new Map(seats.map((s) => [`${s.graphRow}:${s.graphCol}`, s]));
  const selected = new Set(selectedIds);

  return (
    <div className={styles.wrap} style={{ transform: `scale(${scale})`, transformOrigin: 'top center' }}>
      <div className={styles.screen}>{screenLabel}</div>
      <div
        className={styles.grid}
        style={{
          gridTemplateColumns: `repeat(${cols}, 28px)`,
          gridTemplateRows: `repeat(${rows}, 28px)`,
        }}
      >
        {Array.from({ length: rows }, (_, ri) =>
          Array.from({ length: cols }, (_, ci) => {
            const seat = byPos.get(`${ri + 1}:${ci + 1}`);
            if (!seat) {
              return <div key={`${ri}-${ci}`} className={styles.empty} />;
            }
            const isSel = selected.has(seat.seatId);
            const clickable =
              !readonly &&
              onToggle &&
              (seat.status === 'available' || seat.status === undefined || isSel);
            return (
              <button
                key={seat.seatId}
                type="button"
                title={seat.seatName}
                disabled={!clickable}
                className={`${styles.cell} ${statusClass(seat.status, isSel, seat.zone, seat.type)}`}
                onClick={() => clickable && onToggle?.(seat)}
              >
                {seat.type === 'couple' ? '♥' : isSel ? '✓' : ''}
              </button>
            );
          }),
        )}
      </div>
      <div className={styles.legend}>
        <span><i className={styles.available} />可选</span>
        <span><i className={styles.golden} />黄金</span>
        <span><i className={styles.couple} />情侣</span>
        <span><i className={styles.selected} />已选</span>
        <span><i className={styles.sold} />已售</span>
        <span><i className={styles.locked} />锁定</span>
      </div>
    </div>
  );
};

export default SeatMap;
