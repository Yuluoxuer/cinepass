export type LoginModalMode = 'login' | 'register';

/** 注册表单（后端 RegisterDTO：昵称 1-64、手机号、密码 8-64） */
export interface RegisterForm {
  nickname: string;
  phone: string;
  password: string;
  confirmPassword: string;
}
