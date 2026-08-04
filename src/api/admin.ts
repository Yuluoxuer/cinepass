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
  SeatMapUsageVO,
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

export function createCinema(body: Partial<CinemaVO>) {
  return post<CinemaVO>('/admin/cinemas', body);
}

export function updateCinema(cinemaId: string, body: Partial<CinemaVO>) {
  return put<CinemaVO>(`/admin/cinemas/${cinemaId}`, body);
}

export function listHalls(cinemaId?: string) {
  return get<PageResult<HallVO>>('/admin/halls', { cinemaId });
}

export function createHall(body: Partial<HallVO>) {
  return post<HallVO>('/halls', body);
}

export function updateHall(hallId: string, body: Partial<HallVO>) {
  return put<HallVO>(`/admin/halls/${hallId}`, body);
}

export function listSeatMaps() {
  return get<PageResult<SeatMapVO>>('/seat-maps');
}

export function getSeatMapTemplate(id: string) {
  return get<SeatMapVO>(`/seat-maps/${id}`);
}

export function createSeatMap(body: Partial<SeatMapVO>) {
  return post<SeatMapVO>('/seat-maps', body);
}

export function updateSeatMap(id: string, body: Partial<SeatMapVO>) {
  return put<SeatMapVO>(`/seat-maps/${id}`, body);
}

export function deleteSeatMap(id: string) {
  return del<{ deleted: boolean }>(`/seat-maps/${id}`);
}

export function getSeatMapUsage(id: string) {
  return get<SeatMapUsageVO>(`/seat-maps/${id}/usage`);
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

export function adminListOrders(params?: {
  orderId?: string;
  userId?: string;
  status?: string;
  page?: number;
  size?: number;
}) {
  return get<PageResult<OrderVO>>('/admin/orders', params);
}

/** 运营人员关闭待支付订单。 */
export function adminCancelOrder(orderId: string, reason = 'admin_closed') {
  return post<OrderVO>(`/admin/orders/${orderId}/cancel`, { reason });
}

/** 运营人员现场核销已出票订单。 */
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
}) {
  return post<AdminUserVO>('/admin/users', body);
}

export function updateUser(
  userId: string,
  body: Partial<Pick<AdminUserVO, 'role' | 'status'>>,
) {
  return put<AdminUserVO>(`/admin/users/${userId}`, body);
}

export function verifyTicket(payload: string) {
  return get<TicketVerifyVO>('/tickets/verify', { payload });
}
