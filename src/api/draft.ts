import { get, post, put } from './client';
import type { BookingDraft, DraftPatchBody } from '@/types';

export function createDraft(body?: { source?: string; movieId?: string }) {
  return post<BookingDraft>('/booking-drafts', body || {});
}

export function getDraft(sessionId: string) {
  return get<BookingDraft>(`/booking-drafts/${sessionId}`);
}

export function updateDraft(sessionId: string, body: DraftPatchBody) {
  return put<BookingDraft>(`/booking-drafts/${sessionId}`, body);
}
