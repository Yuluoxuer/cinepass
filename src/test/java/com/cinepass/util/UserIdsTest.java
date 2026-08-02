package com.cinepass.util;

import org.junit.jupiter.api.Test;

import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserIdsTest {

    private static final Pattern PATTERN = Pattern.compile("^u[0-9a-f]{32}$");

    @Test
    void next_shouldMatchUPlusUuid7Hex() {
        String a = UserIds.next();
        String b = UserIds.next();
        assertTrue(PATTERN.matcher(a).matches(), a);
        assertTrue(PATTERN.matcher(b).matches(), b);
        assertNotEquals(a, b);
    }
}
