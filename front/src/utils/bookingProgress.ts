import type { AgentProgress, BookingDraft, BookingState } from '@/types';

export const BOOKING_PROGRESS_STEPS = ['选片', '影院', '场次', '选座', '支付'] as const;

/**
 * 按系分 §4 firstIncompleteStep：字段完备度决定当前焦点。
 * 片/院可互换；movieId ∧ cinemaId 齐备后才进入选场。
 */
export function firstIncompleteStep(
  draft: Pick<
    BookingDraft,
    'movieId' | 'cinemaId' | 'showId' | 'lockId' | 'orderId' | 'state' | 'seatIds'
  >,
): BookingState {
  if (!draft.movieId) return 'SelectMovie';
  if (!draft.cinemaId) return 'SelectCinema';
  if (!draft.showId) return 'SelectShow';
  if (!draft.lockId) return 'SelectSeat';
  if (!draft.orderId) {
    return draft.state === 'PayMock' ? 'PayMock' : 'ConfirmOrder';
  }
  if (draft.state === 'TicketIssued') return 'TicketIssued';
  return 'PayMock';
}

/** 五步轨：哪些步因字段已完备而打勾（可与 current 焦点交叉，如院→片） */
export function completedStepFlags(
  draft: Pick<BookingDraft, 'movieId' | 'cinemaId' | 'showId' | 'lockId' | 'orderId' | 'state'>,
): boolean[] {
  const paidOrIssued = draft.state === 'PayMock' || draft.state === 'TicketIssued';
  return [
    !!draft.movieId,
    !!draft.cinemaId,
    !!draft.showId,
    !!draft.lockId,
    draft.state === 'TicketIssued' || (!!draft.orderId && paidOrIssued),
  ];
}

const STATE_TO_INDEX: Record<BookingState, number> = {
  Idle: 0,
  SelectMovie: 0,
  SelectCinema: 1,
  SelectShow: 2,
  SelectSeat: 3,
  ConfirmOrder: 4,
  PayMock: 4,
  TicketIssued: 5,
};

/** 从 Draft 推导 Agent/页面共用的 progress（勿只信旧 state 字符串） */
export function progressFromDraft(draft: BookingDraft | null | undefined): AgentProgress {
  if (!draft) {
    return {
      steps: [...BOOKING_PROGRESS_STEPS],
      currentIndex: 0,
      state: 'Idle',
    };
  }
  const state = firstIncompleteStep(draft);
  return {
    steps: [...BOOKING_PROGRESS_STEPS],
    currentIndex: STATE_TO_INDEX[state] ?? 0,
    state,
  };
}

/** BookingProgress 组件用的 1–5 步；出票后仍显示 5 */
export function bookingStepNumber(draft: BookingDraft | null | undefined): number {
  const idx = progressFromDraft(draft).currentIndex;
  return Math.min(5, Math.max(1, idx + 1));
}
