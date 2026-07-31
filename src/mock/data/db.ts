import type {
  BookingDraft,
  LockVO,
  OrderVO,
  SeatMapVO,
  SeatStatus,
  ShowVO,
} from '@/types';
import {
  MOCK_CINEMAS,
  MOCK_HALLS,
  MOCK_MOVIES,
  MOCK_SEAT_MAPS,
  MOCK_USERS,
  buildMockShows,
} from './seed';
import type { CinemaVO, HallVO, MovieVO, UserVO } from '@/types';

type AuthUser = UserVO & { password: string; phoneRaw?: string };

interface MockDb {
  movies: MovieVO[];
  cinemas: CinemaVO[];
  halls: HallVO[];
  seatMaps: SeatMapVO[];
  shows: ShowVO[];
  users: AuthUser[];
  drafts: Map<string, BookingDraft>;
  locks: Map<string, LockVO>;
  orders: Map<string, OrderVO>;
  /** showId -> seatId -> status */
  seatStatus: Map<string, Map<string, SeatStatus>>;
  wantSee: Map<string, Set<string>>;
  /** orderId -> payToken */
  payTokens: Map<string, string>;
  currentUserId: string | null;
  tokens: Map<string, string>; // token -> userId
}

function clone<T>(v: T): T {
  return JSON.parse(JSON.stringify(v));
}

function initSeatStatus(shows: ShowVO[], seatMaps: SeatMapVO[]): Map<string, Map<string, SeatStatus>> {
  const map = new Map<string, Map<string, SeatStatus>>();
  const sm = seatMaps[0];
  for (const show of shows) {
    const seats = new Map<string, SeatStatus>();
    for (const s of sm.seats) {
      let status: SeatStatus = s.defaultStatus === 'unavailable' ? 'unavailable' : 'available';
      // seed a few sold seats
      if (s.graphRow === 2 && s.graphCol === 5) status = 'sold';
      if (s.graphRow === 3 && s.graphCol === 6) status = 'sold';
      seats.set(s.seatId, status);
    }
    map.set(show.showId, seats);
  }
  return map;
}

function createDb(): MockDb {
  const shows = buildMockShows();
  const seatMaps = clone(MOCK_SEAT_MAPS);
  return {
    movies: clone(MOCK_MOVIES),
    cinemas: clone(MOCK_CINEMAS),
    halls: clone(MOCK_HALLS),
    seatMaps,
    shows,
    users: clone(MOCK_USERS),
    drafts: new Map(),
    locks: new Map(),
    orders: new Map(),
    seatStatus: initSeatStatus(shows, seatMaps),
    wantSee: new Map(),
    payTokens: new Map(),
    currentUserId: null,
    tokens: new Map(),
  };
}

let db = createDb();

export function getDb() {
  return db;
}

export function resetDb() {
  db = createDb();
  return db;
}

export function uid(prefix: string) {
  return `${prefix}_${Date.now().toString(36)}_${Math.random().toString(36).slice(2, 7)}`;
}

export function nowIso() {
  return new Date().toISOString();
}

export function plusMinutes(min: number) {
  return new Date(Date.now() + min * 60_000).toISOString();
}

export function ok<T>(data: T) {
  return { code: 200 as const, message: 'success', data, traceId: uid('tr') };
}

export function fail(message: string, errorCode: string, extra?: Record<string, unknown>) {
  return {
    code: -1 as const,
    message,
    data: { errorCode, ...extra },
    traceId: uid('tr'),
  };
}
