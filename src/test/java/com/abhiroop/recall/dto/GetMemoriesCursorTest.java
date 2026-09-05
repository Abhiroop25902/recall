package com.abhiroop.recall.dto;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class GetMemoriesCursorTest {

    @Test
    void acceptsCompleteCursorWithoutLosingPrecision() {
        var instant = Instant.parse("2026-09-05T12:34:56.123456789Z");
        var cursor = new GetMemoriesCursor(instant, "memory-id");

        assertEquals(instant, cursor.afterCreatedAt());
        assertEquals("memory-id", cursor.afterId());
    }

    @Test
    void rejectsMissingTimestamp() {
        assertThrows(IllegalArgumentException.class, () -> new GetMemoriesCursor(null, "memory-id"));
        assertThrows(IllegalArgumentException.class, () -> new GetMemoriesCursor(null, null));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n"})
    void rejectsMissingOrBlankId(String id) {
        assertThrows(IllegalArgumentException.class, () -> new GetMemoriesCursor(Instant.EPOCH, id));
    }
}
