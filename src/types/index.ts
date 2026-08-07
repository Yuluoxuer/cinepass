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
export type OrderStatus = 'pending_pay' | 'issued' | 'cancelled' | 'redeemed' | 'expired';
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
  /** 已由接口层或全局兜底提示过，避免重复弹出错误消息。 */
  notified?: boolean;
  /** 调用方要求自行呈现错误态时，不触发全局错误页。 */
  silent?: boolean;

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
  /** staff 所属影院；真实环境从 JWT claim 补齐，Mock 由登录态直接返回。 */
  cinemaId?: string | null;
}

export interface LoginResult {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
  userId: string;
  nickname: string;
  phone: string | null;
  role: UserRole;
  /** staff 所属影院；user/admin 为 null */
  cinemaId?: string | null;
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
  /** 导演，详情页可选展示字段 */
  director?: string;
  /** 结构化演职人员，缺失时可由 cast 字段降级生成 */
  castMembers?: CastMemberVO[];
  wantSeeCount: number;
  /** 院→片：该影院下一场本地日历日 yyyy-MM-dd */
  nextShowDate?: string | null;
}

export interface CastMemberVO {
  name: string;
  role: string;
  avatarUrl: string | null;
}

export interface CinemaVO {
  cinemaId: string;
  name: string;
  address: string;
  cityId?: string;
  cityName?: string;
  lat?: number;
  lng?: number;
  distanceMeters: number | null;
  minPrice: number | null;
  /** 影院特色厅标签，例如 IMAX、杜比全景声 */
  features?: string[];
  trafficNote?: string;
  tags?: string[];
  halls?: HallVO[];
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
  status?: 'on_sale' | 'off_sale' | 'cancelled';
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
  cinemaId?: string;
  /** 座位图名称（运营展示） */
  name: string;
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
  seatCount?: number;
}

/** 座位图被影厅、场次使用时的引用摘要，用于删除前治理提示。 */
export interface SeatMapUsageVO {
  seatMapId: string;
  hallCount: number;
  showCount: number;
  hallNames: string[];
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
  nickname?: string;
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
  /** 取消来源，例如场次取消。用于运营端和用户端说明订单变化原因。 */
  cancelReason?: string | null;
  /** 入场核销时间；仅已检票订单存在。 */
  verifiedAt?: string | null;
}

export interface AdminShowImpactVO {
  showId: string;
  pendingPayCount: number;
  issuedCount: number;
  usedCount: number;
}

/** 运营看板聚合数据，场次数量按指定日期的在售场次统计。 */
export interface AdminDashboardStatsVO {
  date: string;
  totalOrderCount: number;
  pendingPayOrderCount: number;
  issuedOrderCount: number;
  onSaleShowCount: number;
}

export interface PayQrVO {
  orderId: string;
  amount: number;
  expireAt: string;
  payUrl: string;
  pollIntervalMs: number;
}

export interface PaySessionVO {
  orderId: string;
  amount: number;
  expireAt: string | null;
  movieTitle: string;
  cinemaName: string;
  hallName: string;
  startTime: string;
  seatIds: string[];
  /** 座位号列表（如「6排7座」，取自座位价区快照） */
  seatNames: string[];
  status: OrderStatus;
  /** 取票码；待支付为空，出票后非空 */
  ticketCode: string | null;
}

/** 核销二维码返回体；前端将 redeemUrl 编码为二维码。 */
export interface RedeemQrVO {
  orderId: string;
  ticketCode: string | null;
  redeemUrl: string;
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
  /** staff 所属影院；user/admin 为 null */
  cinemaId?: string | null;
  /** 后端为 0|1；展示层兼容 active/disabled */
  status: 'active' | 'disabled' | 0 | 1;
}

export interface SearchSuggestionItem {
  text: string;
}

/** ES Completion Suggester 返回的候选词 */
export type SearchSuggestionResult = string[];

export type SeatNameMap = Record<string, string>;
