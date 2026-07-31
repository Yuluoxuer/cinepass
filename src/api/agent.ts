import { post } from './client';
import type { AgentTurnRequest, AgentTurnResponse } from '@/types';

export function postTurn(body: AgentTurnRequest) {
  return post<AgentTurnResponse>('/agent/turns', body);
}
