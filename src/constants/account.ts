/** 账号体系约束，与后端 RegisterDTO / PasswordChangeDTO 对齐 */

/** 昵称最大长度 */
export const NICKNAME_MAX_LEN = 64;

/** 密码最小长度 */
export const PASSWORD_MIN_LEN = 8;

/** 密码最大长度 */
export const PASSWORD_MAX_LEN = 64;

/** 大陆手机号：1 开头，第二位 3-9，共 11 位 */
export const PHONE_REGEX = /^1[3-9]\d{9}$/;
