import { create } from 'zustand';
import { ApiError, type BookingDraft, type BookingState, type SeatNameMap } from '@/types';
import * as draftApi from '@/api/draft';
import { firstIncompleteStep, progressFromDraft } from '@/utils/bookingProgress';
import { useAgentStore } from '@/stores/agent';
import { getAccessToken, useAuthStore } from '@/stores/auth';

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
      try {
        const created = await draftApi.createDraft({ source: 'manual' });
        localStorage.setItem(SESSION_KEY, created.sessionId);
        set({ draft: created });
        return created;
      } catch {
        // 拦截器已 toast；本地占位避免布局崩溃，不写入 localStorage
        const local = emptyDraft(`sess_local_${Date.now()}`);
        set({ draft: local });
        return local;
      }
    } finally {
      set({ ensuring: false });
    }
  },

  hydrateFromServer: async (sessionId) => {
    const sid = sessionId || get().draft?.sessionId || localStorage.getItem(SESSION_KEY);
    if (!sid) return get().ensureSession();
    try {
      const d = await draftApi.getDraft(sid);
      localStorage.setItem(SESSION_KEY, d.sessionId);
      set({ draft: d });
      syncAgentProgress(d);
      return d;
    } catch {
      return get().ensureSession();
    }
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

      // draft 已绑定用户但当前无 token → 先引导登录再继续修改
      if (current.userId && !getAccessToken()) {
        const ok = await useAuthStore.getState().openLoginModal();
        if (!ok) {
          // 用户取消登录，仅保留乐观态
          return get().draft || withDerivedState(current, bodyPatch);
        }
        // 登录成功，继续发请求（此时有了新 token）
      }

      let updated: BookingDraft;
      try {
        updated = await draftApi.updateDraft(current.sessionId, {
          version: current.version,
          patch: bodyPatch,
        });
      } catch (error) {
        // token 过期/无效（401）：引导登录后重试一次
        if (error instanceof ApiError && (error.code === 40101 || error.code === 401 || error.httpStatus === 401)) {
          const ok = await useAuthStore.getState().openLoginModal();
          if (!ok) {
            return get().draft || withDerivedState(current, bodyPatch);
          }
          try {
            updated = await draftApi.updateDraft(current.sessionId, {
              version: current.version,
              patch: bodyPatch,
            });
          } catch {
            return get().draft || withDerivedState(current, bodyPatch);
          }
        } else if (!(error instanceof ApiError) || error.errorCode !== 'DRAFT_CONFLICT') {
          // Draft 归属冲突（不同用户）：丢弃本地 session，重建后再试一次
          if (error instanceof ApiError && (error.code === 40301 || error.errorCode === 'FORBIDDEN')) {
            localStorage.removeItem(SESSION_KEY);
            set({ draft: null });
            try {
              const fresh = await get().ensureSession();
              updated = await draftApi.updateDraft(fresh.sessionId, {
                version: fresh.version,
                patch: bodyPatch,
              });
              set({ draft: updated });
              syncAgentProgress(updated);
              return updated;
            } catch {
              return get().draft || withDerivedState(current, bodyPatch);
            }
          }
          // 拦截器已可视化提示；保留乐观态，不向上抛避免 Umi 红屏
          return get().draft || withDerivedState(current, bodyPatch);
        } else {
          try {
            // Draft 使用 CAS。冲突后先以服务端完整状态为准，再重放本次用户操作。
            const serverDraft = await draftApi.getDraft(current.sessionId);
            const retryPatch = { ...bodyPatch };
            if (retryPatch.state == null) {
              retryPatch.state = firstIncompleteStep({ ...serverDraft, ...retryPatch } as BookingDraft);
            }
            updated = await draftApi.updateDraft(serverDraft.sessionId, {
              version: serverDraft.version,
              patch: retryPatch,
            });
          } catch {
            return get().draft || withDerivedState(current, bodyPatch);
          }
        }
      }
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

    return new Promise((resolve) => {
      if (debounceTimer) clearTimeout(debounceTimer);
      debounceTimer = setTimeout(async () => {
        const p = pendingPatch;
        pendingPatch = null;
        try {
          resolve(await doPut(p || patch));
        } catch {
          resolve(get().draft || optimistic);
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
