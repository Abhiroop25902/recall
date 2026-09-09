package com.abhiroop.recall.service;

import com.abhiroop.recall.dto.GetMemoriesCursor;
import com.abhiroop.recall.dto.GetMemoriesPage;
import com.abhiroop.recall.dto.SaveMemoryRequestDto;
import com.abhiroop.recall.entity.Memory;
import com.abhiroop.recall.repository.MemoryRepository;
import com.abhiroop.recall.utils.ErrorStrings;
import com.google.cloud.Timestamp;
import com.google.cloud.firestore.FieldValue;
import com.google.cloud.firestore.VectorValue;
import com.google.common.primitives.Doubles;
import com.google.common.primitives.Floats;
import org.jspecify.annotations.Nullable;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class MemoryService {

    private static final int DEFAULT_GET_MEMORIES_PAGE_SIZE = 10;
    private final MemoryRepository memoryRepository;
    private final EmbeddingModel embeddingModel;

    @Autowired
    public MemoryService(MemoryRepository memoryRepository, EmbeddingModel embeddingModel) {
        this.memoryRepository = memoryRepository;
        this.embeddingModel = embeddingModel;
    }

    @Tool(description = "Save Memory")
    Memory saveMemory(SaveMemoryRequestDto dto) {
        //gemini-embedding-2 => the embedding is normalized for 1536
        //if for some reason we have to downgrade to gemini-embedding-001 have to manually normalize
        final float[] embedding = embeddingModel.embed(dto.text());

        final VectorValue embeddingVector = FieldValue.vector(
                Doubles.toArray(Floats.asList(embedding))
        );

        final var memory = Memory
                .builder()
                .appId(dto.appId())
                .text(dto.text())
                .embedding(embeddingVector)
                .createdAt(Timestamp.now())
                .build();


        return memoryRepository.save(memory);
    }

    @Tool(description = "Get a page with memories (up to " + DEFAULT_GET_MEMORIES_PAGE_SIZE + ") and nextCursor, ordered by createdAt then id. Omit cursor for the first page; pass the server-issued nextCursor unchanged as cursor for the next page. Stop when nextCursor is null.")
    GetMemoriesPage getMemories(
            String appId,
            @Nullable GetMemoriesCursor cursor
    ) {
        if (appId == null || appId.isBlank())
            throw new IllegalArgumentException(
                    ErrorStrings.APP_ID_MUST_BE_PRESENT_AND_MUST_NOT_BE_BLANK.getErrorString()
            );

        Timestamp afterCreatedAt = null;
        String afterId = null;

        if (cursor != null) {
            afterCreatedAt = Timestamp.ofTimeSecondsAndNanos(
                    cursor.afterCreatedAt().getEpochSecond(),
                    cursor.afterCreatedAt().getNano()
            );
            afterId = cursor.afterId();
        }

        final List<Memory> memoryList = memoryRepository.findByAppId(appId, DEFAULT_GET_MEMORIES_PAGE_SIZE, afterCreatedAt, afterId);

        final GetMemoriesCursor nextCursor = memoryList.size() < DEFAULT_GET_MEMORIES_PAGE_SIZE ?
                null :
                new GetMemoriesCursor(
                        memoryList.getLast().createdAt().toSqlTimestamp().toInstant(),
                        memoryList.getLast().id()
                );

        return new GetMemoriesPage(
                memoryList,
                nextCursor
        );
    }

    @Tool(description = "Delete a memory by id")
    void deleteMemory(String id) {
        memoryRepository.deleteById(id);
    }

    @Tool(description = "Get top n closest memories")
    List<Memory> getTopNClosest(String appId, String text, int topN) {
        if (appId == null || appId.isBlank())
            throw new IllegalArgumentException(
                    ErrorStrings.APP_ID_MUST_BE_PRESENT_AND_MUST_NOT_BE_BLANK.getErrorString()
            );

        if (text == null || text.isBlank())
            throw new IllegalArgumentException(
                    ErrorStrings.TEXT_MUST_BE_PRESENT_AND_MUST_NOT_BE_BLANK.getErrorString()
            );

        if (topN == 0) return List.of();

        if (topN < 0 || topN > 1000)
            throw new IllegalArgumentException("topN must be between 1 and 1000");

        //avoid generating embedding and doing similarity search if no memory with given appId exists
        final var appIdMemoryCount = memoryRepository.findCountByAppId(appId);

        if (appIdMemoryCount == 0) return List.of();

        //gemini-embedding-2 => the embedding is normalized for 1536
        //if for some reason we have to downgrade to gemini-embedding-001 have to manually normalize
        final VectorValue embeddingVector = FieldValue.vector(
                Doubles.toArray(Floats.asList(
                        embeddingModel.embed(text)
                ))
        );

        return memoryRepository.findNearestN(appId, embeddingVector, topN);
    }
}
