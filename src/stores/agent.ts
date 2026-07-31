import { create } from 'zustand';
import type {
  AgentCardVO,
  AgentProgress,
  AgentTurnResponse,
} from '@/types';
import * as agentApi from '@/api/agent';
import { useBookingStore } from '@/stores/booking';
import { useAuthStore } from '@/stores/auth';
import { progressFromDraft } from '@/utils/bookingProgress';

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

  syncProgressFromDraft: () => {
    const draft = useBookingStore.getState().draft;
    set({ progress: progressFromDraft(draft) });
  },

  openDrawer: (opts) => {
    useBookingStore.getState().setAgentPaused(true);
    const openWithDraft = async () => {
      const booking = useBookingStore.getState();
      const draft = booking.draft || (await booking.ensureSession());
      // 模式切换：以服务端 Draft 为准，保护已完备步骤
      try {
        await booking.hydrateFromServer(draft.sessionId);
      } catch {
        /* keep local */
      }
      const latest = useBookingStore.getState().draft || draft;
      const progress = progressFromDraft(latest);

      set((s) => {
        if (s.messages.length === 0) {
          return {
            open: true,
            messages: [
              {
                id: mid(),
                role: 'assistant',
                text: greetingForDraft(latest),
              },
            ],
            progress,
          };
        }
        return { open: true, progress };
      });

      if (opts?.message) {
        void get().sendMessage(opts.message);
      }
    };
    void openWithDraft();
  },

  closeDrawer: () => {
    useBookingStore.getState().setAgentPaused(false);
    void useBookingStore.getState().hydrateFromServer();
    set({ open: false });
  },

  applyTurn: (res) => {
    useBookingStore.getState().applyAgentDraft(res.draft);
    // progress 以服务端为准；若缺字段则按 Draft 完备度回退
    const progress = res.progress?.steps?.length
      ? res.progress
      : progressFromDraft(res.draft);
    set((s) => ({
      progress,
      messages: [
        ...s.messages.filter((m) => !m.loading),
        {
          id: mid(),
          role: 'assistant',
          text: res.replyText,
          cards: res.cards,
        },
      ],
    }));
    if (res.needLogin) {
      void useAuthStore.getState().openLoginModal();
    }
  },

  sendMessage: async (message) => {
    const booking = useBookingStore.getState();
    const draft = booking.draft || (await booking.ensureSession());
    set((s) => ({
      sending: true,
      messages: [
        ...s.messages,
        { id: mid(), role: 'user', text: message },
        { id: mid(), role: 'assistant', text: '规划中…', loading: true },
      ],
    }));
    try {
      const res = await agentApi.postTurn({
        sessionId: draft.sessionId,
        message,
        clientDraftVersion: draft.version,
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
    const booking = useBookingStore.getState();
    const draft = booking.draft || (await booking.ensureSession());
    set({ sending: true });
    set((s) => ({
      messages: [
        ...s.messages,
        { id: mid(), role: 'assistant', text: '处理中…', loading: true },
      ],
    }));
    try {
      const res = await agentApi.postTurn({
        sessionId: draft.sessionId,
        clientDraftVersion: draft.version,
        cardAction: opts,
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

  reset: () => set({ messages: [], progress: null }),
}));
