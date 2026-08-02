package com.cinepass.serializer;

import lombok.extern.slf4j.Slf4j;

/**
 * 字段权限检查工具（模板桩实现 — 默认全部穿透）。
 */
@Slf4j
public final class FieldPermissionSerializer {

    private FieldPermissionSerializer() {
    }

    public static void init() {
        log.debug("FieldPermissionSerializer stub init");
    }

    public static FieldRule getFieldRule(String tableName, String fieldName) {
        return FieldRule.PASS_THROUGH;
    }

    public static String mask(String value, String fieldName) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        int len = value.length();
        if (len <= 2) {
            return value;
        }
        int maskLen = len / 2;
        int keepLen = (len - maskLen) / 2;
        return value.substring(0, keepLen)
                + repeat(maskLen)
                + value.substring(keepLen + maskLen);
    }

    private static String repeat(int count) {
        if (count <= 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder(count);
        for (int i = 0; i < count; i++) {
            sb.append('*');
        }
        return sb.toString();
    }

    public enum FieldRule {
        PASS_THROUGH,
        MASKED,
        HIDDEN
    }
}
