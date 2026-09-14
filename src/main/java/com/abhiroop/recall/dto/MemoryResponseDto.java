package com.abhiroop.recall.dto;

import com.abhiroop.recall.entity.Memory;
import lombok.Builder;

import java.time.Instant;

@Builder
public record MemoryResponseDto(
        String id,
        String appId,
        String text,
        Instant createdAt
) {

    public static MemoryResponseDto fromMemory(Memory memory) {
        final var createdAt = memory.createdAt();
        return MemoryResponseDto.builder()
                .id(memory.id())
                .appId(memory.appId())
                .text(memory.text())
                .createdAt(
                        Instant.ofEpochSecond(createdAt.getSeconds(), createdAt.getNanos())
                )
                .build();
    }
}
