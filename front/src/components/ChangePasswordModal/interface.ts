export interface ChangePasswordModalProps {
  open: boolean;
  /** 当前登录用户账号（昵称或手机号）；改密接口需带 account 以定位用户 */
  account: string;
  onClose: () => void;
  onChanged?: () => void;
}

/** 修改密码表单 */
export interface ChangePasswordForm {
  oldPassword: string;
  newPassword: string;
  confirmPassword: string;
}
