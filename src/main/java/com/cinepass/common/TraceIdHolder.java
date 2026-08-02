package com.cinepass.common;

public final class TraceIdHolder {

    private static final ThreadLocal<String> TRACE = new ThreadLocal<String>();

    private TraceIdHolder() {
    }

    public static void set(String traceId) {
        TRACE.set(traceId);
    }

    public static String get() {
        return TRACE.get();
    }

    public static void clear() {
        TRACE.remove();
    }
}
