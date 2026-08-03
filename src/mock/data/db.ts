import type {
  BookingDraft,
  LockVO,
  OrderVO,
  SeatPriceSnapshot,
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

type AuthUser = UserVO & { password: string; phoneRaw?: string; status?: 'active' | 'disabled' };

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

function initSeatStatus(
  shows: ShowVO[],
  seatMaps: SeatMapVO[],
  halls: HallVO[],
): Map<string, Map<string, SeatStatus>> {
  const map = new Map<string, Map<string, SeatStatus>>();
  const hallById = new Map(halls.map((hall) => [hall.hallId, hall]));
  const seatMapById = new Map(seatMaps.map((seatMap) => [seatMap.seatMapId, seatMap]));
  for (const show of shows) {
    const hall = hallById.get(show.hallId);
    const sm = seatMapById.get(hall?.seatMapId || '') || seatMaps[0];
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

/**
 * 运营端订单列表的初始样本，覆盖待支付、已出票、已核销和已取消四种状态。
 * 订单始终从本次生成的真实场次中取值，避免种子数据与排片脱节。
 */
function buildSeedOrders(shows: ShowVO[], seatMaps: SeatMapVO[]): OrderVO[] {
  const movieById = new Map(MOCK_MOVIES.map((movie) => [movie.movieId, movie]));
  const cinemaById = new Map(MOCK_CINEMAS.map((cinema) => [cinema.cinemaId, cinema]));
  const seatMapById = new Map(seatMaps.map((seatMap) => [seatMap.seatMapId, seatMap]));
  const hallById = new Map(MOCK_HALLS.map((hall) => [hall.hallId, hall]));
  const scenarios = [
    { status: 'pending_pay', showIndex: 8, seatIndexes: [0, 1], createdOffset: 15 },
    { status: 'issued', showIndex: 17, seatIndexes: [2, 3], createdOffset: 90 },
    { status: 'used', showIndex: 29, seatIndexes: [4, 5], createdOffset: 180 },
    { status: 'cancelled', showIndex: 41, seatIndexes: [6, 7], createdOffset: 240 },
  ] as const;

  return scenarios.map((scenario, index) => {
    const show = shows[scenario.showIndex % shows.length];
    const movie = movieById.get(show.movieId)!;
    const cinema = cinemaById.get(show.cinemaId)!;
    const hall = hallById.get(show.hallId)!;
    const seatMap = seatMapById.get(hall.seatMapId)!;
    const priceByZone = new Map((show.zonePrices || []).map((item) => [item.zone, item.price]));
    const seats = scenario.seatIndexes.map((seatIndex) => seatMap.seats[seatIndex]);
    const seatPrices: SeatPriceSnapshot[] = seats.map((seat) => ({
      seatId: seat.seatId,
      seatName: seat.seatName,
      zone: seat.zone,
      price: priceByZone.get(seat.zone) ?? show.price,
    }));
    const amount = seatPrices.reduce((sum, item) => sum + item.price, 0);
    const createdAt = new Date(Date.now() - scenario.createdOffset * 60_000).toISOString();
    const isPaid = scenario.status === 'issued' || scenario.status === 'used';
    const ticketCode = isPaid ? `TKT-SEED-${String(index + 1).padStart(3, '0')}` : null;

    return {
      orderId: `o_seed_${index + 1}`,
      userId: 'u1',
      showId: show.showId,
      movieTitle: movie.title,
      cinemaName: cinema.name,
      hallName: show.hallName,
      startTime: show.startTime,
      seatIds: seats.map((seat) => seat.seatId),
      unitPrice: Math.round((amount / seatPrices.length) * 100) / 100,
      amount,
      seatPrices,
      status: scenario.status,
      ticketCode,
      qrPayload: ticketCode ? `QR:o_seed_${index + 1}:${ticketCode}` : null,
      payChannel: isPaid ? 'mock' : null,
      lockId: `lock_seed_${index + 1}`,
      expireAt:
        scenario.status === 'pending_pay' ? new Date(Date.now() + 10 * 60_000).toISOString() : null,
      createdAt,
      payAt: isPaid ? new Date(Date.now() - (scenario.createdOffset - 3) * 60_000).toISOString() : null,
      cancelReason: scenario.status === 'cancelled' ? '用户主动取消' : null,
      verifiedAt:
        scenario.status === 'used'
          ? new Date(Date.now() - (scenario.createdOffset - 5) * 60_000).toISOString()
          : null,
    };
  });
}

function createDb(): MockDb {
  const shows = buildMockShows();
  const seatMaps = clone(MOCK_SEAT_MAPS);
  const halls = clone(MOCK_HALLS);
  for (const hall of halls) {
    hall.showCount = shows.filter((show) => show.hallId === hall.hallId).length;
  }
  const orders = buildSeedOrders(shows, seatMaps);
  const seatStatus = initSeatStatus(shows, seatMaps, halls);
  for (const order of orders) {
    if (order.status === 'cancelled') continue;
    const status = order.status === 'pending_pay' ? 'locked' : 'sold';
    const showSeatStatus = seatStatus.get(order.showId);
    for (const seatId of order.seatIds) showSeatStatus?.set(seatId, status);
  }
  return {
    movies: clone(MOCK_MOVIES),
    cinemas: clone(MOCK_CINEMAS),
    halls,
    seatMaps,
    shows,
    users: clone(MOCK_USERS),
    drafts: new Map(),
    locks: new Map(),
    orders: new Map(orders.map((order) => [order.orderId, order])),
    seatStatus,
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
