package com.abhiroop.recall.mcp;

import com.abhiroop.recall.dto.GetMemoriesCursor;
import com.abhiroop.recall.dto.GetMemoriesPage;
import com.abhiroop.recall.dto.MemoryResponseDto;
import com.abhiroop.recall.service.MemoryService;
import com.abhiroop.recall.utils.ErrorStrings;
import org.jspecify.annotations.Nullable;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.concurrent.Callable;

@Component
public class McpMemoryTools {

    private final MemoryService memoryService;

    public McpMemoryTools(MemoryService memoryService) {
        this.memoryService = memoryService;
    }

    private <T> Mono<T> safelyInvoke(Callable<T> operation) {
        return Mono.fromCallable(operation)
                .onErrorMap(
                        exception -> !(exception instanceof IllegalArgumentException),
                        _ -> new IllegalStateException(
                                ErrorStrings.UNABLE_TO_PROCESS_MEMORY_AT_THIS_TIME_PLEASE_TRY_AGAIN_LATER.getErrorString()
                        )
                );
    }

    private Mono<Void> safelyInvoke(Runnable operation) {
        return Mono.<Void>fromRunnable(operation)
                .onErrorMap(
                        exception -> !(exception instanceof IllegalArgumentException),
                        _ -> new IllegalStateException(
                                ErrorStrings.UNABLE_TO_PROCESS_MEMORY_AT_THIS_TIME_PLEASE_TRY_AGAIN_LATER.getErrorString()
                        )
                );
    }

    @McpTool(
            description = "Save a new durable memory. appId is the required stable project namespace: use the repository name for code projects or the folder name otherwise. text is the required nonblank memory content. Creates a new memory rather than updating an existing one. The response contains only id, appId, text, and ISO-8601 createdAt; embeddings are excluded.",
            annotations = @McpTool.McpAnnotations(destructiveHint = false, openWorldHint = false)
    )
    public Mono<MemoryResponseDto> saveMemory(String appId, String text) {
        return safelyInvoke(() ->
                MemoryResponseDto.fromMemory(
                        memoryService.saveMemory(appId, text)
                ));
    }

    @McpTool(
            description = "List a page of memories for an explicit full-project audit. appId is the required stable project namespace. cursor is optional: omit it for the first page, then pass the server-issued nextCursor unchanged for the next page. Returns memories ordered by createdAt then id and a nextCursor; each page contains at most " + MemoryService.DEFAULT_GET_MEMORIES_PAGE_SIZE + " memories. Each memory contains only id, appId, text, and ISO-8601 createdAt; embeddings are excluded. Stop when nextCursor is null. This read operation does not mutate memories.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true, openWorldHint = false)
    )
    public Mono<GetMemoriesPage> getMemories(String appId, @Nullable GetMemoriesCursor cursor) {
        return safelyInvoke(() -> memoryService.getMemories(appId, cursor));
    }

    @McpTool(
            description = "Permanently delete a memory by its required id. This is a destructive mutation. The current operation is not app-scoped, so callers must use the id returned by Recall for the intended memory.",
            annotations = @McpTool.McpAnnotations(idempotentHint = true, openWorldHint = false)
    )
    public Mono<Void> deleteMemory(String id) {
        return safelyInvoke(() -> memoryService.deleteMemory(id));
    }

    @McpTool(
            description = "Retrieve the closest memories for a semantic query within one project namespace. appId is the required stable project namespace, text is the required nonblank query, and topN is the requested result count from 0 through 1000. topN of 0 returns an empty result without retrieval work; positive values return up to topN closest memories in rank order. Each memory contains only id, appId, text, and ISO-8601 createdAt; embeddings are excluded. This read operation does not mutate memories.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true, openWorldHint = false)
    )
    public Mono<List<MemoryResponseDto>> getTopNClosest(String appId, String text, int topN) {
        return safelyInvoke(() -> memoryService.getTopNClosest(appId, text, topN));
    }
}
