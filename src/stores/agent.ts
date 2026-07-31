import { create } from 'zustand';
import type {
  AgentCardVO,
  AgentProgress,
  AgentTurnResponse,
  BookingDraft,
} from '@/types';
import * as agentApi from '@/api/agent';
import { useBookingStore } from '@/stores/booking';
import { useAuthStore } from '@/stores/auth';

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

const DEFAULT_PROGRESS: AgentProgress = {
  steps: ['选片', '影院', '场次', '选座', '支付'],
  currentIndex: 0,
  state: 'Idle',
};

export const useAgentStore = create<AgentState>((set, get) => ({
  open: false,
  messages: [],
  progress: null,
  sending: false,

  openDrawer: (opts) => {
    useBookingStore.getState().setAgentPaused(true);
    set((s) => {
      if (s.messages.length === 0) {
        return {
          open: true,
          messages: [
            {
              id: mid(),
              role: 'assistant',
              text: '你好！想看点什么？可以说「周末看喜剧」或直接告诉我片名。',
            },
          ],
          progress: s.progress || DEFAULT_PROGRESS,
        };
      }
      return { open: true };
    });
    if (opts?.message) {
      void get().sendMessage(opts.message);
    }
  },

  closeDrawer: () => {
    useBookingStore.getState().setAgentPaused(false);
    void useBookingStore.getState().hydrateFromServer();
    set({ open: false });
  },

  applyTurn: (res) => {
    useBookingStore.getState().applyAgentDraft(res.draft);
    set((s) => ({
      progress: res.progress,
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
