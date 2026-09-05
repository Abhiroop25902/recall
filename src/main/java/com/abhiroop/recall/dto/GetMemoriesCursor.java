package com.abhiroop.recall.dto;


import java.time.Instant;

public record GetMemoriesCursor(
        Instant afterCreatedAt,
        String afterId
) {

    public GetMemoriesCursor {
        if (afterCreatedAt == null || afterId == null || afterId.isBlank()) {
            throw new IllegalArgumentException(
                    "cursor requires afterCreatedAt and a nonblank afterId");
        }
    }
}
