import { get, post, put, del } from './client';
import type {
  MovieVO,
  CinemaVO,
  HallVO,
  SeatMapVO,
  ShowVO,
  OrderVO,
  PageResult,
  AdminUserVO,
  TicketVerifyVO,
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

export function createShow(body: Partial<ShowVO> & {
  movieId: string;
  cinemaId: string;
  hallId: string;
  startTime: string;
  endTime: string;
  price: number;
}) {
  return post<ShowVO>('/admin/shows', body);
}

export function updateShow(showId: string, body: Partial<ShowVO>) {
  return put<ShowVO>(`/admin/shows/${showId}`, body);
}

export function cancelShow(showId: string) {
  return post<ShowVO>(`/admin/shows/${showId}/cancel`);
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

export function updateUser(userId: string, body: Partial<AdminUserVO>) {
  return put<AdminUserVO>(`/admin/users/${userId}`, body);
}

export function verifyTicket(payload: string) {
  return get<TicketVerifyVO>('/tickets/verify', { payload });
}
