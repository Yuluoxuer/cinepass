import { create } from 'zustand';
import type { BookingDraft, BookingState, SeatNameMap } from '@/types';
import * as draftApi from '@/api/draft';
import { firstIncompleteStep, progressFromDraft } from '@/utils/bookingProgress';
import { useAgentStore } from '@/stores/agent';

const SESSION_KEY = 'miaoyu_sessionId';

function emptyDraft(sessionId: string): BookingDraft {
  return {
    sessionId,
    source: 'manual',
    state: 'Idle',
    count: 2,
    seatIds: [],
    version: 1,
  };
}

function syncAgentProgress(draft: BookingDraft) {
  if (useAgentStore.getState().open) {
    useAgentStore.setState({ progress: progressFromDraft(draft) });
  }
}

interface BookingStateStore {
  draft: BookingDraft | null;
  seatNameById: SeatNameMap;
  agentPaused: boolean;
  ensuring: boolean;
  setAgentPaused: (v: boolean) => void;
  setSeatNameById: (map: SeatNameMap) => void;
  mergeSeatNames: (map: SeatNameMap) => void;
  ensureSession: () => Promise<BookingDraft>;
  hydrateFromServer: (sessionId?: string) => Promise<BookingDraft>;
  patchLocal: (patch: DraftPatchInput, opts?: { debounce?: boolean }) => Promise<BookingDraft>;
  applyAgentDraft: (draft: BookingDraft) => void;
  rollbackDependent: (from: BookingState) => void;
}

type DraftPatchInput = NonNullable<Parameters<typeof draftApi.updateDraft>[1]>['patch'];

let debounceTimer: ReturnType<typeof setTimeout> | null = null;
let pendingPatch: DraftPatchInput | null = null;

const DEPENDENT_FIELDS: Record<string, (keyof BookingDraft)[]> = {
  SelectMovie: ['cinemaId', 'showId', 'seatIds', 'lockId', 'orderId', 'expireAt'],
  SelectCinema: ['showId', 'seatIds', 'lockId', 'orderId', 'expireAt'],
  SelectShow: ['seatIds', 'lockId', 'orderId', 'expireAt'],
  SelectSeat: ['lockId', 'orderId', 'expireAt'],
};

function withDerivedState(base: BookingDraft, patch: DraftPatchInput): BookingDraft {
  const merged = { ...base, ...patch } as BookingDraft;
  if (patch.state == null) {
    merged.state = firstIncompleteStep(merged);
  }
  return merged;
}

export const useBookingStore = create<BookingStateStore>((set, get) => ({
  draft: null,
  seatNameById: {},
  agentPaused: false,
  ensuring: false,

  setAgentPaused: (v) => set({ agentPaused: v }),

  setSeatNameById: (map) => set({ seatNameById: map }),

  mergeSeatNames: (map) =>
    set((s) => ({ seatNameById: { ...s.seatNameById, ...map } })),

  ensureSession: async () => {
    const existing = get().draft;
    if (existing) return existing;
    if (get().ensuring) {
      await new Promise((r) => setTimeout(r, 50));
      return get().ensureSession();
    }
    set({ ensuring: true });
    try {
      const sid = localStorage.getItem(SESSION_KEY);
      if (sid) {
        try {
          const d = await draftApi.getDraft(sid);
          localStorage.setItem(SESSION_KEY, d.sessionId);
          set({ draft: d });
          return d;
        } catch {
          /* create new */
        }
      }
      const created = await draftApi.createDraft({ source: 'manual' });
      localStorage.setItem(SESSION_KEY, created.sessionId);
      set({ draft: created });
      return created;
    } finally {
      set({ ensuring: false });
    }
  },

  hydrateFromServer: async (sessionId) => {
    const sid = sessionId || get().draft?.sessionId || localStorage.getItem(SESSION_KEY);
    if (!sid) return get().ensureSession();
    const d = await draftApi.getDraft(sid);
    localStorage.setItem(SESSION_KEY, d.sessionId);
    set({ draft: d });
    syncAgentProgress(d);
    return d;
  },

  patchLocal: async (patch, opts) => {
    const draft = get().draft || (await get().ensureSession());
    if (get().agentPaused && opts?.debounce !== false) {
      const merged = withDerivedState(draft, patch);
      merged.version = draft.version;
      set({ draft: merged });
      syncAgentProgress(merged);
      return merged;
    }

    const doPut = async (p: DraftPatchInput) => {
      const current = get().draft || draft;
      const bodyPatch = { ...p };
      if (bodyPatch.state == null) {
        bodyPatch.state = firstIncompleteStep({ ...current, ...p } as BookingDraft);
      }
      const updated = await draftApi.updateDraft(current.sessionId, {
        version: current.version,
        patch: bodyPatch,
      });
      set({ draft: updated });
      syncAgentProgress(updated);
      return updated;
    };

    if (opts?.debounce === false) {
      return doPut(patch);
    }

    pendingPatch = { ...(pendingPatch || {}), ...patch };
    const optimistic = withDerivedState(draft, pendingPatch);
    set({ draft: optimistic });
    syncAgentProgress(optimistic);

    return new Promise((resolve, reject) => {
      if (debounceTimer) clearTimeout(debounceTimer);
      debounceTimer = setTimeout(async () => {
        const p = pendingPatch;
        pendingPatch = null;
        try {
          resolve(await doPut(p || patch));
        } catch (e) {
          reject(e);
        }
      }, 300);
    });
  },

  applyAgentDraft: (draft) => {
    localStorage.setItem(SESSION_KEY, draft.sessionId);
    set({ draft });
    syncAgentProgress(draft);
  },

  rollbackDependent: (from) => {
    const draft = get().draft;
    if (!draft) return;
    const keys = DEPENDENT_FIELDS[from] || [];
    const next = { ...draft };
    for (const k of keys) {
      if (k === 'seatIds') next.seatIds = [];
      else delete (next as Record<string, unknown>)[k];
    }
    next.state = firstIncompleteStep(next);
    set({ draft: next });
    syncAgentProgress(next);
  },
}));

export function getSessionId(): string | null {
  return (
    useBookingStore.getState().draft?.sessionId ||
    localStorage.getItem(SESSION_KEY)
  );
}

export { emptyDraft };
