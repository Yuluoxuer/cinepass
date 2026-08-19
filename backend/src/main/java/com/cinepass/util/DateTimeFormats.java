package com.cinepass.util;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * 前后端统一时间契约。
 * <ul>
 *   <li>瞬时（开场/过期/下单等）：ISO-8601 带偏移，业务固定东八区，如 {@code 2026-08-11T19:30:00+08:00}</li>
 *   <li>日历日：{@code yyyy-MM-dd}</li>
 *   <li>批量排片时刻：{@code HH:mm}</li>
 * </ul>
 */
public final class DateTimeFormats {

    /** 业务时区（影院墙钟） */
    public static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    /** 固定 +08:00（与 {@link #ZONE} 在无夏令时下等价） */
    public static final ZoneOffset OFFSET = ZoneOffset.ofHours(8);

    /** 瞬时 API 序列化 */
    public static final DateTimeFormatter ISO_OFFSET = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    /** 日历日 */
    public static final DateTimeFormatter DATE = DateTimeFormatter.ISO_LOCAL_DATE;

    /** 批量排片时刻 */
    public static final DateTimeFormatter TIME_HM = DateTimeFormatter.ofPattern("HH:mm");

    private DateTimeFormats() {
    }

    /** 当前东八区时刻 */
    public static OffsetDateTime now() {
        return OffsetDateTime.now(OFFSET);
    }

    /** OffsetDateTime → API 字符串；{@code null} 安全 */
    public static String format(OffsetDateTime t) {
        return t == null ? null : ISO_OFFSET.format(t);
    }
}
