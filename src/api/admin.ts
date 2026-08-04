import { get, post, put, del } from './client';
import type {
  MovieVO,
  CinemaVO,
  HallVO,
  SeatMapVO,
  ShowVO,
  ShowListResult,
  OrderVO,
  PageResult,
  AdminUserVO,
  AdminShowImpactVO,
  AdminDashboardStatsVO,
  TicketVerifyVO,
  ZonePrice,
} from '@/types';

export function createMovie(body: Partial<MovieVO>) {
  return post<MovieVO>('/admin/movies', body);
}

export function updateMovie(movieId: string, body: Partial<MovieVO>) {
  return put<MovieVO>(`/admin/movies/${movieId}`, body);
}

export interface CinemaCreateBody {
  cinemaId?: string;
  cityId?: string;
  cityName: string;
  name: string;
  address: string;
  lat: number;
  lng: number;
  trafficNote?: string;
  tags?: string[];
}

export type CinemaUpdateBody = Partial<Omit<CinemaCreateBody, 'cinemaId'>>;

export function createCinema(body: CinemaCreateBody) {
  return post<CinemaVO>('/admin/cinemas', body);
}

export function updateCinema(cinemaId: string, body: CinemaUpdateBody) {
  return put<CinemaVO>(`/admin/cinemas/${cinemaId}`, body);
}

/** 删除影院。服务端软删除并保留历史影厅、场次及订单关联。 */
export function deleteCinema(cinemaId: string) {
  return del<null>(`/admin/cinemas/${cinemaId}`);
}

export function listHalls(params?: { cinemaId?: string; page?: number; size?: number }) {
  return get<PageResult<HallVO>>('/admin/halls', params);
}

export interface HallCreateBody {
  hallId?: string;
  cinemaId?: string;
  name: string;
  seatMapId: string;
}

export interface HallUpdateBody {
  name: string;
}

export function createHall(body: HallCreateBody) {
  return post<HallVO>('/halls', body);
}

export function updateHall(hallId: string, body: HallUpdateBody) {
  return put<HallVO>(`/admin/halls/${hallId}`, body);
}

export interface SeatMapCreateBody {
  seatMapId?: string;
  cinemaId?: string;
  rows: number;
  cols: number;
  screenLabel?: string;
  seats: Array<Pick<SeatMapVO['seats'][number], 'graphRow' | 'graphCol' | 'rowNo' | 'colNo' | 'seatId' | 'type' | 'zone' | 'defaultStatus' | 'couplePairId'>>;
}

export type SeatMapUpdateBody = Omit<SeatMapCreateBody, 'seatMapId' | 'cinemaId'>;

/** 运营端座位图列表；staff 自动本院，admin 须传 cinemaId。 */
export function listSeatMaps(params?: { cinemaId?: string; page?: number; size?: number }) {
  return get<PageResult<SeatMapVO>>('/seat-maps', params);
}

/** 查询座位图模板详情（含座位明细）。 */
export function getSeatMapTemplate(seatMapId: string) {
  return get<SeatMapVO>(`/seat-maps/${seatMapId}`);
}

export function createSeatMap(body: SeatMapCreateBody) {
  return post<SeatMapVO>('/seat-maps', body);
}

/** 全量替换座位集合；仅 mutable 时可改。 */
export function updateSeatMap(seatMapId: string, body: SeatMapUpdateBody) {
  return put<SeatMapVO>(`/seat-maps/${seatMapId}`, body);
}

/** 删除座位图；仍被影厅/场次引用时 409。 */
export function deleteSeatMap(seatMapId: string) {
  return del<{ deleted: boolean; seatMapId: string }>(`/seat-maps/${seatMapId}`);
}

export function createShow(body: {
  movieId: string;
  cinemaId: string;
  hallId: string;
  startTime: string;
  endTime: string;
  zonePrices: ZonePrice[];
  /** @deprecated 忽略；服务端用 zonePrices 回填 min */
  price?: number;
}) {
  return post<ShowVO>('/admin/shows', body);
}

export function adminListShows(params: { cinemaId: string; movieId: string; date?: string }) {
  return get<ShowListResult>('/admin/shows', params);
}

export function updateShow(
  showId: string,
  body: Partial<Pick<ShowVO, 'startTime' | 'endTime' | 'price'>> & {
    zonePrices?: ZonePrice[];
  },
) {
  return put<ShowVO>(`/admin/shows/${showId}`, body);
}

export function cancelShow(showId: string) {
  return post<ShowVO>(`/admin/shows/${showId}/cancel`);
}

export function resumeShowSale(showId: string) {
  return post<ShowVO>(`/admin/shows/${showId}/resume-sale`);
}

export function closeShowSale(showId: string) {
  return post<ShowVO>(`/admin/shows/${showId}/close-sale`);
}

export function getShowImpact(showId: string) {
  return get<AdminShowImpactVO>(`/admin/shows/${showId}/impact`);
}

/** 获取运营看板汇总，场次数量按指定日期统计全部在售场次。 */
export function getDashboardStats(date: string) {
  return get<AdminDashboardStatsVO>('/admin/dashboard/stats', { date });
}

/** 运营协助查单；与中台 GET /admin/orders 对齐。 */
export function adminListOrders(params?: {
  orderId?: string;
  userId?: string;
  status?: string;
  dateFrom?: string;
  dateTo?: string;
  page?: number;
  size?: number;
}) {
  return get<PageResult<OrderVO>>('/admin/orders', params);
}

/** 运营关闭待支付订单并释放座位；POST /admin/orders/:orderId/cancel。 */
export function adminCancelOrder(orderId: string, reason?: string) {
  return post<OrderVO>(`/admin/orders/${orderId}/cancel`, reason ? { reason } : {});
}

/** 运营核销已出票订单；POST /admin/orders/:orderId/consume。 */
export function consumeTicket(orderId: string) {
  return post<OrderVO>(`/admin/orders/${orderId}/consume`);
}

export function listUsers(params?: { page?: number; size?: number }) {
  return get<PageResult<AdminUserVO>>('/admin/users', params);
}

export function createUser(body: {
  nickname: string;
  phone?: string;
  password: string;
  role: string;
  cinemaId?: string | null;
}) {
  return post<AdminUserVO>('/admin/users', body);
}

export function updateUser(
  userId: string,
  body: Partial<{
    role: AdminUserVO['role'];
    status: 0 | 1;
    cinemaId: string | null;
    nickname: string;
    phone: string;
    password: string;
  }>,
) {
  return put<AdminUserVO>(`/admin/users/${userId}`, body);
}

export function verifyTicket(payload: string) {
  return get<TicketVerifyVO>('/tickets/verify', { payload });
}
