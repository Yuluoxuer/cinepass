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
  SearchSuggestionResult,
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
  q?: string;
  lat?: number;
  lng?: number;
  radiusMeters?: number;
  sort?: 'distance' | 'price';
  page?: number;
  size?: number;
}) {
  return get<PageResult<CinemaVO>>('/cinemas', params);
}

export function getCinema(cinemaId: string) {
  return get<CinemaVO>(`/cinemas/${cinemaId}`);
}

/** 院→片：当前时刻之后该院仍有在售场次的影片 */
export function listCinemaMovies(cinemaId: string) {
  return get<PageResult<MovieVO>>(`/cinemas/${cinemaId}/movies`);
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

/** 搜索联想：ES Completion Suggester，返回候选词数组 */
export function searchSuggestions(q: string, size = 8) {
  return get<SearchSuggestionResult>('/search/suggestions', { q, size }, { silent: true });
}

/** 全部影片类型标签（后端 tag 字典表，按名称排序） */
export function listGenres() {
  return get<string[]>('/movies/genres');
}
