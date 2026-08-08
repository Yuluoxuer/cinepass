import { get, post } from './client';
import type { AgentTurnRequest, AgentTurnResponse } from '@/types';

export function postTurn(body: AgentTurnRequest) {
  return post<AgentTurnResponse>('/agent/turns', body);
}

export interface SessionMeta {
  sessionId: string;
  userId: string;
  title: string | null;
  createdAt: string | null;
  lastMessageAt: string | null;
}

export interface HistoryMessage {
  role: string;
  content: string;
}

export function getSessions() {
  return get<SessionMeta[]>('/agent/sessions');
}

export function createSession(title?: string) {
  return post<SessionMeta>('/agent/sessions', { title: title || null });
}

export function getHistory(sessionId: string, limit = 5, offset = 0) {
  return get<HistoryMessage[]>(`/agent/sessions/${sessionId}/messages`, { limit, offset });
}
