import { create } from 'zustand';
import type {
  AgentCardVO,
  AgentProgress,
  AgentTurnResponse,
} from '@/types';
import * as agentApi from '@/api/agent';
import type { SessionMeta } from '@/api/agent';
import { useBookingStore } from '@/stores/booking';
import { useAuthStore } from '@/stores/auth';
import { progressFromDraft } from '@/utils/bookingProgress';
import { getCurrentLocation } from '@/utils/location';

export interface ChatMessage {
  id: string;
  role: 'user' | 'assistant' | 'system';
  text: string;
  cards?: AgentCardVO[];
  loading?: boolean;
}

interface AgentState {
  open: boolean;
  messages: ChatMessage[];
  progress: AgentProgress | null;
  sending: boolean;
  // 会话管理
  sessions: SessionMeta[];
  currentSessionId: string | null;
  historyLoading: boolean;
  hasMoreHistory: boolean;
  historyOffset: number;

  openDrawer: (opts?: { message?: string }) => void;
  closeDrawer: () => void;
  syncProgressFromDraft: () => void;
  sendMessage: (message: string) => Promise<void>;
  clickCardAction: (opts: {
    cardId: string;
    actionId: string;
    itemId?: string;
    draftPatch?: Record<string, unknown>;
  }) => Promise<AgentTurnResponse | null>;
  applyTurn: (res: AgentTurnResponse) => void;
  reset: () => void;
  // 会话管理方法
  loadSessions: () => Promise<void>;
  createNewSession: () => Promise<void>;
  switchSession: (sessionId: string) => Promise<void>;
  loadHistory: (sessionId: string) => Promise<void>;
  loadMoreHistory: () => Promise<void>;
}

let msgSeq = 0;
function mid() {
  return `msg_${++msgSeq}_${Date.now()}`;
}

function greetingForDraft(draft: ReturnType<typeof useBookingStore.getState>['draft']): string {
  if (draft?.movieId && draft.filmTitle) {
    if (draft.cinemaId && draft.showId) {
      return `已带上《${draft.filmTitle}》的购票草稿，我们可以继续选座或调整场次。`;
    }
    if (draft.cinemaId) {
      return `已选《${draft.filmTitle}》，接下来帮你挑场次？`;
    }
    return `已选《${draft.filmTitle}》，不用重选片。直接告诉我影院偏好，或说「附近影院」。`;
  }
  if (draft?.movieId) {
    return '草稿里已有影片，我们从选影院继续。可以说「附近影院」或影院名。';
  }
  if (draft?.cinemaId) {
    return '已选好影院。告诉我想看的影片或类型，我帮你往下排。';
  }
  return '你好！想看点什么？可以说「周末看喜剧」或直接告诉我片名。';
}

export const useAgentStore = create<AgentState>((set, get) => ({
  open: false,
  messages: [],
  progress: null,
  sending: false,
  sessions: [],
  currentSessionId: null,
  historyLoading: false,
  hasMoreHistory: false,
  historyOffset: 0,

  syncProgressFromDraft: () => {
    const draft = useBookingStore.getState().draft;
    set({ progress: progressFromDraft(draft) });
  },

  openDrawer: (opts) => {
    useBookingStore.getState().setAgentPaused(true);
    const init = async () => {
      const booking = useBookingStore.getState();
      // 主界面/其他页面打开时，从选片开始：不沿用历史手动草稿。
      // 仅当在手动购票流程（/booking/*）中打开才同步当前草稿。
      const pathname = typeof window !== 'undefined' ? window.location.pathname : '';
      const inBookingFlow = pathname.startsWith('/booking');
      const draft = inBookingFlow
        ? booking.draft || (await booking.ensureSession())
        : null;

      if (draft) {
        try {
          await booking.hydrateFromServer(draft.sessionId);
        } catch {
          /* keep local */
        }
      }
      // 主界面/其他页面打开时从选片开始：清空手动购票草稿，不沿用历史选择，
      // 避免残留的《封神第二部》选座状态导致 progress 停留在选座阶段
      if (!inBookingFlow) {
        useBookingStore.setState({ draft: null });
      }
      const latest = useBookingStore.getState().draft || draft || null;
      const progress = progressFromDraft(latest);

      // 加载会话列表
      await get().loadSessions();
      const sessions = get().sessions;

      set({ open: true, progress });

      if (sessions.length > 0) {
        // 进入用户最近的一个会话（保留历史上下文）
        await get().switchSession(sessions[0].sessionId);
      } else {
        // 没有会话，新建一个
        await get().createNewSession();
      }

      if (opts?.message) {
        void get().sendMessage(opts.message);
      }
    };
    void init();
  },

  closeDrawer: () => {
    useBookingStore.getState().setAgentPaused(false);
    void useBookingStore.getState().hydrateFromServer().catch(() => {
      /* 拦截器已提示 */
    });
    set({ open: false });
  },

  applyTurn: (res) => {
    useBookingStore.getState().applyAgentDraft(res.draft);
    const progress = res.progress?.steps?.length
      ? res.progress
      : progressFromDraft(res.draft);
    set((s) => {
      const messages = [
        ...s.messages.filter((m) => !m.loading),
        {
          id: mid(),
          role: 'assistant' as const,
          text: res.replyText,
          cards: res.cards,
        },
      ];
      // 更新 currentSessionId
      const currentSessionId = res.draft.sessionId || s.currentSessionId;
      return { progress, messages, currentSessionId };
    });
    if (res.needLogin) {
      void useAuthStore.getState().openLoginModal();
    }
    // 刷新会话列表（更新 lastMessageAt）
    void get().loadSessions();
  },

  sendMessage: async (message) => {
    // 确保有 currentSessionId
    let sessionId = get().currentSessionId;
    if (!sessionId) {
      await get().createNewSession();
      sessionId = get().currentSessionId;
      if (!sessionId) return;
    }

    set((s) => ({
      sending: true,
      messages: [
        ...s.messages,
        { id: mid(), role: 'user', text: message },
        { id: mid(), role: 'assistant', text: '规划中…', loading: true },
      ],
    }));
    try {
      const loc = await getCurrentLocation();
      const bookingDraft = useBookingStore.getState().draft;
      const res = await agentApi.postTurn({
        sessionId,
        message,
        latitude: loc?.latitude,
        longitude: loc?.longitude,
        clientDraft: bookingDraft || undefined,
        clientDraftVersion: bookingDraft?.version,
      });
      get().applyTurn(res);
    } catch (e) {
      set((s) => ({
        messages: [
          ...s.messages.filter((m) => !m.loading),
          {
            id: mid(),
            role: 'assistant',
            text: e instanceof Error ? e.message : '出错了，请稍后再试',
          },
        ],
      }));
    } finally {
      set({ sending: false });
    }
  },

  clickCardAction: async (opts) => {
    const sessionId = get().currentSessionId;
    if (!sessionId) return null;
    set({ sending: true });
    set((s) => ({
      messages: [
        ...s.messages,
        { id: mid(), role: 'assistant', text: '处理中…', loading: true },
      ],
    }));
    try {
      const loc = await getCurrentLocation();
      const bookingDraft = useBookingStore.getState().draft;
      const res = await agentApi.postTurn({
        sessionId,
        cardAction: opts,
        latitude: loc?.latitude,
        longitude: loc?.longitude,
        clientDraft: bookingDraft || undefined,
        clientDraftVersion: bookingDraft?.version,
      });
      get().applyTurn(res);
      return res;
    } catch (e) {
      set((s) => ({
        messages: [
          ...s.messages.filter((m) => !m.loading),
          {
            id: mid(),
            role: 'assistant',
            text: e instanceof Error ? e.message : '操作失败',
          },
        ],
      }));
      return null;
    } finally {
      set({ sending: false });
    }
  },

  reset: () =>
    set({
      messages: [],
      progress: null,
      currentSessionId: null,
      sessions: [],
      historyOffset: 0,
      hasMoreHistory: false,
    }),

  // ---------- 会话管理 ----------

  loadSessions: async () => {
    try {
      const sessions = await agentApi.getSessions();
      set({ sessions });
    } catch {
      /* 拦截器已提示 */
    }
  },

  createNewSession: async () => {
    // 清空手动购票草稿：避免旧草稿作为 clientDraft 同步给新会话，
    // 导致新对话继承了上一个会话的 bookingdraft（影片/影院等残留状态）
    useBookingStore.setState({ draft: null });
    try {
      const session = await agentApi.createSession();
      set((s) => ({
        sessions: [session, ...s.sessions],
        currentSessionId: session.sessionId,
        messages: [
          {
            id: mid(),
            role: 'assistant',
            text: '新对话已开启。想看点什么？可以说「周末看喜剧」或直接告诉我片名。',
          },
        ],
        historyOffset: 0,
        hasMoreHistory: false,
      }));
    } catch {
      // fallback: 用本地生成的 session_id
      const sid = `sess_local_${Date.now()}`;
      set({
        currentSessionId: sid,
        messages: [
          {
            id: mid(),
            role: 'assistant',
            text: '新对话已开启。想看点什么？',
          },
        ],
        historyOffset: 0,
        hasMoreHistory: false,
      });
    }
  },

  switchSession: async (sessionId) => {
    set({ currentSessionId: sessionId, historyLoading: true });
    await get().loadHistory(sessionId);
    set({ historyLoading: false });
  },

  loadHistory: async (sessionId) => {
    try {
      const history = await agentApi.getHistory(sessionId, 5, 0);
      const messages: ChatMessage[] = history.map((m) => ({
        id: mid(),
        role: (m.role === 'user' ? 'user' : 'assistant') as 'user' | 'assistant',
        text: m.content,
        cards: (m as unknown as { cards?: AgentCardVO[] }).cards || undefined,
      }));
      set({
        messages:
          messages.length > 0
            ? messages
            : [
                {
                  id: mid(),
                  role: 'assistant',
                  text: '想看点什么？可以说「周末看喜剧」或直接告诉我片名。',
                },
              ],
        historyOffset: history.length,
        hasMoreHistory: history.length === 5,
      });
    } catch {
      set({
        messages: [
          {
            id: mid(),
            role: 'assistant',
            text: '想看点什么？可以说「周末看喜剧」或直接告诉我片名。',
          },
        ],
        historyOffset: 0,
        hasMoreHistory: false,
      });
    }
  },

  loadMoreHistory: async () => {
    const sessionId = get().currentSessionId;
    if (!sessionId || get().historyLoading || !get().hasMoreHistory) return;

    set({ historyLoading: true });
    const offset = get().historyOffset;
    try {
      const history = await agentApi.getHistory(sessionId, 5, offset);
      const olderMessages: ChatMessage[] = history.map((m) => ({
        id: mid(),
        role: (m.role === 'user' ? 'user' : 'assistant') as 'user' | 'assistant',
        text: m.content,
        cards: (m as unknown as { cards?: AgentCardVO[] }).cards || undefined,
      }));
      set((s) => ({
        messages: [...olderMessages, ...s.messages],
        historyOffset: offset + history.length,
        hasMoreHistory: history.length === 5,
      }));
    } catch {
      set({ hasMoreHistory: false });
    } finally {
      set({ historyLoading: false });
    }
  },
}));
