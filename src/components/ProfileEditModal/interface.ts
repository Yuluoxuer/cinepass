import type { PreferRow, PreferSide } from '@/types';

export interface ProfileEditModalProps {
  open: boolean;
  onClose: () => void;
  onSaved?: () => void;
}

/** 观影偏好编辑表单（对齐后端 ProfileUpdateDTO；排位/侧向可留空表示不偏好） */
export interface ProfileEditForm {
  preferGenres: string[];
  preferRow?: PreferRow;
  preferSide?: PreferSide;
}
