import type { ApiEnvelope } from '@/types';
import { getDb, fail, ok, uid, nowIso, plusMinutes } from './data/db';
import type {
  BookingDraft,
  LockVO,
  OrderVO,
  SeatMapVO,
  SeatPlanVO,
  SeatRecoResult,
  SeatVO,
  ShowListResult,
  AgentTurnRequest,
  AgentTurnResponse,
  AgentCardVO,
  AgentProgress,
  BookingState,
  PageResult,
  MovieVO,
  CinemaVO,
  PayQrVO,
  PaySessionVO,
  TicketVerifyVO,
  LoginResult,
  UserVO,
  WeeklyHotResult,
  PersonalRecoResult,
  HallVO,
  AdminUserVO,
  AdminDashboardStatsVO,
  ZonePrice,
  SeatPriceSnapshot,
} from '@/types';
import { distinctZones, zoneLabel } from '@/utils/zone';

export interface MockRequest {
  method: string;
  path: string; // without /api/v1 prefix, e.g. /movies
  query: Record<string, string>;
  body?: unknown;
  headers: Record<string, string>;
}

function pageOf<T>(items: T[], page = 1, size = 20): PageResult<T> {
  const p = Math.max(1, page);
  const s = Math.min(50, Math.max(1, size));
  const start = (p - 1) * s;
  return { items: items.slice(start, start + s), page: p, size: s, total: items.length };
}

function authUser(req: MockRequest): UserVO | null {
  const db = getDb();
  const h = req.headers.Authorization || req.headers.authorization || '';
  const token = h.replace(/^Bearer\s+/i, '');
  if (!token) return null;
  const userId = db.tokens.get(token);
  if (!userId) return null;
  const u = db.users.find((x) => x.userId === userId);
  if (!u || u.status === 'disabled') return null;
  return {
    userId: u.userId,
    nickname: u.nickname,
    phone: u.phone,
    role: u.role,
    avatarUrl: u.avatarUrl,
  };
}

function requireAuth(req: MockRequest) {
  const u = authUser(req);
  if (!u) return { error: fail('未登录', 'UNAUTHORIZED') as ApiEnvelope<unknown>, user: null };
  return { error: null, user: u };
}

function requireStaff(req: MockRequest) {
  const { error, user } = requireAuth(req);
  if (error) return { error, user: null };
  if (user!.role !== 'staff' && user!.role !== 'admin') {
    return { error: fail('无权限', 'FORBIDDEN') as ApiEnvelope<unknown>, user: null };
  }
  return { error: null, user: user! };
}

function progressFromDraft(draft: BookingDraft): AgentProgress {
  const steps = ['选片', '影院', '场次', '选座', '支付'];
  // 与前端 utils/bookingProgress.firstIncompleteStep 对齐
  let state: BookingState = draft.state;
  if (!draft.movieId) state = 'SelectMovie';
  else if (!draft.cinemaId) state = 'SelectCinema';
  else if (!draft.showId) state = 'SelectShow';
  else if (!draft.lockId) state = 'SelectSeat';
  else if (!draft.orderId) state = draft.state === 'PayMock' ? 'PayMock' : 'ConfirmOrder';
  else if (draft.state === 'TicketIssued') state = 'TicketIssued';
  else state = 'PayMock';

  const map: Record<BookingState, number> = {
    Idle: 0,
    SelectMovie: 0,
    SelectCinema: 1,
    SelectShow: 2,
    SelectSeat: 3,
    ConfirmOrder: 4,
    PayMock: 4,
    TicketIssued: 5,
  };
  return { steps, currentIndex: map[state] ?? 0, state };
}

function progressFromState(state: BookingState): AgentProgress {
  return progressFromDraft({
    sessionId: '',
    source: 'manual',
    state,
    count: 1,
    seatIds: [],
    version: 0,
  });
}

function getOrCreateDraft(sessionId?: string, patch?: Partial<BookingDraft>): BookingDraft {
  const db = getDb();
  if (sessionId && db.drafts.has(sessionId)) {
    return db.drafts.get(sessionId)!;
  }
  const sid = sessionId || uid('sess');
  const draft: BookingDraft = {
    sessionId: sid,
    source: 'manual',
    state: 'Idle',
    count: 2,
    seatIds: [],
    version: 1,
    updatedAt: nowIso(),
    ...patch,
  };
  db.drafts.set(sid, draft);
  return draft;
}

function movieById(id?: string) {
  return getDb().movies.find((m) => m.movieId === id);
}

function cinemaById(id?: string) {
  return getDb().cinemas.find((c) => c.cinemaId === id);
}

function showById(id?: string) {
  return getDb().shows.find((s) => s.showId === id);
}

function hallById(id?: string) {
  return getDb().halls.find((h) => h.hallId === id);
}

function seatMapForShow(showId: string): SeatMapVO | null {
  const db = getDb();
  const show = showById(showId);
  if (!show) return null;
  const hall = hallById(show.hallId);
  const sm = db.seatMaps.find((x) => x.seatMapId === hall?.seatMapId) || db.seatMaps[0];
  const statusMap = db.seatStatus.get(showId);
  const zonePrices = show.zonePrices || [{ zone: 'C', price: show.price }];
  const priceByZone = new Map(zonePrices.map((z) => [z.zone, z.price]));
  const zones = distinctZones(sm.seats);
  const legend: Record<string, string> = {
    available: '可选',
    locked: '锁定',
    sold: '已售',
    unavailable: '不可选',
    couple: '情侣座',
  };
  for (const z of zones) legend[z] = zoneLabel(z);
  const seats = sm.seats.map((s) => ({
    ...s,
    status: statusMap?.get(s.seatId) || ('available' as const),
    price: priceByZone.get(s.zone) ?? show.price,
  }));
  return {
    ...sm,
    showId,
    price: show.price,
    zonePrices,
    zones,
    seats,
    legend,
  };
}

function resolveZonePrices(
  seatMap: SeatMapVO,
  bodyZonePrices?: ZonePrice[],
  fallbackPrice?: number,
): { zonePrices: ZonePrice[]; error?: string } {
  const mapZones = seatMap.zones?.length ? seatMap.zones : distinctZones(seatMap.seats);
  if (mapZones.length === 0) return { zonePrices: [], error: '座位图无分区' };
  if (!bodyZonePrices || bodyZonePrices.length === 0) {
    if (fallbackPrice != null && fallbackPrice > 0) {
      return { zonePrices: mapZones.map((z) => ({ zone: z, price: fallbackPrice })) };
    }
    return { zonePrices: [], error: '请提供 zonePrices' };
  }
  const bodyZones = bodyZonePrices.map((z) => z.zone).sort();
  const expected = [...mapZones].sort();
  if (bodyZones.length !== expected.length || bodyZones.some((z, i) => z !== expected[i])) {
    return { zonePrices: [], error: `区价须覆盖座位图全部区：${expected.join(',')}` };
  }
  if (bodyZonePrices.some((z) => !(z.price > 0))) {
    return { zonePrices: [], error: '区价须大于 0' };
  }
  return { zonePrices: bodyZonePrices };
}

function dateOfShow(show: { startTime: string }) {
  return show.startTime.slice(0, 10);
}

/** 同一影厅两场间预留清场、入场的运营缓冲时间。 */
const SHOW_BUFFER_MINUTES = 20;

function parseShowTime(value?: string) {
  const time = value ? new Date(value).getTime() : Number.NaN;
  return Number.isFinite(time) ? time : null;
}

function validateShowSchedule(
  input: { hallId?: string; startTime?: string; endTime?: string },
  excludeShowId?: string,
): string | null {
  if (!input.hallId || !hallById(input.hallId)) return '影厅不存在';
  const start = parseShowTime(input.startTime);
  const end = parseShowTime(input.endTime);
  if (start == null || end == null) return '场次时间格式无效';
  if (start <= Date.now()) return '不允许创建或修改为过去时间的场次';
  if (end <= start) return '散场时间必须晚于开场时间';
  const bufferMs = SHOW_BUFFER_MINUTES * 60 * 1000;
  const conflict = getDb().shows.find((show) => {
    if (show.showId === excludeShowId || show.hallId !== input.hallId || show.status === 'cancelled') {
      return false;
    }
    const otherStart = parseShowTime(show.startTime);
    const otherEnd = parseShowTime(show.endTime);
    return otherStart != null && otherEnd != null && start < otherEnd + bufferMs && end + bufferMs > otherStart;
  });
  return conflict
    ? `与场次 ${conflict.showId} 时间冲突，场次前后需预留 ${SHOW_BUFFER_MINUTES} 分钟缓冲`
    : null;
}

function releasePendingOrder(order: OrderVO, reason: string) {
  order.status = 'cancelled';
  order.cancelReason = reason;
  order.expireAt = null;
  const statusMap = getDb().seatStatus.get(order.showId);
  for (const id of order.seatIds) {
    if (statusMap?.get(id) === 'locked') statusMap.set(id, 'available');
  }
  // 运营侧关闭订单时，同步回退用户草稿，避免继续停留在失效支付页。
  for (const draft of getDb().drafts.values()) {
    if (draft.orderId !== order.orderId) continue;
    draft.seatIds = [];
    draft.state = 'SelectSeat';
    delete draft.lockId;
    delete draft.orderId;
    delete draft.expireAt;
    draft.version += 1;
    draft.updatedAt = nowIso();
  }
}

function handleAuth(req: MockRequest): ApiEnvelope<unknown> | null {
  const db = getDb();
  if (req.method === 'POST' && req.path === '/auth/login') {
    const body = (req.body || {}) as { account?: string; password?: string };
    const account = (body.account || '').trim();
    const password = body.password || '';
    if (!account || account.length > 64 || password.length < 8 || password.length > 64) {
      return fail('参数校验失败', 'VALIDATION_ERROR');
    }
    const isPhone = /^1[3-9]\d{9}$/.test(account);
    const user = db.users.find((u) =>
      isPhone ? u.phoneRaw === account : u.nickname === account,
    );
    if (!user) return fail('账号不存在', 'NOT_FOUND');
    if (user.status === 'disabled') return fail('账号已被停用', 'ACCOUNT_DISABLED');
    if (user.password !== password) return fail('密码错误', 'UNAUTHORIZED');
    const token = uid('tok');
    db.tokens.set(token, user.userId);
    db.currentUserId = user.userId;
    const data: LoginResult = {
      accessToken: token,
      tokenType: 'Bearer',
      expiresIn: 3600,
      userId: user.userId,
      nickname: user.nickname,
      phone: user.phone,
      role: user.role,
    };
    return ok(data);
  }
  if (req.method === 'POST' && req.path === '/auth/logout') {
    const h = req.headers.Authorization || req.headers.authorization || '';
    const token = h.replace(/^Bearer\s+/i, '');
    db.tokens.delete(token);
    db.currentUserId = null;
    return ok({ loggedOut: true });
  }
  if (req.method === 'GET' && req.path === '/auth/me') {
    const { error, user } = requireAuth(req);
    if (error) return error;
    return ok(user);
  }
  return null;
}

function handleMovies(req: MockRequest): ApiEnvelope<unknown> | null {
  const db = getDb();
  const wantSeeMatch = req.path.match(/^\/movies\/([^/]+)\/want-see$/);
  if (wantSeeMatch) {
    const movieId = wantSeeMatch[1];
    const { error, user } = requireAuth(req);
    if (error) return error;
    const set = db.wantSee.get(user!.userId) || new Set();
    const movie = movieById(movieId);
    if (!movie) return fail('影片不存在', 'NOT_FOUND');
    if (req.method === 'POST') {
      set.add(movieId);
      movie.wantSeeCount += 1;
      db.wantSee.set(user!.userId, set);
      return ok({ movieId, wanted: true });
    }
    if (req.method === 'DELETE') {
      if (set.has(movieId)) {
        set.delete(movieId);
        movie.wantSeeCount = Math.max(0, movie.wantSeeCount - 1);
      }
      db.wantSee.set(user!.userId, set);
      return ok({ movieId, wanted: false });
    }
  }

  if (req.method === 'GET' && req.path === '/me/want-see') {
    const { error, user } = requireAuth(req);
    if (error) return error;
    const ids = [...(db.wantSee.get(user!.userId) || [])];
    const items = ids.map((id) => movieById(id)).filter(Boolean) as MovieVO[];
    return ok(pageOf(items, Number(req.query.page) || 1, Number(req.query.size) || 20));
  }

  if (req.method === 'GET' && req.path === '/movies') {
    let list = [...db.movies];
    if (req.query.status) list = list.filter((m) => m.status === req.query.status);
    if (req.query.q) {
      const q = req.query.q.toLowerCase();
      list = list.filter((m) => m.title.toLowerCase().includes(q));
    }
    if (req.query.genre) list = list.filter((m) => m.genres.includes(req.query.genre));
    return ok(pageOf(list, Number(req.query.page) || 1, Number(req.query.size) || 20));
  }

  const detail = req.path.match(/^\/movies\/([^/]+)$/);
  if (req.method === 'GET' && detail) {
    const m = movieById(detail[1]);
    if (!m) return fail('影片不存在', 'NOT_FOUND');
    return ok(m);
  }

  if (req.method === 'POST' && req.path === '/admin/movies') {
    const { error } = requireStaff(req);
    if (error) return error;
    const body = req.body as Partial<MovieVO>;
    const movie: MovieVO = {
      movieId: uid('m'),
      title: body.title || '未命名',
      posterUrl: body.posterUrl || '',
      genres: body.genres || [],
      rating: body.rating ?? null,
      durationMin: body.durationMin || 120,
      releaseDate: body.releaseDate || nowIso().slice(0, 10),
      status: body.status || 'coming_soon',
      description: body.description || '',
      cast: body.cast || '',
      wantSeeCount: 0,
    };
    db.movies.unshift(movie);
    return ok(movie);
  }

  const adminMovie = req.path.match(/^\/admin\/movies\/([^/]+)$/);
  if (req.method === 'PUT' && adminMovie) {
    const { error } = requireStaff(req);
    if (error) return error;
    const m = movieById(adminMovie[1]);
    if (!m) return fail('影片不存在', 'NOT_FOUND');
    Object.assign(m, req.body);
    return ok(m);
  }

  return null;
}

function handleCinemas(req: MockRequest): ApiEnvelope<unknown> | null {
  const db = getDb();
  if (req.method === 'GET' && req.path === '/cinemas') {
    let list = [...db.cinemas];
    if (req.query.movieId) {
      const hasShow = new Set(
        db.shows.filter((s) => s.movieId === req.query.movieId).map((s) => s.cinemaId),
      );
      list = list.filter((c) => hasShow.has(c.cinemaId));
      list = list.map((c) => {
        const prices = db.shows
          .filter((s) => s.cinemaId === c.cinemaId && s.movieId === req.query.movieId)
          .map((s) => s.price);
        return { ...c, minPrice: prices.length ? Math.min(...prices) : c.minPrice };
      });
    }
    if (req.query.sort === 'distance') {
      list.sort((a, b) => (a.distanceMeters || 0) - (b.distanceMeters || 0));
    }
    return ok(pageOf(list, Number(req.query.page) || 1, Number(req.query.size) || 20));
  }
  const one = req.path.match(/^\/cinemas\/([^/]+)$/);
  if (req.method === 'GET' && one) {
    const c = cinemaById(one[1]);
    if (!c) return fail('影院不存在', 'NOT_FOUND');
    return ok(c);
  }
  if (req.method === 'POST' && req.path === '/admin/cinemas') {
    const { error } = requireStaff(req);
    if (error) return error;
    const body = req.body as Partial<CinemaVO>;
    const c: CinemaVO = {
      cinemaId: uid('c'),
      name: body.name || '新影院',
      address: body.address || '',
      cityId: body.cityId || 'city_sh',
      lat: body.lat,
      lng: body.lng,
      distanceMeters: null,
      minPrice: null,
    };
    db.cinemas.push(c);
    return ok(c);
  }
  const upd = req.path.match(/^\/admin\/cinemas\/([^/]+)$/);
  if (req.method === 'PUT' && upd) {
    const { error } = requireStaff(req);
    if (error) return error;
    const c = cinemaById(upd[1]);
    if (!c) return fail('影院不存在', 'NOT_FOUND');
    Object.assign(c, req.body);
    return ok(c);
  }
  return null;
}

function handleShows(req: MockRequest): ApiEnvelope<unknown> | null {
  const db = getDb();
  if (req.method === 'GET' && req.path === '/admin/dashboard/stats') {
    const { error } = requireStaff(req);
    if (error) return error;
    const { date } = req.query;
    if (!/^\d{4}-\d{2}-\d{2}$/.test(date || '')) {
      return fail('date 必须为 YYYY-MM-DD', 'VALIDATION_ERROR');
    }
    const orders = [...db.orders.values()];
    const data: AdminDashboardStatsVO = {
      date,
      totalOrderCount: orders.length,
      pendingPayOrderCount: orders.filter((order) => order.status === 'pending_pay').length,
      issuedOrderCount: orders.filter((order) => order.status === 'issued').length,
      onSaleShowCount: db.shows.filter(
        (show) => show.status === 'on_sale' && dateOfShow(show) === date,
      ).length,
    };
    return ok(data);
  }
  if (req.method === 'GET' && req.path === '/admin/shows') {
    const { error } = requireStaff(req);
    if (error) return error;
    const { cinemaId, movieId, date } = req.query;
    if (!cinemaId || !movieId || !date) return fail('cinemaId、movieId、date 必填', 'VALIDATION_ERROR');
    return ok({
      date,
      items: db.shows.filter(
        (show) => show.cinemaId === cinemaId && show.movieId === movieId && dateOfShow(show) === date,
      ),
    });
  }
  if (req.method === 'GET' && req.path === '/shows') {
    const { cinemaId, movieId, date } = req.query;
    if (!cinemaId || !movieId || !date) {
      return fail('cinemaId、movieId、date 必填', 'VALIDATION_ERROR');
    }
    const items = db.shows.filter(
      (s) =>
        s.cinemaId === cinemaId &&
        s.movieId === movieId &&
        dateOfShow(s) === date &&
        s.status === 'on_sale',
    );
    const data: ShowListResult = { date, items };
    return ok(data);
  }

  const seatMap = req.path.match(/^\/shows\/([^/]+)\/seat-map$/);
  if (req.method === 'GET' && seatMap) {
    const sm = seatMapForShow(seatMap[1]);
    if (!sm) return fail('场次不存在', 'NOT_FOUND');
    return ok(sm);
  }

  if (req.method === 'POST' && req.path === '/admin/shows') {
    const { error } = requireStaff(req);
    if (error) return error;
    const body = req.body as Partial<{
      movieId: string;
      cinemaId: string;
      hallId: string;
      startTime: string;
      endTime: string;
      price: number;
      zonePrices: ZonePrice[];
    }>;
    if (!body.movieId || !movieById(body.movieId) || !body.cinemaId || !cinemaById(body.cinemaId)) {
      return fail('影片或影院不存在', 'VALIDATION_ERROR');
    }
    const scheduleError = validateShowSchedule(body);
    if (scheduleError) return fail(scheduleError, 'SHOW_SCHEDULE_INVALID');
    const hall = hallById(body.hallId);
    if (hall?.cinemaId !== body.cinemaId) return fail('影厅不属于所选影院', 'VALIDATION_ERROR');
    const sm = db.seatMaps.find((x) => x.seatMapId === hall?.seatMapId) || db.seatMaps[0];
    const resolved = resolveZonePrices(sm, body.zonePrices, body.price);
    if (resolved.error) return fail(resolved.error, 'VALIDATION_ERROR');
    const minPrice = Math.min(...resolved.zonePrices.map((z) => z.price));
    const show = {
      showId: uid('s'),
      movieId: body.movieId!,
      cinemaId: body.cinemaId!,
      hallId: body.hallId!,
      hallName: hall?.name || '厅',
      startTime: body.startTime!,
      endTime: body.endTime!,
      price: minPrice,
      zonePrices: resolved.zonePrices,
      seatRemain: sm.seats.filter((s) => s.defaultStatus !== 'unavailable').length,
      seatRemainLevel: 'ample' as const,
      status: 'on_sale' as const,
    };
    db.shows.push(show);
    sm.mutable = false;
    const seats = new Map<string, 'available' | 'unavailable' | 'locked' | 'sold'>();
    for (const s of sm.seats) {
      seats.set(s.seatId, s.defaultStatus === 'unavailable' ? 'unavailable' : 'available');
    }
    db.seatStatus.set(show.showId, seats);
    return ok(show);
  }

  const adminShow = req.path.match(/^\/admin\/shows\/([^/]+)$/);
  if (req.method === 'PUT' && adminShow) {
    const { error } = requireStaff(req);
    if (error) return error;
    const s = showById(adminShow[1]);
    if (!s) return fail('场次不存在', 'NOT_FOUND');
    if (s.status !== 'on_sale') return fail('停售或已取消场次不可编辑', 'SHOW_NOT_ON_SALE');
    const body = (req.body || {}) as Partial<{
      startTime: string;
      endTime: string;
      price: number;
      zonePrices: ZonePrice[];
    }>;
    if (body.zonePrices) {
      const hall = hallById(s.hallId);
      const sm = db.seatMaps.find((x) => x.seatMapId === hall?.seatMapId) || db.seatMaps[0];
      const resolved = resolveZonePrices(sm, body.zonePrices);
      if (resolved.error) return fail(resolved.error, 'VALIDATION_ERROR');
      s.zonePrices = resolved.zonePrices;
      s.price = Math.min(...resolved.zonePrices.map((z) => z.price));
    }
    if (body.startTime || body.endTime) {
      const scheduleError = validateShowSchedule(
        {
          hallId: s.hallId,
          startTime: body.startTime || s.startTime,
          endTime: body.endTime || s.endTime,
        },
        s.showId,
      );
      if (scheduleError) return fail(scheduleError, 'SHOW_SCHEDULE_INVALID');
      if (body.startTime) s.startTime = body.startTime;
      if (body.endTime) s.endTime = body.endTime;
    }
    return ok(s);
  }
  const impact = req.path.match(/^\/admin\/shows\/([^/]+)\/impact$/);
  if (req.method === 'GET' && impact) {
    const { error } = requireStaff(req);
    if (error) return error;
    const show = showById(impact[1]);
    if (!show) return fail('场次不存在', 'NOT_FOUND');
    const orders = [...db.orders.values()].filter((order) => order.showId === show.showId);
    return ok({
      showId: show.showId,
      pendingPayCount: orders.filter((order) => order.status === 'pending_pay').length,
      issuedCount: orders.filter((order) => order.status === 'issued').length,
      usedCount: orders.filter((order) => order.status === 'used').length,
    });
  }
  const closeSale = req.path.match(/^\/admin\/shows\/([^/]+)\/close-sale$/);
  if (req.method === 'POST' && closeSale) {
    const { error } = requireStaff(req);
    if (error) return error;
    const s = showById(closeSale[1]);
    if (!s) return fail('场次不存在', 'NOT_FOUND');
    if (s.status !== 'on_sale') return fail('该场次当前不可停售', 'SHOW_NOT_ON_SALE');
    if ((parseShowTime(s.startTime) || 0) <= Date.now()) return fail('开场后不可停售', 'SHOW_STARTED');
    for (const order of db.orders.values()) {
      if (order.showId === s.showId && order.status === 'pending_pay') {
        releasePendingOrder(order, 'sale_closed');
      }
    }
    s.status = 'off_sale';
    return ok(s);
  }
  const cancel = req.path.match(/^\/admin\/shows\/([^/]+)\/cancel$/);
  if (req.method === 'POST' && cancel) {
    const { error } = requireStaff(req);
    if (error) return error;
    const s = showById(cancel[1]);
    if (!s) return fail('场次不存在', 'NOT_FOUND');
    if (s.status === 'cancelled') return fail('场次已取消', 'SHOW_ALREADY_CANCELLED');
    if ((parseShowTime(s.startTime) || 0) <= Date.now()) return fail('开场后不可取消', 'SHOW_STARTED');
    for (const order of db.orders.values()) {
      if (order.showId !== s.showId) continue;
      if (order.status === 'pending_pay') {
        releasePendingOrder(order, 'show_cancelled');
      } else if (order.status === 'issued') {
        order.status = 'cancelled';
        order.cancelReason = 'show_cancelled';
      }
    }
    s.status = 'cancelled';
    return ok(s);
  }
  return null;
}

function handleReco(req: MockRequest): ApiEnvelope<unknown> | null {
  const db = getDb();
  if (req.method === 'GET' && req.path === '/reco/weekly-hot') {
    const limit = Number(req.query.limit) || 10;
    const hot = db.movies
      .filter((m) => m.status === 'hot_showing')
      .sort((a, b) => b.wantSeeCount - a.wantSeeCount)
      .slice(0, limit);
    const data: WeeklyHotResult = {
      computedAt: nowIso(),
      items: hot.map((movie, i) => ({
        rank: i + 1,
        hotScore: 100 - i * 7,
        heatTag: i < 3 ? '本周爆款' : '热议',
        movie,
      })),
    };
    return ok(data);
  }
  if (req.method === 'GET' && req.path === '/reco/personal') {
    const user = authUser(req);
    const limit = Number(req.query.limit) || 10;
    if (!user) {
      const data: PersonalRecoResult = {
        mode: 'fallback_hot',
        items: db.movies
          .filter((m) => m.status === 'hot_showing')
          .slice(0, limit)
          .map((movie) => ({ movie, personalScore: 0.5, reason: '' })),
      };
      return ok(data);
    }
    const data: PersonalRecoResult = {
      mode: 'content',
      items: db.movies
        .filter((m) => m.status === 'hot_showing')
        .slice(0, limit)
        .map((movie, i) => ({
          movie,
          personalScore: 0.9 - i * 0.05,
          reason: i % 2 === 0 ? '因为你喜欢科幻' : '看过同类型影片',
        })),
    };
    return ok(data);
  }
  if (req.method === 'POST' && req.path === '/reco/seats') {
    const body = (req.body || {}) as {
      showId: string;
      count?: number;
      preferRow?: string;
      preferSide?: string;
      together?: boolean;
    };
    const sm = seatMapForShow(body.showId);
    if (!sm) return fail('场次不存在', 'NOT_FOUND');
    const count = Math.min(4, Math.max(1, body.count || 2));
    const available = sm.seats.filter((s) => s.status === 'available');
    const plans: SeatPlanVO[] = [];
    const mid = Math.floor(available.length / 2);
    for (let i = 0; i < 3; i++) {
      const start = Math.max(0, mid - count + i * 2);
      const slice = available.slice(start, start + count);
      if (slice.length < count) continue;
      plans.push({
        planId: uid('plan'),
        seatIds: slice.map((s) => s.seatId),
        score: 90 - i * 8,
        explain: i === 0 ? '黄金区居中连座' : i === 1 ? '靠前优选' : '性价比方案',
        seats: slice,
      });
    }
    const data: SeatRecoResult = {
      showId: body.showId,
      plans,
      compromise: plans.length
        ? null
        : { suggestion: '当前偏好下无理想连座，可换场次试试', altShowIds: [] },
    };
    return ok(data);
  }
  return null;
}

function handleDrafts(req: MockRequest): ApiEnvelope<unknown> | null {
  const db = getDb();
  if (req.method === 'POST' && req.path === '/booking-drafts') {
    const body = (req.body || {}) as Partial<BookingDraft>;
    const draft = getOrCreateDraft(undefined, {
      source: body.source || 'manual',
      movieId: body.movieId,
      state: body.movieId ? 'SelectCinema' : 'Idle',
      filmTitle: movieById(body.movieId)?.title,
    });
    return ok(draft);
  }
  const one = req.path.match(/^\/booking-drafts\/([^/]+)$/);
  if (!one) return null;
  const sid = one[1];
  if (req.method === 'GET') {
    const draft = getOrCreateDraft(sid);
    return ok(draft);
  }
  if (req.method === 'PUT') {
    const body = (req.body || {}) as { version: number; patch: Partial<BookingDraft> };
    let draft = db.drafts.get(sid);
    if (!draft) draft = getOrCreateDraft(sid);
    if (body.version !== draft.version) {
      return fail('Draft 版本冲突', 'DRAFT_CONFLICT', { serverDraft: draft });
    }
    const patch = body.patch || {};
    const next: BookingDraft = {
      ...draft,
      ...patch,
      sessionId: draft.sessionId,
      version: draft.version + 1,
      updatedAt: nowIso(),
      // 锁/单由服务端权威字段写；客户端 patch 不含则保留
      lockId: patch.lockId !== undefined ? patch.lockId : draft.lockId,
      orderId: patch.orderId !== undefined ? patch.orderId : draft.orderId,
      expireAt: patch.expireAt !== undefined ? patch.expireAt : draft.expireAt,
      userId: patch.userId !== undefined ? patch.userId : draft.userId,
    };
    if (patch.movieId) next.filmTitle = movieById(patch.movieId)?.title || next.filmTitle;
    // 回退清空：改片清院及以下；改院清场及以下
    if (patch.movieId && patch.movieId !== draft.movieId) {
      next.showId = undefined;
      next.seatIds = [];
      next.lockId = undefined;
      next.orderId = undefined;
      next.expireAt = undefined;
      if (patch.cinemaId === undefined) {
        next.cinemaId = undefined;
      }
    } else if (patch.cinemaId && patch.cinemaId !== draft.cinemaId) {
      next.showId = undefined;
      next.seatIds = [];
      next.lockId = undefined;
      next.orderId = undefined;
      next.expireAt = undefined;
    }
    // state 以字段完备度为准（保护已选步骤）
    next.state = progressFromDraft(next).state;
    db.drafts.set(sid, next);
    return ok(next);
  }
  return null;
}

function handleLocksOrders(req: MockRequest): ApiEnvelope<unknown> | null {
  const db = getDb();

  if (req.method === 'POST' && req.path === '/locks') {
    const { error, user } = requireAuth(req);
    if (error) return error;
    const body = (req.body || {}) as { showId: string; seatIds: string[]; sessionId?: string };
    const show = showById(body.showId);
    if (!show) return fail('场次不存在', 'NOT_FOUND');
    if (show.status !== 'on_sale' || (parseShowTime(show.startTime) || 0) <= Date.now()) {
      return fail('该场次已停售或不可购买', 'SHOW_NOT_ON_SALE');
    }
    const statusMap = db.seatStatus.get(body.showId);
    if (!statusMap) return fail('场次不存在', 'NOT_FOUND');
    for (const id of body.seatIds) {
      if (statusMap.get(id) !== 'available') {
        return fail('座位已被占用', 'SEAT_TAKEN');
      }
    }
    // couple rule
    const sm = seatMapForShow(body.showId);
    for (const id of body.seatIds) {
      const seat = sm?.seats.find((s) => s.seatId === id);
      if (seat?.type === 'couple' && seat.couplePairId) {
        const pair = sm!.seats.find(
          (s) => s.couplePairId === seat.couplePairId && s.seatId !== id,
        );
        if (pair && !body.seatIds.includes(pair.seatId)) {
          return fail('情侣座须成对选择', 'COUPLE_RULE');
        }
      }
    }
    for (const id of body.seatIds) statusMap.set(id, 'locked');
    const lock: LockVO = {
      lockId: uid('lock'),
      showId: body.showId,
      seatIds: body.seatIds,
      userId: user!.userId,
      expireAt: plusMinutes(15),
      ttlSeconds: 900,
      status: 'active',
    };
    db.locks.set(lock.lockId, lock);
    if (body.sessionId) {
      const draft = getOrCreateDraft(body.sessionId);
      draft.lockId = lock.lockId;
      draft.seatIds = body.seatIds;
      draft.showId = body.showId;
      draft.expireAt = lock.expireAt;
      draft.state = 'ConfirmOrder';
      draft.version += 1;
      draft.userId = user!.userId;
      db.drafts.set(body.sessionId, draft);
    }
    return ok(lock);
  }

  const delLock = req.path.match(/^\/locks\/([^/]+)$/);
  if (req.method === 'DELETE' && delLock) {
    const lock = db.locks.get(delLock[1]);
    if (!lock) return fail('锁不存在', 'NOT_FOUND');
    const statusMap = db.seatStatus.get(lock.showId);
    for (const id of lock.seatIds) {
      if (statusMap?.get(id) === 'locked') statusMap.set(id, 'available');
    }
    lock.status = 'released';
    const sid = req.query.sessionId;
    if (sid && db.drafts.has(sid)) {
      const d = db.drafts.get(sid)!;
      delete d.lockId;
      d.seatIds = [];
      d.version += 1;
    }
    return ok({ released: true });
  }

  if (req.method === 'POST' && req.path === '/orders') {
    const { error, user } = requireAuth(req);
    if (error) return error;
    const body = (req.body || {}) as { lockId: string; sessionId?: string };
    const lock = db.locks.get(body.lockId);
    if (!lock || lock.status !== 'active') return fail('锁无效或已过期', 'LOCK_EXPIRED');
    if (lock.userId !== user!.userId) return fail('无权限', 'FORBIDDEN');
    const show = showById(lock.showId)!;
    if (show.status !== 'on_sale' || (parseShowTime(show.startTime) || 0) <= Date.now()) {
      return fail('该场次已停售或不可购买', 'SHOW_NOT_ON_SALE');
    }
    const movie = movieById(show.movieId)!;
    const cinema = cinemaById(show.cinemaId)!;
    const sm = seatMapForShow(show.showId);
    const seatById = new Map(sm?.seats.map((x) => [x.seatId, x]) || []);
    const zonePrices = show.zonePrices || [{ zone: 'C', price: show.price }];
    const priceByZone = new Map(zonePrices.map((z) => [z.zone, z.price]));
    const seatPrices: SeatPriceSnapshot[] = lock.seatIds.map((sid) => {
      const seat = seatById.get(sid);
      const zone = seat?.zone || 'C';
      const price = priceByZone.get(zone) ?? show.price;
      return { seatId: sid, zone, price, seatName: seat?.seatName };
    });
    const amount = seatPrices.reduce((sum, p) => sum + p.price, 0);
    const unitPrice =
      seatPrices.length > 0 ? Math.round((amount / seatPrices.length) * 100) / 100 : show.price;
    const order: OrderVO = {
      orderId: uid('o'),
      userId: user!.userId,
      showId: show.showId,
      movieTitle: movie.title,
      cinemaName: cinema.name,
      hallName: show.hallName,
      startTime: show.startTime,
      seatIds: lock.seatIds,
      unitPrice,
      amount,
      seatPrices,
      status: 'pending_pay',
      ticketCode: null,
      qrPayload: null,
      payChannel: null,
      lockId: lock.lockId,
      expireAt: lock.expireAt,
      createdAt: nowIso(),
      payAt: null,
    };
    db.orders.set(order.orderId, order);
    lock.status = 'consumed';
    if (body.sessionId) {
      const draft = getOrCreateDraft(body.sessionId);
      draft.orderId = order.orderId;
      draft.state = 'PayMock';
      draft.version += 1;
      db.drafts.set(body.sessionId, draft);
    }
    return ok(order);
  }

  if (req.method === 'GET' && req.path === '/orders') {
    const { error, user } = requireAuth(req);
    if (error) return error;
    let list = [...db.orders.values()].filter((o) => o.userId === user!.userId);
    if (req.query.status) list = list.filter((o) => o.status === req.query.status);
    list.sort((a, b) => b.createdAt.localeCompare(a.createdAt));
    return ok(pageOf(list, Number(req.query.page) || 1, Number(req.query.size) || 20));
  }

  if (req.method === 'GET' && req.path === '/admin/orders') {
    const { error } = requireStaff(req);
    if (error) return error;
    let list = [...db.orders.values()];
    if (req.query.status) list = list.filter((o) => o.status === req.query.status);
    if (req.query.orderId) list = list.filter((o) => o.orderId.includes(req.query.orderId));
    if (req.query.userId) list = list.filter((o) => o.userId === req.query.userId);
    return ok(pageOf(list, Number(req.query.page) || 1, Number(req.query.size) || 20));
  }

  const adminCancel = req.path.match(/^\/admin\/orders\/([^/]+)\/cancel$/);
  if (req.method === 'POST' && adminCancel) {
    const { error } = requireStaff(req);
    if (error) return error;
    const order = db.orders.get(adminCancel[1]);
    if (!order) return fail('订单不存在', 'NOT_FOUND');
    if (order.status !== 'pending_pay') return fail('仅待支付订单可关闭', 'ORDER_NOT_CLOSABLE');
    const body = (req.body || {}) as { reason?: string };
    releasePendingOrder(order, body.reason || 'admin_closed');
    return ok(order);
  }

  const adminConsume = req.path.match(/^\/admin\/orders\/([^/]+)\/consume$/);
  if (req.method === 'POST' && adminConsume) {
    const { error } = requireStaff(req);
    if (error) return error;
    const order = db.orders.get(adminConsume[1]);
    if (!order) return fail('订单不存在', 'NOT_FOUND');
    const show = showById(order.showId);
    if (!show || show.status === 'cancelled') return fail('场次已取消，票券不可核销', 'SHOW_CANCELLED');
    if (order.status !== 'issued') return fail('仅已出票订单可核销', 'TICKET_NOT_USABLE');
    order.status = 'used';
    order.verifiedAt = nowIso();
    return ok(order);
  }

  const orderOne = req.path.match(/^\/orders\/([^/]+)$/);
  if (req.method === 'GET' && orderOne && !req.path.includes('pay')) {
    const order = db.orders.get(orderOne[1]);
    if (!order) return fail('订单不存在', 'NOT_FOUND');
    return ok(order);
  }

  const payQr = req.path.match(/^\/orders\/([^/]+)\/pay-qrcode$/);
  if (req.method === 'GET' && payQr) {
    const order = db.orders.get(payQr[1]);
    if (!order) return fail('订单不存在', 'NOT_FOUND');
    if (order.status !== 'pending_pay') return fail('订单状态不可支付', 'CONFLICT');
    const token = uid('pay');
    db.payTokens.set(order.orderId, token);
    const origin = typeof window !== 'undefined' ? window.location.origin : 'http://localhost:8000';
    const data: PayQrVO = {
      orderId: order.orderId,
      amount: order.amount,
      expireAt: order.expireAt || plusMinutes(15),
      payUrl: `${origin}/m/pay/${order.orderId}?t=${token}`,
      pollIntervalMs: 2000,
      payToken: token,
    };
    return ok(data);
  }

  const paySession = req.path.match(/^\/orders\/([^/]+)\/pay-session$/);
  if (req.method === 'GET' && paySession) {
    const order = db.orders.get(paySession[1]);
    if (!order) return fail('订单不存在', 'NOT_FOUND');
    const t = req.query.t;
    if (!t || db.payTokens.get(order.orderId) !== t) {
      return fail('支付链接无效', 'PAY_TOKEN_INVALID');
    }
    if (order.expireAt && new Date(order.expireAt).getTime() < Date.now()) {
      return fail('锁座已过期', 'LOCK_EXPIRED');
    }
    const data: PaySessionVO = {
      orderId: order.orderId,
      amount: order.amount,
      expireAt: order.expireAt || plusMinutes(15),
      movieTitle: order.movieTitle,
      cinemaName: order.cinemaName,
      hallName: order.hallName,
      startTime: order.startTime,
      seatIds: order.seatIds,
      status: order.status,
    };
    return ok(data);
  }

  const pay = req.path.match(/^\/orders\/([^/]+)\/pay$/);
  if (req.method === 'POST' && pay) {
    const order = db.orders.get(pay[1]);
    if (!order) return fail('订单不存在', 'NOT_FOUND');
    if (order.status === 'issued') return ok(order);
    if (order.status !== 'pending_pay') return fail('订单不可支付', 'CONFLICT');
    if (order.expireAt && new Date(order.expireAt).getTime() < Date.now()) {
      return fail('锁座已过期', 'LOCK_EXPIRED');
    }
    const body = (req.body || {}) as { channel?: string; sessionId?: string };
    const payToken = req.headers['X-Pay-Token'] || req.headers['x-pay-token'];
    if (body.channel === 'mobile_qr') {
      if (!payToken || db.payTokens.get(order.orderId) !== payToken) {
        return fail('支付 Token 无效', 'PAY_TOKEN_INVALID');
      }
    } else {
      const { error } = requireAuth(req);
      if (error) return error;
    }
    const statusMap = db.seatStatus.get(order.showId);
    const show = showById(order.showId);
    if (!show || show.status !== 'on_sale' || (parseShowTime(show.startTime) || 0) <= Date.now()) {
      return fail('场次已停售或不可支付', 'SHOW_NOT_ON_SALE');
    }
    for (const id of order.seatIds) statusMap?.set(id, 'sold');
    order.status = 'issued';
    order.ticketCode = `TKT-${Date.now().toString().slice(-8)}`;
    order.qrPayload = `QR:${order.orderId}:${order.ticketCode}`;
    order.payChannel = body.channel || 'desktop_button';
    order.payAt = nowIso();
    order.expireAt = null;
    if (body.sessionId && db.drafts.has(body.sessionId)) {
      const d = db.drafts.get(body.sessionId)!;
      d.state = 'TicketIssued';
      d.orderId = order.orderId;
      d.version += 1;
    }
    return ok(order);
  }

  const cancel = req.path.match(/^\/orders\/([^/]+)\/cancel$/);
  if (req.method === 'POST' && cancel) {
    const { error, user } = requireAuth(req);
    if (error) return error;
    const order = db.orders.get(cancel[1]);
    if (!order) return fail('订单不存在', 'NOT_FOUND');
    if (order.userId !== user!.userId && user!.role === 'user') {
      return fail('无权限', 'FORBIDDEN');
    }
    if (order.status !== 'pending_pay') return fail('无法取消', 'CONFLICT');
    order.status = 'cancelled';
    order.cancelReason = 'user_cancelled';
    const statusMap = db.seatStatus.get(order.showId);
    for (const id of order.seatIds) {
      if (statusMap?.get(id) === 'locked') statusMap.set(id, 'available');
    }
    const sessionId = req.headers['X-Session-Id'];
    const draft = sessionId ? db.drafts.get(sessionId) : undefined;
    if (draft?.orderId === order.orderId) {
      draft.seatIds = [];
      draft.state = 'SelectSeat';
      delete draft.lockId;
      delete draft.orderId;
      delete draft.expireAt;
      draft.version += 1;
      draft.updatedAt = nowIso();
    }
    return ok(order);
  }

  return null;
}

function handleSeatMapsHalls(req: MockRequest): ApiEnvelope<unknown> | null {
  const db = getDb();
  if (req.method === 'GET' && req.path === '/seat-maps') {
    return ok(pageOf(db.seatMaps, 1, 50));
  }
  const one = req.path.match(/^\/seat-maps\/([^/]+)$/);
  if (req.method === 'GET' && one) {
    const sm = db.seatMaps.find((x) => x.seatMapId === one[1]);
    if (!sm) return fail('座位图不存在', 'NOT_FOUND');
    return ok({
      ...sm,
      zones: sm.zones?.length ? sm.zones : distinctZones(sm.seats),
    });
  }
  const usage = req.path.match(/^\/seat-maps\/([^/]+)\/usage$/);
  if (req.method === 'GET' && usage) {
    const { error } = requireStaff(req);
    if (error) return error;
    const sm = db.seatMaps.find((x) => x.seatMapId === usage[1]);
    if (!sm) return fail('座位图不存在', 'NOT_FOUND');
    const halls = db.halls.filter((hall) => hall.seatMapId === sm.seatMapId);
    const hallIds = new Set(halls.map((hall) => hall.hallId));
    const shows = db.shows.filter((show) => hallIds.has(show.hallId));
    return ok({
      seatMapId: sm.seatMapId,
      hallCount: halls.length,
      showCount: shows.length,
      hallNames: halls.map((hall) => hall.name),
    });
  }
  if (req.method === 'POST' && req.path === '/seat-maps') {
    const { error } = requireStaff(req);
    if (error) return error;
    const body = req.body as SeatMapVO;
    const seats = body.seats || [];
    const sm: SeatMapVO = {
      seatMapId: body.seatMapId || uid('sm'),
      rows: body.rows,
      cols: body.cols,
      screenLabel: body.screenLabel || '银幕',
      seats,
      zones: body.zones?.length ? body.zones : distinctZones(seats),
      mutable: true,
    };
    db.seatMaps.push(sm);
    return ok(sm);
  }
  if (req.method === 'PUT' && one) {
    const { error } = requireStaff(req);
    if (error) return error;
    const idx = db.seatMaps.findIndex((x) => x.seatMapId === one[1]);
    if (idx < 0) return fail('座位图不存在', 'NOT_FOUND');
    if (db.seatMaps[idx].mutable === false) return fail('座位图不可修改', 'CONFLICT');
    const body = req.body as SeatMapVO;
    const seats = body.seats ?? db.seatMaps[idx].seats;
    db.seatMaps[idx] = {
      ...db.seatMaps[idx],
      ...body,
      seatMapId: one[1],
      seats,
      zones: body.zones?.length ? body.zones : distinctZones(seats),
    };
    return ok(db.seatMaps[idx]);
  }
  if (req.method === 'DELETE' && one) {
    const { error } = requireStaff(req);
    if (error) return error;
    const sm = db.seatMaps.find((x) => x.seatMapId === one[1]);
    if (!sm) return fail('座位图不存在', 'NOT_FOUND');
    const halls = db.halls.filter((hall) => hall.seatMapId === sm.seatMapId);
    const hallIds = new Set(halls.map((hall) => hall.hallId));
    const shows = db.shows.filter((show) => hallIds.has(show.hallId));
    if (halls.length || shows.length) {
      return fail('座位图仍被影厅或场次引用，无法删除', 'SEAT_MAP_IN_USE', {
        hallCount: halls.length,
        showCount: shows.length,
        hallNames: halls.map((hall) => hall.name),
      });
    }
    db.seatMaps = db.seatMaps.filter((x) => x.seatMapId !== sm.seatMapId);
    return ok({ deleted: true });
  }

  if (req.method === 'GET' && req.path === '/admin/halls') {
    const { error } = requireStaff(req);
    if (error) return error;
    let list = [...db.halls];
    if (req.query.cinemaId) list = list.filter((h) => h.cinemaId === req.query.cinemaId);
    return ok(pageOf(list, 1, 50));
  }
  if (req.method === 'POST' && req.path === '/halls') {
    const { error } = requireStaff(req);
    if (error) return error;
    const body = req.body as Partial<HallVO>;
    if (!body.cinemaId || !cinemaById(body.cinemaId)) return fail('影院不存在', 'NOT_FOUND');
    if (!body.seatMapId || !db.seatMaps.some((map) => map.seatMapId === body.seatMapId)) {
      return fail('座位图不存在', 'NOT_FOUND');
    }
    const hall: HallVO = {
      hallId: uid('h'),
      cinemaId: body.cinemaId!,
      name: body.name || '新厅',
      seatMapId: body.seatMapId!,
      showCount: 0,
    };
    db.halls.push(hall);
    return ok(hall);
  }
  const hallUpd = req.path.match(/^\/admin\/halls\/([^/]+)$/);
  if (req.method === 'PUT' && hallUpd) {
    const { error } = requireStaff(req);
    if (error) return error;
    const h = hallById(hallUpd[1]);
    if (!h) return fail('影厅不存在', 'NOT_FOUND');
    const body = (req.body || {}) as Partial<HallVO>;
    if (body.seatMapId && !db.seatMaps.some((map) => map.seatMapId === body.seatMapId)) {
      return fail('座位图不存在', 'NOT_FOUND');
    }
    Object.assign(h, body);
    return ok(h);
  }
  return null;
}

function handleAdminUsers(req: MockRequest): ApiEnvelope<unknown> | null {
  const db = getDb();
  if (req.method === 'GET' && req.path === '/admin/users') {
    const { error, user } = requireStaff(req);
    if (error) return error;
    if (user!.role !== 'admin') return fail('仅管理员可访问', 'FORBIDDEN');
    const items: AdminUserVO[] = db.users.map((u) => ({
      userId: u.userId,
      nickname: u.nickname,
      phone: u.phone,
      role: u.role,
      status: u.status || 'active',
    }));
    return ok(pageOf(items, 1, 50));
  }
  if (req.method === 'POST' && req.path === '/admin/users') {
    const { error, user } = requireStaff(req);
    if (error) return error;
    if (user!.role !== 'admin') return fail('仅管理员可访问', 'FORBIDDEN');
    const body = req.body as {
      nickname: string;
      phone?: string;
      password: string;
      role: UserVO['role'];
    };
    const nu = {
      userId: uid('u'),
      nickname: body.nickname,
      phone: body.phone ? `${body.phone.slice(0, 3)}****${body.phone.slice(-4)}` : null,
      phoneRaw: body.phone,
      role: body.role || 'staff',
      avatarUrl: null,
      password: body.password,
      status: 'active' as const,
    };
    db.users.push(nu);
    return ok({
      userId: nu.userId,
      nickname: nu.nickname,
      phone: nu.phone,
      role: nu.role,
      status: nu.status,
    });
  }
  const upd = req.path.match(/^\/admin\/users\/([^/]+)$/);
  if (req.method === 'PUT' && upd) {
    const { error, user } = requireStaff(req);
    if (error) return error;
    if (user!.role !== 'admin') return fail('仅管理员可访问', 'FORBIDDEN');
    const u = db.users.find((x) => x.userId === upd[1]);
    if (!u) return fail('用户不存在', 'NOT_FOUND');
    const body = (req.body || {}) as Partial<Pick<AdminUserVO, 'role' | 'status'>>;
    if (u.userId === user!.userId && (body.role !== undefined || body.status !== undefined)) {
      return fail('不能修改自己的角色或启用状态', 'SELF_PERMISSION_CHANGE_FORBIDDEN');
    }
    if (body.role !== undefined && !['user', 'staff', 'admin'].includes(body.role)) {
      return fail('角色无效', 'VALIDATION_ERROR');
    }
    if (body.status !== undefined && !['active', 'disabled'].includes(body.status)) {
      return fail('状态无效', 'VALIDATION_ERROR');
    }
    const activeAdminCount = db.users.filter(
      (candidate) => candidate.role === 'admin' && candidate.status !== 'disabled',
    ).length;
    const removesLastAdmin =
      u.role === 'admin' &&
      u.status !== 'disabled' &&
      activeAdminCount <= 1 &&
      ((body.role !== undefined && body.role !== 'admin') || body.status === 'disabled');
    if (removesLastAdmin) {
      return fail('至少需要保留一位启用中的管理员', 'LAST_ADMIN_PROTECTED');
    }
    if (body.role !== undefined) u.role = body.role;
    if (body.status !== undefined) {
      u.status = body.status;
      if (body.status === 'disabled') {
        for (const [token, userId] of db.tokens.entries()) {
          if (userId === u.userId) db.tokens.delete(token);
        }
      }
    }
    return ok({
      userId: u.userId,
      nickname: u.nickname,
      phone: u.phone,
      role: u.role,
      status: u.status || 'active',
    });
  }
  return null;
}

function handleTickets(req: MockRequest): ApiEnvelope<unknown> | null {
  const db = getDb();
  if (req.method === 'GET' && req.path === '/tickets/verify') {
    const payload = req.query.payload || '';
    const order = [...db.orders.values()].find((o) => o.qrPayload === payload);
    if (!order || order.status !== 'issued') {
      const data: TicketVerifyVO = { valid: false, reason: 'TICKET_INVALID' };
      return ok(data);
    }
    const data: TicketVerifyVO = {
      valid: true,
      orderId: order.orderId,
      ticketCode: order.ticketCode || undefined,
      movieTitle: order.movieTitle,
      cinemaName: order.cinemaName,
      hallName: order.hallName,
      startTime: order.startTime,
      seatIds: order.seatIds,
      payAt: order.payAt || undefined,
      userId: order.userId,
    };
    return ok(data);
  }
  return null;
}

function handleAgent(req: MockRequest): ApiEnvelope<unknown> | null {
  if (!(req.method === 'POST' && req.path === '/agent/turns')) return null;
  const body = (req.body || {}) as AgentTurnRequest;
  if (body.cardAction?.actionId === 'fill_slot') {
    const message = body.cardAction.itemId || body.cardAction.draftPatch?.message;
    if (typeof message === 'string' && message.trim()) {
      return handleAgent({
        ...req,
        body: { ...body, cardAction: undefined, message },
      });
    }
  }
  const draft = getOrCreateDraft(body.sessionId);
  if (
    body.clientDraftVersion != null &&
    body.clientDraftVersion !== draft.version &&
    body.clientDraftVersion < draft.version
  ) {
    // soft: still allow if close
  }

  const user = authUser(req);
  let replyText = '';
  const cards: AgentCardVO[] = [];
  const db = getDb();

  const applyPatch = (patch: Partial<BookingDraft>) => {
    Object.assign(draft, patch);
    draft.version += 1;
    draft.updatedAt = nowIso();
    db.drafts.set(draft.sessionId, draft);
  };

  if (body.cardAction) {
    const { actionId, itemId, draftPatch } = body.cardAction;
    if (actionId === 'retry') {
      const date = nowIso().slice(0, 10);
      const shows = db.shows
        .filter(
          (show) =>
            show.cinemaId === draft.cinemaId &&
            show.movieId === draft.movieId &&
            dateOfShow(show) === date &&
            show.status === 'on_sale' &&
            (parseShowTime(show.startTime) || 0) > Date.now(),
        )
        .slice(0, 5);
      applyPatch({ state: 'SelectShow', seatIds: [] });
      replyText = shows.length ? '请重新选择可购买的场次。' : '当前影院暂无可购买场次，请更换影院。';
      cards.push({
        cardId: uid('card'),
        type: shows.length ? 'show_list' : 'error',
        title: shows.length ? '重新选择场次' : '暂无可购买场次',
        payload: shows.length ? { date, shows } : { code: 'NO_SHOW_AVAILABLE', message: replyText },
        actions: shows.map((show) => ({ actionId: 'select', label: '选这场', itemId: show.showId })),
      });
    } else if (actionId === 'payment_done' && draftPatch?.orderId) {
      const order = db.orders.get(String(draftPatch.orderId));
      if (order?.status === 'issued') {
        applyPatch({ state: 'TicketIssued', orderId: order.orderId });
        replyText = '出票成功！请凭取票码到影院取票。';
        cards.push({
          cardId: uid('card'),
          type: 'ticket_issued',
          title: '购票成功',
          payload: { order },
          actions: [{ actionId: 'view_order', label: '查看订单', itemId: order.orderId }],
        });
      } else {
        replyText = '订单尚未支付完成，请先完成支付。';
      }
    } else if (actionId === 'select' || actionId === 'confirm') {
      if (itemId?.startsWith('m')) {
        applyPatch({
          movieId: itemId,
          filmTitle: movieById(itemId)?.title,
          state: 'SelectCinema',
        });
        const cinemas = db.cinemas.slice(0, 3);
        replyText = `已选择《${movieById(itemId)?.title}》，这些影院有场次：`;
        cards.push({
          cardId: uid('card'),
          type: 'cinema_list',
          title: '选择影院',
          payload: { cinemas },
          actions: cinemas.map((c) => ({
            actionId: 'select',
            label: '选这家',
            itemId: c.cinemaId,
          })),
        });
        applyPatch({ listContext: { type: 'cinema', ids: cinemas.map((c) => c.cinemaId) } });
      } else if (itemId?.startsWith('c')) {
        applyPatch({ cinemaId: itemId, state: 'SelectShow' });
        const date = nowIso().slice(0, 10);
        const shows = db.shows
          .filter(
            (s) =>
              s.cinemaId === itemId &&
              s.movieId === draft.movieId &&
              dateOfShow(s) === date &&
              s.status === 'on_sale' &&
              (parseShowTime(s.startTime) || 0) > Date.now(),
          )
          .slice(0, 5);
        replyText = '好的，这些场次可以选：';
        cards.push({
          cardId: uid('card'),
          type: 'show_list',
          title: '选择场次',
          payload: { date, shows },
          actions: shows.map((s) => ({
            actionId: 'select',
            label: '选这场',
            itemId: s.showId,
          })),
        });
      } else if (itemId?.startsWith('s')) {
        const show = showById(itemId);
        if (!show || show.status !== 'on_sale' || (parseShowTime(show.startTime) || 0) <= Date.now()) {
          replyText = '该场次已停售、取消或开场，请重新选择场次。';
          cards.push({
            cardId: uid('card'),
            type: 'error',
            title: '场次不可购买',
            payload: { code: 'SHOW_NOT_ON_SALE', message: replyText },
            actions: [{ actionId: 'retry', label: '重新选场' }],
          });
          return ok({
            sessionId: draft.sessionId,
            replyText,
            draft,
            cards,
            progress: progressFromDraft(draft),
            needLogin: false,
          } satisfies AgentTurnResponse);
        }
        applyPatch({ showId: itemId, state: 'SelectSeat' });
        if (!user) {
          replyText = '选座下单需要登录，请先登录。';
          return ok({
            sessionId: draft.sessionId,
            replyText,
            draft,
            cards: [],
            progress: progressFromDraft(draft),
            needLogin: true,
          } satisfies AgentTurnResponse);
        }
        // auto recommend seats and lock+order
        const recoBody = { showId: itemId, count: draft.count || 2 };
        const sm = seatMapForShow(itemId)!;
        const available = sm.seats.filter((s) => s.status === 'available').slice(0, draft.count || 2);
        const plans: SeatPlanVO[] = [
          {
            planId: uid('plan'),
            seatIds: available.map((s) => s.seatId),
            score: 88,
            explain: '智能推荐连座',
            seats: available,
          },
        ];
        replyText = '为你推荐了这些座位方案：';
        cards.push({
          cardId: uid('card'),
          type: 'seat_plans',
          title: '智能选座',
          payload: { plans, compromise: null, showId: itemId },
          actions: [
            ...plans.map((p) => ({
              actionId: 'confirm',
              label: '确认方案',
              itemId: p.planId,
              draftPatch: { seatIds: p.seatIds },
            })),
            { actionId: 'manual', label: '自己选' },
          ],
        });
        void recoBody;
      } else if (itemId?.startsWith('plan') || draftPatch?.seatIds) {
        const seatIds = (draftPatch?.seatIds as string[]) || [];
        if (!user) {
          return ok({
            sessionId: draft.sessionId,
            replyText: '下单需要登录',
            draft,
            cards: [],
            progress: progressFromDraft(draft),
            needLogin: true,
          } satisfies AgentTurnResponse);
        }
        // find plan seats from last or draftPatch
        let seats = seatIds;
        if (!seats.length && body.cardAction.itemId) {
          // use first available
          const sm = seatMapForShow(draft.showId!)!;
          seats = sm.seats
            .filter((s) => s.status === 'available')
            .slice(0, draft.count || 2)
            .map((s) => s.seatId);
        }
        const statusMap = db.seatStatus.get(draft.showId!);
        const show = showById(draft.showId!);
        if (!show || show.status !== 'on_sale' || (parseShowTime(show.startTime) || 0) <= Date.now()) {
          replyText = '该场次已停售、取消或开场，请重新选择场次。';
          cards.push({
            cardId: uid('card'),
            type: 'error',
            title: '场次不可购买',
            payload: { code: 'SHOW_NOT_ON_SALE', message: replyText },
            actions: [{ actionId: 'retry', label: '重新选场' }],
          });
        }
        for (const id of seats) {
          if (statusMap?.get(id) !== 'available') {
            replyText = '座位已被抢，请重新选择';
            cards.push({
              cardId: uid('card'),
              type: 'error',
              title: '座位冲突',
              payload: { code: 'SEAT_TAKEN', message: replyText },
              actions: [{ actionId: 'retry', label: '重试' }],
            });
            break;
          }
        }
        if (!cards.length) {
          for (const id of seats) statusMap?.set(id, 'locked');
          const lock: LockVO = {
            lockId: uid('lock'),
            showId: draft.showId!,
            seatIds: seats,
            userId: user.userId,
            expireAt: plusMinutes(15),
            ttlSeconds: 900,
            status: 'active',
          };
          db.locks.set(lock.lockId, lock);
          const show = showById(draft.showId!)!;
          const movie = movieById(show.movieId)!;
          const cinema = cinemaById(show.cinemaId)!;
          const sm2 = seatMapForShow(show.showId);
          const seatById = new Map(sm2?.seats.map((x) => [x.seatId, x]) || []);
          const zonePrices = show.zonePrices || [{ zone: 'C', price: show.price }];
          const priceByZone = new Map(zonePrices.map((z) => [z.zone, z.price]));
          const seatPrices: SeatPriceSnapshot[] = seats.map((sid) => {
            const seat = seatById.get(sid);
            const zone = seat?.zone || 'C';
            const price = priceByZone.get(zone) ?? show.price;
            return { seatId: sid, zone, price, seatName: seat?.seatName };
          });
          const amount = seatPrices.reduce((sum, p) => sum + p.price, 0);
          const unitPrice =
            seatPrices.length > 0 ? Math.round((amount / seatPrices.length) * 100) / 100 : show.price;
          const order: OrderVO = {
            orderId: uid('o'),
            userId: user.userId,
            showId: show.showId,
            movieTitle: movie.title,
            cinemaName: cinema.name,
            hallName: show.hallName,
            startTime: show.startTime,
            seatIds: seats,
            unitPrice,
            amount,
            seatPrices,
            status: 'pending_pay',
            ticketCode: null,
            qrPayload: null,
            payChannel: null,
            lockId: lock.lockId,
            expireAt: lock.expireAt,
            createdAt: nowIso(),
            payAt: null,
          };
          db.orders.set(order.orderId, order);
          lock.status = 'consumed';
          applyPatch({
            seatIds: seats,
            lockId: lock.lockId,
            orderId: order.orderId,
            expireAt: lock.expireAt,
            state: 'PayMock',
            userId: user.userId,
          });
          const token = uid('pay');
          db.payTokens.set(order.orderId, token);
          const origin =
            typeof window !== 'undefined' ? window.location.origin : 'http://localhost:8000';
          replyText = '订单已创建，请扫码支付（我不会代你支付）。';
          cards.push({
            cardId: uid('card'),
            type: 'order_confirm',
            title: '确认订单',
            payload: { order },
            actions: [{ actionId: 'go_pay', label: '去支付', itemId: order.orderId }],
          });
          cards.push({
            cardId: uid('card'),
            type: 'pay_mock',
            title: '扫码支付',
            payload: {
              orderId: order.orderId,
              amount: order.amount,
              expireAt: order.expireAt,
              payUrl: `${origin}/m/pay/${order.orderId}?t=${token}`,
              pollIntervalMs: 2000,
            },
            actions: [],
          });
        }
      }
    } else if (actionId === 'manual') {
      replyText = '好的，请到选座页自行选座。';
    } else if (actionId === 'go_pay') {
      replyText = '请在支付页完成扫码支付。';
    }
  } else if (body.message) {
    const msg = body.message;

    // 草稿已有影片：跳过选片，继续影院/场次（系分 SKIP / 模式切换保护）
    const continueFromDraftMovie = () => {
      const title = draft.filmTitle || movieById(draft.movieId)?.title || '已选影片';
      if (!draft.cinemaId) {
        const cinemas = db.cinemas.slice(0, 3);
        replyText = `《${title}》已在草稿里，不用重选片。这些影院有场次：`;
        cards.push({
          cardId: uid('card'),
          type: 'cinema_list',
          title: '选择影院',
          payload: { cinemas },
          actions: cinemas.map((c) => ({
            actionId: 'select',
            label: '选这家',
            itemId: c.cinemaId,
          })),
        });
        applyPatch({
          intent: msg,
          state: 'SelectCinema',
          source: draft.source === 'manual' ? 'hybrid' : draft.source,
          listContext: { type: 'cinema', ids: cinemas.map((c) => c.cinemaId) },
        });
        return;
      }
      if (!draft.showId) {
        const date =
          draft.date ||
          (() => {
            const d = new Date();
            return d.toISOString().slice(0, 10);
          })();
        const shows = db.shows
          .filter((s) => s.movieId === draft.movieId && s.cinemaId === draft.cinemaId)
          .slice(0, 6);
        replyText = `《${title}》影院已选，看看这些场次：`;
        cards.push({
          cardId: uid('card'),
          type: 'show_list',
          title: '选择场次',
          payload: { shows, date },
          actions: shows.map((s) => ({
            actionId: 'select',
            label: '选这场',
            itemId: s.showId,
          })),
        });
        applyPatch({
          intent: msg,
          date,
          state: 'SelectShow',
          source: draft.source === 'manual' ? 'hybrid' : draft.source,
        });
        return;
      }
      replyText = `草稿已推进到选座。《${title}》可以说「帮我选座」或转手动。`;
      applyPatch({
        intent: msg,
        state: 'SelectSeat',
        source: draft.source === 'manual' ? 'hybrid' : draft.source,
      });
    };

    if (draft.movieId && !/换一部|重选|别的片|换片/.test(msg)) {
      // 已有 movieId：不追问选片（除非用户明确要换片）
      if (/附近|影院|IMAX|imax|明天|下午|两张|选座|继续/.test(msg) || msg.length < 40) {
        continueFromDraftMovie();
      } else if (/喜剧/.test(msg) && !draft.filmTitle?.includes('喜剧')) {
        // 想换类型时仍可给电影列表，但不清已有片除非用户点选
        continueFromDraftMovie();
      } else {
        continueFromDraftMovie();
      }
    } else if (/喜剧/.test(msg)) {
      const movies = db.movies.filter((m) => m.genres.includes('喜剧') && m.status === 'hot_showing');
      replyText = '好的，这几部喜剧口碑不错：';
      cards.push({
        cardId: uid('card'),
        type: 'movie_list',
        title: '喜剧推荐',
        payload: { movies },
        actions: movies.map((m) => ({
          actionId: 'select',
          label: '选这部',
          itemId: m.movieId,
        })),
      });
      applyPatch({
        intent: msg,
        genre: '喜剧',
        state: 'SelectMovie',
        listContext: { type: 'movie', ids: movies.map((m) => m.movieId) },
        source: draft.source === 'manual' ? 'agent' : draft.source,
      });
    } else if (/IMAX|imax/.test(msg)) {
      replyText = '想看 IMAX？先选一部影片吧：';
      const movies = db.movies.filter((m) => m.status === 'hot_showing').slice(0, 4);
      cards.push({
        cardId: uid('card'),
        type: 'movie_list',
        title: '热映影片',
        payload: { movies },
        actions: movies.map((m) => ({
          actionId: 'select',
          label: '选这部',
          itemId: m.movieId,
        })),
      });
      applyPatch({ intent: msg, state: 'SelectMovie', source: 'agent' });
    } else if (/明天|下午|两张/.test(msg)) {
      applyPatch({
        intent: msg,
        date: (() => {
          const d = new Date();
          d.setDate(d.getDate() + 1);
          return d.toISOString().slice(0, 10);
        })(),
        count: 2,
        timeWindow: 'afternoon',
        state: 'SelectMovie',
        source: 'agent',
      });
      const movies = db.movies.filter((m) => m.status === 'hot_showing').slice(0, 4);
      replyText = '明天下午两张票，先挑一部片子：';
      cards.push({
        cardId: uid('card'),
        type: 'movie_list',
        title: '热映影片',
        payload: { movies },
        actions: movies.map((m) => ({
          actionId: 'select',
          label: '选这部',
          itemId: m.movieId,
        })),
      });
    } else {
      const hit = db.movies.find((m) => msg.includes(m.title.slice(0, 2)));
      if (hit) {
        applyPatch({
          movieId: hit.movieId,
          filmTitle: hit.title,
          state: 'SelectCinema',
          intent: msg,
          source: 'agent',
        });
        const cinemas = db.cinemas.slice(0, 3);
        replyText = `《${hit.title}》可以，选家影院吧：`;
        cards.push({
          cardId: uid('card'),
          type: 'cinema_list',
          title: '选择影院',
          payload: { cinemas },
          actions: cinemas.map((c) => ({
            actionId: 'select',
            label: '选这家',
            itemId: c.cinemaId,
          })),
        });
      } else {
        replyText = '可以告诉我类型、时间或片名，例如「周末看喜剧」。';
        cards.push({
          cardId: uid('card'),
          type: 'ask',
          title: '继续说说',
          payload: {
            slot: 'intent',
            prompt: '想看什么类型？',
            suggestions: ['周末看喜剧', '带对象看IMAX', '明天下午两张'],
          },
          actions: [
            { actionId: 'fill_slot', label: '周末看喜剧' },
            { actionId: 'fill_slot', label: '带对象看IMAX' },
          ],
        });
      }
    }
  } else {
    replyText = '请发送消息或点击卡片。';
  }

  const res: AgentTurnResponse = {
    sessionId: draft.sessionId,
    replyText,
    draft: { ...draft },
    cards,
    progress: progressFromDraft(draft),
    needLogin: false,
    events: ['turn'],
  };
  return ok(res);
}

export async function dispatchMock(req: MockRequest): Promise<ApiEnvelope<unknown>> {
  // simulate latency
  await new Promise((r) => setTimeout(r, 80 + Math.random() * 120));

  const handlers = [
    handleAuth,
    handleMovies,
    handleCinemas,
    handleShows,
    handleReco,
    handleDrafts,
    handleLocksOrders,
    handleSeatMapsHalls,
    handleAdminUsers,
    handleTickets,
    handleAgent,
  ];

  for (const h of handlers) {
    const res = h(req);
    if (res) return res;
  }

  return fail(`Mock 未实现: ${req.method} ${req.path}`, 'NOT_FOUND');
}
