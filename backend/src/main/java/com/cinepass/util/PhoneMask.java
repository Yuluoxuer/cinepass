package com.cinepass.util;

/**
 * 手机号脱敏与格式判断。
 */
public final class PhoneMask {

    private PhoneMask() {
    }

    /** 脱敏：保留前 3 后 4，中间替换为 ****；过短则原样返回 */
    public static String mask(String phone) {
        if (phone == null || phone.length() < 7) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    /** 判断字符串是否为大陆 11 位手机号 */
    public static boolean isMobile(String account) {
        return account != null && account.matches("^1[3-9]\\d{9}$");
    }
}
