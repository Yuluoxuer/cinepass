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

/** 下架：校验该影片未来无在售场次 */
export function takeDownMovie(movieId: string) {
  return post<MovieVO>(`/admin/movies/${movieId}/take-down`);
}

/** 上架：按上映日期自动推导为热映或待映 */
export function relistMovie(movieId: string) {
  return post<MovieVO>(`/admin/movies/${movieId}/relist`);
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
  /** 换绑座位图；不传则保持原绑定 */
  seatMapId?: string;
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
  name: string;
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

export interface BatchCreateShowBody {
  movieId: string;
  cinemaId: string;
  hallId: string;
  dateStart: string;   // yyyy-MM-dd
  dateEnd: string;     // yyyy-MM-dd
  timeStart: string;   // HH:mm
  timeEnd: string;     // HH:mm
  intervalMin: number; // 场次间隔（分钟），须 ≥ 影片时长
  zonePrices: ZonePrice[];
  price?: number;
}

export function createBatchShows(body: BatchCreateShowBody) {
  return post<ShowVO[]>('/admin/shows/batch', body);
}

export function adminListShows(params: { cinemaId: string; movieId?: string; date?: string; after?: string }) {
  const query: Record<string, string> = { cinemaId: params.cinemaId };
  if (params.movieId) query.movieId = params.movieId;
  if (params.date) query.date = params.date;
  if (params.after) query.after = params.after;
  return get<ShowListResult>('/admin/shows', query);
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

/** 上传海报图片，返回可访问的 URL 路径。 */
export async function uploadPoster(file: File): Promise<string> {
  const formData = new FormData();
  formData.append('file', file);
  const { getAccessToken } = await import('@/stores/auth');
  const token = getAccessToken();
  const resp = await fetch('/api/v1/admin/upload/poster', {
    method: 'POST',
    headers: token ? { Authorization: `Bearer ${token}` } : {},
    body: formData,
  });
  const json = await resp.json();
  if (json.code !== 200) {
    throw new Error(json.message || '上传失败');
  }
  return json.data as string;
}

/** ===== 知识库管理（agent4 RAG，前缀 /agent4/knowledge） ===== */

export interface KnowledgeFileVO {
  filename: string;
  chunkCount: number;
  scope: string;
  cinemaId?: string | null;
  updatedAt?: string | null;
}

/** 列出当前账号作用域知识库的文档（admin→系统知识库，staff→本院知识库）。 */
export function listKnowledgeFiles() {
  return get<KnowledgeFileVO[]>('/agent4/knowledge/files');
}

/** 上传 Markdown 知识文档到当前账号作用域知识库。 */
export async function uploadKnowledgeFile(file: File): Promise<KnowledgeFileVO> {
  const formData = new FormData();
  formData.append('file', file);
  const { getAccessToken } = await import('@/stores/auth');
  const token = getAccessToken();
  const resp = await fetch('/api/v1/agent4/knowledge/files', {
    method: 'POST',
    headers: token ? { Authorization: `Bearer ${token}` } : {},
    body: formData,
  });
  const json = await resp.json();
  if (json.code !== 200) {
    throw new Error(json.message || '上传失败');
  }
  return json.data as KnowledgeFileVO;
}

/** 删除知识库中的指定文档（向量块 + 磁盘原文）。 */
export function deleteKnowledgeFile(filename: string) {
  return del<null>(`/agent4/knowledge/files/${encodeURIComponent(filename)}`);
}
