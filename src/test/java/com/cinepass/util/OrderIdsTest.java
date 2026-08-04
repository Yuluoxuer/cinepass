package com.cinepass.util;

import org.junit.jupiter.api.Test;

import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderIdsTest {

    private static final Pattern PATTERN = Pattern.compile("^o[0-9a-f]{32}$");

    @Test
    void next_shouldMatchOPlusUuid7Hex() {
        String a = OrderIds.next();
        String b = OrderIds.next();
        assertTrue(PATTERN.matcher(a).matches(), a);
        assertTrue(PATTERN.matcher(b).matches(), b);
        assertNotEquals(a, b);
        assertTrue(a.length() == 33);
    }
}
