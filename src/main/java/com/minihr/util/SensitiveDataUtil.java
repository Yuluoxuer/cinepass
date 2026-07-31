package com.minihr.util;

import org.springframework.util.StringUtils;

/**
 * 敏感数据脱敏工具类
 * <p>统一脱敏规则，后端统一脱敏，不依赖前端</p>
 */
public class SensitiveDataUtil {

    /** 手机号脱敏：保留前3位和后4位，中间用星号填充 */
    public static String maskPhone(String phone) {
        if (!StringUtils.hasText(phone) || phone.length() < 7) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    /** 身份证号脱敏：保留前6位和后4位，中间用星号填充 */
    public static String maskIdCard(String idCard) {
        if (!StringUtils.hasText(idCard) || idCard.length() < 10) {
            return idCard;
        }
        return idCard.substring(0, 6) + "********" + idCard.substring(idCard.length() - 4);
    }

    /** 邮箱脱敏：保留首字母和域名，中间用星号 */
    public static String maskEmail(String email) {
        if (!StringUtils.hasText(email) || !email.contains("@")) {
            return email;
        }
        int atIndex = email.indexOf('@');
        String prefix = email.substring(0, atIndex);
        String domain = email.substring(atIndex);
        if (prefix.length() <= 2) {
            return prefix.charAt(0) + "***" + domain;
        }
        return prefix.charAt(0) + "***" + prefix.charAt(prefix.length() - 1) + domain;
    }

    /** 银行卡号脱敏：仅保留后4位 */
    public static String maskBankAccount(String bankAccount) {
        if (!StringUtils.hasText(bankAccount) || bankAccount.length() <= 4) {
            return bankAccount;
        }
        int maskLen = bankAccount.length() - 4;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < maskLen; i++) {
            sb.append('*');
        }
        return sb.append(bankAccount.substring(maskLen)).toString();
    }

}
