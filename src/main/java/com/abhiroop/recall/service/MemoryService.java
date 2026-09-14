package com.abhiroop.recall.service;

import com.abhiroop.recall.dto.GetMemoriesCursor;
import com.abhiroop.recall.dto.GetMemoriesPage;
import com.abhiroop.recall.dto.MemoryResponseDto;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class MemoryService {

    public static final int DEFAULT_GET_MEMORIES_PAGE_SIZE = 10;
    private final MemoryRepository memoryRepository;
    private final EmbeddingModel embeddingModel;

    @Autowired
    public MemoryService(MemoryRepository memoryRepository, EmbeddingModel embeddingModel) {
        this.memoryRepository = memoryRepository;
        this.embeddingModel = embeddingModel;
    }

    public Memory saveMemory(String appId, String text) {
        if (appId == null || appId.isBlank())
            throw new IllegalArgumentException(
                    ErrorStrings.APP_ID_MUST_BE_PRESENT_AND_MUST_NOT_BE_BLANK.getErrorString()
            );

        if (text == null || text.isBlank())
            throw new IllegalArgumentException(
                    ErrorStrings.TEXT_MUST_BE_PRESENT_AND_MUST_NOT_BE_BLANK.getErrorString()
            );

        //gemini-embedding-2 => the embedding is normalized for 1536
        //if for some reason we have to downgrade to gemini-embedding-001 have to manually normalize
        final float[] embedding = embeddingModel.embed(text);

        final VectorValue embeddingVector = FieldValue.vector(
                Doubles.toArray(Floats.asList(embedding))
        );

        final var memory = Memory
                .builder()
                .appId(appId)
                .text(text)
                .embedding(embeddingVector)
                .createdAt(Timestamp.now())
                .build();


        return memoryRepository.save(memory);
    }

    public GetMemoriesPage getMemories(
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
                memoryList.stream().map(MemoryResponseDto::fromMemory).toList(),
                nextCursor
        );
    }

    public void deleteMemory(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException(
                    ErrorStrings.ID_MUST_BE_PRESENT_AND_MUST_NOT_BE_BLANK.getErrorString()
            );
        }

        memoryRepository.deleteById(id);
    }

    public List<MemoryResponseDto> getTopNClosest(String appId, String text, int topN) {
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

        return memoryRepository.findNearestN(appId, embeddingVector, topN)
                .stream().map(MemoryResponseDto::fromMemory)
                .toList();
    }
}
