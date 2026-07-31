import { create } from 'zustand';

const STORAGE_KEY = 'miaoyu_use_mock';

function readInitial(): boolean {
  if (typeof window === 'undefined') return true;
  const raw = localStorage.getItem(STORAGE_KEY);
  if (raw === null) return true;
  return raw === '1' || raw === 'true';
}

interface MockState {
  enabled: boolean;
  setEnabled: (enabled: boolean) => void;
  toggle: () => void;
}

export const useMockStore = create<MockState>((set, get) => ({
  enabled: readInitial(),
  setEnabled: (enabled) => {
    localStorage.setItem(STORAGE_KEY, enabled ? '1' : '0');
    set({ enabled });
  },
  toggle: () => {
    const next = !get().enabled;
    localStorage.setItem(STORAGE_KEY, next ? '1' : '0');
    set({ enabled: next });
  },
}));

export function getUseMock(): boolean {
  return useMockStore.getState().enabled;
}
