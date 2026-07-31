/** 与后端系分 VO/DTO 对齐 */

export type BookingState =
  | 'Idle'
  | 'SelectMovie'
  | 'SelectCinema'
  | 'SelectShow'
  | 'SelectSeat'
  | 'ConfirmOrder'
  | 'PayMock'
  | 'TicketIssued';

export type UserRole = 'user' | 'staff' | 'admin';

export type MovieStatus = 'hot_showing' | 'coming_soon' | 'off';

export type SeatStatus = 'available' | 'locked' | 'sold' | 'unavailable';
export type SeatType = 'normal' | 'couple' | 'disabled';
/** 座位分区 code：座位图自定义，无枚举/正则限制（如 A/B/C、VIP） */
export type SeatZone = string;
export type SeatRemainLevel = 'ample' | 'tight' | 'almost_full';
export type OrderStatus = 'pending_pay' | 'issued' | 'cancelled';
export type LockStatus = 'active' | 'expired' | 'consumed' | 'released';

export interface ZonePrice {
  zone: string;
  price: number;
}

export interface SeatPriceSnapshot {
  seatId: string;
  zone: string;
  price: number;
  seatName?: string;
}

export interface PageResult<T> {
  items: T[];
  page: number;
  size: number;
  total: number;
}

export interface ApiEnvelope<T = unknown> {
  code: number;
  message: string;
  data: T;
  accessToken?: string | null;
  traceId?: string;
}

export interface ApiErrorData {
  errorCode?: string;
  fieldErrors?: Array<{ field: string; rejectedValue?: unknown; message: string }>;
  [key: string]: unknown;
}

export class ApiError extends Error {
  code: number;
  errorCode?: string;
  data?: ApiErrorData;
  httpStatus?: number;

  constructor(
    message: string,
    opts?: { code?: number; errorCode?: string; data?: ApiErrorData; httpStatus?: number },
  ) {
    super(message);
    this.name = 'ApiError';
    this.code = opts?.code ?? -1;
    this.errorCode = opts?.errorCode;
    this.data = opts?.data;
    this.httpStatus = opts?.httpStatus;
  }
}

export interface UserVO {
  userId: string;
  nickname: string;
  phone: string | null;
  role: UserRole;
  avatarUrl: string | null;
}

export interface LoginResult {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
  userId: string;
  nickname: string;
  phone: string | null;
  role: UserRole;
}

export interface MovieVO {
  movieId: string;
  title: string;
  posterUrl: string;
  genres: string[];
  rating: number | null;
  durationMin: number;
  releaseDate: string;
  status: MovieStatus;
  description: string;
  cast: string;
  wantSeeCount: number;
}

export interface CinemaVO {
  cinemaId: string;
  name: string;
  address: string;
  cityId?: string;
  lat?: number;
  lng?: number;
  distanceMeters: number | null;
  minPrice: number | null;
}

export interface ShowVO {
  showId: string;
  movieId: string;
  cinemaId: string;
  hallId: string;
  hallName: string;
  startTime: string;
  endTime: string;
  /** 最低区价（列表「¥xx起」） */
  price: number;
  /** 本场各区单价 */
  zonePrices?: ZonePrice[];
  seatRemain: number;
  seatRemainLevel: SeatRemainLevel;
  status?: 'on_sale' | 'cancelled';
}

export interface SeatVO {
  seatId: string;
  seatName: string;
  rowNo: number;
  colNo: number;
  graphRow: number;
  graphCol: number;
  type: SeatType;
  zone: SeatZone;
  status?: SeatStatus;
  defaultStatus?: 'available' | 'unavailable';
  couplePairId: string | null;
  /** 本场座位图冗余：所属区单价 */
  price?: number;
}

export interface SeatMapVO {
  seatMapId: string;
  rows: number;
  cols: number;
  screenLabel: string;
  seats: SeatVO[];
  mutable?: boolean;
  legend?: Record<string, string>;
  /** 最低区价（兼容） */
  price?: number;
  zonePrices?: ZonePrice[];
  /** 模板/辅助：本图出现的区 */
  zones?: string[];
  showId?: string;
}

export interface SeatPlanVO {
  planId: string;
  seatIds: string[];
  score: number;
  explain: string;
  seats?: SeatVO[];
}

export interface SeatRecoResult {
  showId: string;
  plans: SeatPlanVO[];
  compromise: null | { suggestion: string; altShowIds: string[] };
}

export interface LockVO {
  lockId: string;
  showId: string;
  seatIds: string[];
  userId: string;
  expireAt: string;
  ttlSeconds: number;
  status: LockStatus;
}

export interface OrderVO {
  orderId: string;
  userId: string;
  showId: string;
  movieTitle: string;
  cinemaName: string;
  hallName: string;
  startTime: string;
  seatIds: string[];
  /** @deprecated 所选座均价；新 UI 读 seatPrices */
  unitPrice: number;
  amount: number;
  seatPrices?: SeatPriceSnapshot[];
  status: OrderStatus;
  ticketCode: string | null;
  qrPayload: string | null;
  payChannel: string | null;
  lockId: string;
  expireAt: string | null;
  createdAt: string;
  payAt: string | null;
}

export interface PayQrVO {
  orderId: string;
  amount: number;
  expireAt: string;
  payUrl: string;
  pollIntervalMs: number;
  payToken?: string;
}

export interface PaySessionVO {
  orderId: string;
  amount: number;
  expireAt: string;
  movieTitle: string;
  cinemaName: string;
  hallName: string;
  startTime: string;
  seatIds: string[];
  status: OrderStatus;
}

export interface TicketVerifyVO {
  valid: boolean;
  reason?: string;
  orderId?: string;
  ticketCode?: string;
  movieTitle?: string;
  cinemaName?: string;
  hallName?: string;
  startTime?: string;
  seatIds?: string[];
  payAt?: string;
  userId?: string;
}

export interface ListContext {
  type: string;
  ids: string[];
}

export interface BookingDraft {
  sessionId: string;
  userId?: string;
  source: 'manual' | 'agent' | 'hybrid';
  state: BookingState;
  intent?: string;
  movieId?: string;
  filmTitle?: string;
  genre?: string;
  date?: string;
  timeWindow?: string;
  lat?: number;
  lng?: number;
  cinemaId?: string;
  showId?: string;
  count: number;
  seatIds: string[];
  preferRow?: 'front' | 'middle' | 'back';
  preferSide?: 'center' | 'aisle' | 'edge';
  together?: boolean;
  budgetMax?: number;
  listContext?: ListContext;
  lockId?: string;
  orderId?: string;
  expireAt?: string;
  updatedAt?: string;
  version: number;
}

export type BookingDraftVO = BookingDraft;

export interface DraftPatchBody {
  version: number;
  patch: Partial<
    Pick<
      BookingDraft,
      | 'source'
      | 'state'
      | 'intent'
      | 'movieId'
      | 'filmTitle'
      | 'genre'
      | 'date'
      | 'timeWindow'
      | 'lat'
      | 'lng'
      | 'cinemaId'
      | 'showId'
      | 'count'
      | 'seatIds'
      | 'preferRow'
      | 'preferSide'
      | 'together'
      | 'budgetMax'
      | 'listContext'
    >
  >;
}

export interface CardAction {
  actionId: string;
  label: string;
  draftPatch?: Record<string, unknown> | null;
  itemId?: string | null;
}

export type AgentCardType =
  | 'movie_list'
  | 'cinema_list'
  | 'show_list'
  | 'seat_plans'
  | 'order_confirm'
  | 'pay_mock'
  | 'ticket_issued'
  | 'error'
  | 'ask';

export interface AgentCardVO {
  cardId: string;
  type: AgentCardType;
  title: string;
  payload: Record<string, unknown>;
  actions: CardAction[];
}

export interface AgentProgress {
  steps: string[];
  currentIndex: number;
  state: BookingState;
}

export interface AgentTurnRequest {
  sessionId?: string;
  message?: string;
  cardAction?: {
    cardId: string;
    actionId: string;
    itemId?: string;
    draftPatch?: Record<string, unknown>;
  };
  clientDraftVersion?: number;
  debug?: boolean;
}

export interface AgentTurnResponse {
  sessionId: string;
  replyText: string;
  draft: BookingDraftVO;
  cards: AgentCardVO[];
  progress: AgentProgress;
  needLogin: boolean;
  events?: string[];
  toolTraces?: unknown[];
}

export interface WeeklyHotItem {
  rank: number;
  hotScore: number;
  heatTag: string;
  movie: MovieVO;
}

export interface WeeklyHotResult {
  computedAt: string;
  items: WeeklyHotItem[];
}

export interface PersonalRecoItem {
  movie: MovieVO;
  personalScore: number;
  reason: string;
}

export interface PersonalRecoResult {
  mode: string;
  items: PersonalRecoItem[];
}

export interface ShowListResult {
  date: string;
  items: ShowVO[];
}

export interface HallVO {
  hallId: string;
  cinemaId: string;
  name: string;
  seatMapId: string;
  showCount?: number;
}

export interface AdminUserVO {
  userId: string;
  nickname: string;
  phone: string | null;
  role: UserRole;
  status: 'active' | 'disabled';
}

export type SeatNameMap = Record<string, string>;
