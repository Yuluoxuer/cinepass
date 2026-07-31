import { get, post, put, del } from './client';
import type {
  MovieVO,
  CinemaVO,
  ShowListResult,
  SeatMapVO,
  PageResult,
  WeeklyHotResult,
  PersonalRecoResult,
  SeatRecoResult,
} from '@/types';

export function listMovies(params?: {
  status?: string;
  q?: string;
  genre?: string;
  page?: number;
  size?: number;
}) {
  return get<PageResult<MovieVO>>('/movies', params);
}

export function getMovie(movieId: string) {
  return get<MovieVO>(`/movies/${movieId}`);
}

export function wantSee(movieId: string) {
  return post<{ movieId: string; wanted: boolean }>(`/movies/${movieId}/want-see`);
}

export function unwantSee(movieId: string) {
  return del<{ movieId: string; wanted: boolean }>(`/movies/${movieId}/want-see`);
}

export function listWantSee(params?: { page?: number; size?: number }) {
  return get<PageResult<MovieVO>>('/me/want-see', params);
}

export function listCinemas(params?: {
  movieId?: string;
  lat?: number;
  lng?: number;
  radiusMeters?: number;
  sort?: string;
  page?: number;
  size?: number;
}) {
  return get<PageResult<CinemaVO>>('/cinemas', params);
}

export function getCinema(cinemaId: string) {
  return get<CinemaVO>(`/cinemas/${cinemaId}`);
}

export function listShows(params: { cinemaId: string; movieId: string; date: string }) {
  return get<ShowListResult>('/shows', params);
}

export function getSeatMap(showId: string) {
  return get<SeatMapVO>(`/shows/${showId}/seat-map`);
}

export function weeklyHot(limit = 10) {
  return get<WeeklyHotResult>('/reco/weekly-hot', { limit });
}

export function personalReco(limit = 10) {
  return get<PersonalRecoResult>('/reco/personal', { limit }, { silent: true });
}

export function recommendSeats(body: {
  showId: string;
  count: number;
  preferRow?: string;
  preferSide?: string;
  together?: boolean;
}) {
  return post<SeatRecoResult>('/reco/seats', body);
}
