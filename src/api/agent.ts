import { get, post } from './client';
import type { AgentCardVO, AgentTurnRequest, AgentTurnResponse } from '@/types';

/**
 * Agent 对话与会话管理：当前接入 agent4（意图识别 → 提取草稿 → 追问补齐 → 确认 → 锁座支付）。
 * 备选：'/booking'（booking_graph 状态机）、'/agent'（agent2 create_react_agent）、'/agent3'（监督者模式）。
 */
const BASE = '/agent4';
const TURN_URL = `${BASE}/turns`;

export function postTurn(body: AgentTurnRequest) {
  return post<AgentTurnResponse>(TURN_URL, body);
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
  /** assistant 消息附带的历史动态卡片（恢复会话时重新渲染） */
  cards?: AgentCardVO[];
}

export function getSessions() {
  return get<SessionMeta[]>(`${BASE}/sessions`);
}

export function createSession(title?: string) {
  return post<SessionMeta>(`${BASE}/sessions`, { title: title || null });
}

export function getHistory(sessionId: string, limit = 5, offset = 0) {
  return get<HistoryMessage[]>(`${BASE}/sessions/${sessionId}/messages`, { limit, offset });
}
