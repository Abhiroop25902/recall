package com.abhiroop.recall.dto;

import com.abhiroop.recall.entity.Memory;
import org.jspecify.annotations.Nullable;


import java.util.List;

public record GetMemoriesPage(
        List<Memory> memories,
        @Nullable GetMemoriesCursor nextCursor
) {
}
