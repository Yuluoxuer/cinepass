import { get, put } from './client';
import type { ProfileVO, ProfileUpdateBody } from '@/types';

/** 当前用户观影偏好（后端 ProfileVO） */
export function getProfile() {
  return get<ProfileVO>('/me/profile');
}

/** 更新当前用户观影偏好 */
export function updateProfile(body: ProfileUpdateBody) {
  return put<ProfileVO>('/me/profile', body);
}
