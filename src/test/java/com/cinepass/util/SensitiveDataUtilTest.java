package com.cinepass.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 敏感数据脱敏工具测试
 */
class SensitiveDataUtilTest {

    @Test
    void maskPhoneShouldKeepFirst3AndLast4() {
        assertEquals("139****9000", SensitiveDataUtil.maskPhone("13912349000"));
    }

    @Test
    void maskPhoneShortOrNullShouldReturnAsIs() {
        assertNull(SensitiveDataUtil.maskPhone(null));
        assertEquals("123456", SensitiveDataUtil.maskPhone("123456"), "长度不足时原样返回");
    }

    @Test
    void maskIdCardShouldKeepFirst6AndLast4() {
        assertEquals("110101********0011", SensitiveDataUtil.maskIdCard("110101199001010011"));
    }

    @Test
    void maskEmailShouldKeepFirstCharAndDomain() {
        assertEquals("l***i@example.com", SensitiveDataUtil.maskEmail("lisi@example.com"));
        assertEquals("a***@example.com", SensitiveDataUtil.maskEmail("ab@example.com"));
    }

    @Test
    void maskEmailInvalidShouldReturnAsIs() {
        assertNull(SensitiveDataUtil.maskEmail(null));
        assertEquals("not-an-email", SensitiveDataUtil.maskEmail("not-an-email"));
    }

}
