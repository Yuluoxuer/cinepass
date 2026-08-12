package com.cinepass.util;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link DateTimeFormats} 契约：瞬时 ISO-8601 带 +08:00。
 */
class DateTimeFormatsTest {

    @Test
    void format_nullSafe() {
        assertNull(DateTimeFormats.format(null));
    }

    @Test
    void format_offsetIso() {
        OffsetDateTime t = OffsetDateTime.of(2026, 8, 11, 19, 30, 0, 0, ZoneOffset.ofHours(8));
        assertEquals("2026-08-11T19:30:00+08:00", DateTimeFormats.format(t));
    }

    @Test
    void now_usesPlusEight() {
        assertEquals(DateTimeFormats.OFFSET, DateTimeFormats.now().getOffset());
        assertTrue(DateTimeFormats.format(DateTimeFormats.now()).endsWith("+08:00"));
    }
}
