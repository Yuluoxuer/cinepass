package com.cinepass.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PhoneMask 边界：脱敏长度阈值、手机号格式判定。
 */
class PhoneMaskTest {

    @Test
    void mask_null_shouldReturnNull() {
        assertThat(PhoneMask.mask(null)).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "1", "123456"})
    void mask_shorterThan7_shouldReturnOriginal(String phone) {
        assertThat(PhoneMask.mask(phone)).isEqualTo(phone);
    }

    @Test
    void mask_exact7_shouldMaskMiddle() {
        // length >= 7：前3 + **** + 后4 → 7位时中间重叠可接受
        assertThat(PhoneMask.mask("1234567")).isEqualTo("123****4567");
    }

    @Test
    void mask_standard11_shouldKeepHead3Tail4() {
        assertThat(PhoneMask.mask("13812345678")).isEqualTo("138****5678");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"123", "01234567890", "23812345678", "1381234567", "138123456789"})
    void isMobile_invalid_shouldBeFalse(String account) {
        assertThat(PhoneMask.isMobile(account)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"13000000000", "15912345678", "19899999999"})
    void isMobile_validMainland_shouldBeTrue(String account) {
        assertThat(PhoneMask.isMobile(account)).isTrue();
    }
}
