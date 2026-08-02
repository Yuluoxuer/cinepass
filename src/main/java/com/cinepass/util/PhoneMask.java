package com.cinepass.util;

public final class PhoneMask {
    private PhoneMask() {}

    public static String mask(String phone) {
        if (phone == null || phone.length() < 7) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    public static boolean isMobile(String account) {
        return account != null && account.matches("^1[3-9]\\d{9}$");
    }
}
