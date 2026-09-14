package com.abhiroop.recall.dto;

import org.jspecify.annotations.Nullable;

import java.util.List;

public record GetMemoriesPage(
        List<MemoryResponseDto> memories,
        @Nullable GetMemoriesCursor nextCursor
) {
}
