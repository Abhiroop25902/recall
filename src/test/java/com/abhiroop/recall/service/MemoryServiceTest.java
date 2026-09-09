package com.abhiroop.recall.service;

import com.abhiroop.recall.RecallApplication;
import com.abhiroop.recall.dto.GetMemoriesCursor;
import com.abhiroop.recall.dto.SaveMemoryRequestDto;
import com.abhiroop.recall.entity.Memory;
import com.abhiroop.recall.repository.MemoryRepository;
import com.google.cloud.Timestamp;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.embedding.EmbeddingModel;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MemoryServiceTest {

    @Test
    void saveMemoryMapsDtoAndDelegatesToRepository() {
        var repository = new FakeMemoryRepository();
        var embeddingModel = mock(EmbeddingModel.class);
        when(embeddingModel.embed("text")).thenReturn(new float[]{0.6f, 0.8f});
        var service = new MemoryService(repository, embeddingModel);

        Memory saved = service.saveMemory(new SaveMemoryRequestDto("app", "text"));

        assertEquals("persisted-id", saved.id());
        assertEquals("app", repository.saved.appId());
        assertEquals("text", repository.saved.text());
        assertArrayEquals(new double[]{0.6, 0.8}, repository.saved.embedding().toArray(), 0.000001);
    }

    @Test
    void failedReplacementSaveDoesNotDeleteExistingMemory() {
        var repository = mock(MemoryRepository.class);
        var embeddingModel = mock(EmbeddingModel.class);
        when(embeddingModel.embed("replacement")).thenReturn(new float[]{0.6f, 0.8f});
        when(repository.save(any())).thenThrow(new IllegalStateException("save failed"));
        var service = new MemoryService(repository, embeddingModel);

        final var request = new SaveMemoryRequestDto("app", "replacement");
        
        assertThrows(IllegalStateException.class, () -> service.saveMemory(request));

        verify(repository, never()).deleteById(any());
    }

    @Test
    void successfulReplacementSaveReturnsIdBeforeSeparateDelete() {
        var repository = mock(MemoryRepository.class);
        var embeddingModel = mock(EmbeddingModel.class);
        when(embeddingModel.embed("replacement")).thenReturn(new float[]{0.6f, 0.8f});
        when(repository.save(any())).thenReturn(new Memory("replacement-id", "app", "replacement", null, null));
        var service = new MemoryService(repository, embeddingModel);

        assertEquals("replacement-id", service.saveMemory(new SaveMemoryRequestDto("app", "replacement")).id());
        verify(repository, never()).deleteById(any());

        service.deleteMemory("obsolete-id");
        verify(repository).deleteById("obsolete-id");
    }

    @Test
    void getMemoriesWithNullCursorRequestsFirstPageOfTen() {
        var repository = new FakeMemoryRepository();
        var expected = List.of(new Memory(null, "app", "text", null, null));
        repository.memories = expected;
        var service = new MemoryService(repository, mock(EmbeddingModel.class));

        var page = service.getMemories("app", null);
        assertSame(expected, page.memories());
        assertNull(page.nextCursor());
        assertEquals("app", repository.requestedAppId);
        assertEquals(10, repository.requestedPageSize);
        assertNull(repository.afterCreatedAt);
        assertNull(repository.afterId);
    }

    @Test
    void getMemoriesWithCursorPreservesExactNanoseconds() {
        var repository = new FakeMemoryRepository();
        var expected = List.of(new Memory("next-id", "app", "text", null, null));
        repository.memories = expected;
        var service = new MemoryService(repository, mock(EmbeddingModel.class));
        var instant = Instant.parse("2026-09-05T12:34:56.123456789Z");
        var cursor = new GetMemoriesCursor(instant, "previous-id");

        var page = service.getMemories("app", cursor);
        assertSame(expected, page.memories());
        assertNull(page.nextCursor());
        assertEquals("app", repository.requestedAppId);
        assertEquals(10, repository.requestedPageSize);
        assertEquals(instant.getEpochSecond(), repository.afterCreatedAt.getSeconds());
        assertEquals(123456789, repository.afterCreatedAt.getNanos());
        assertEquals("previous-id", repository.afterId);
    }

    @Test
    void getMemoriesEmptyPageHasNoCursor() {
        var service = new MemoryService(new FakeMemoryRepository(), mock(EmbeddingModel.class));
        var page = service.getMemories("app", null);
        assertEquals(List.of(), page.memories());
        assertNull(page.nextCursor());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n"})
    void getMemoriesRejectsMissingOrBlankAppIdWithoutExternalWork(String appId) {
        var repository = mock(MemoryRepository.class);
        var embeddingModel = mock(EmbeddingModel.class);
        var service = new MemoryService(repository, embeddingModel);

        assertThrows(IllegalArgumentException.class, () -> service.getMemories(appId, null));

        verifyNoInteractions(repository, embeddingModel);
    }

    @Test
    void getMemoriesToolRoundTripsServerCursorWithFullPrecision() {
        var repository = new FakeMemoryRepository();
        var instant = Instant.parse("2026-09-05T12:34:56.123456789Z");
        var timestamp = Timestamp.ofTimeSecondsAndNanos(instant.getEpochSecond(), instant.getNano());
        repository.memories = IntStream.range(0, 10)
                .mapToObj(i -> new Memory("id-" + i, "app", "text", null, timestamp)).toList();
        var service = new MemoryService(repository, mock(EmbeddingModel.class));
        var page = service.getMemories("app", null);
        assertSame(repository.memories, page.memories());
        assertEquals(new GetMemoriesCursor(instant, "id-9"), page.nextCursor());

        var tool = java.util.Arrays.stream(new RecallApplication().memoryTools(service).getToolCallbacks())
                .filter(callback -> callback.getToolDefinition().name().equals("getMemories"))
                .findFirst().orElseThrow();
        var mapper = JsonMapper.builder().build();
        var response = mapper.readTree(tool.call("{\"appId\":\"app\"}"));
        assertEquals(10, response.get("memories").size());
        assertEquals(instant.toString(), response.at("/nextCursor/afterCreatedAt").asString());
        assertEquals("id-9", response.at("/nextCursor/afterId").asString());
        repository.memories = List.of();
        var lastPage = mapper.readTree(tool.call("{\"appId\":\"app\",\"cursor\":" + response.get("nextCursor") + "}"));
        assertEquals(timestamp, repository.afterCreatedAt);
        assertEquals("id-9", repository.afterId);
        assertTrue(lastPage.get("memories").isEmpty());
        assertTrue(lastPage.get("nextCursor").isNull());
    }

    @Test
    void deleteMemoryDelegatesToRepository() {
        var repository = new FakeMemoryRepository();
        var service = new MemoryService(repository, mock(EmbeddingModel.class));

        service.deleteMemory("memory-id");

        assertEquals("memory-id", repository.deletedId);
    }

    @Test
    void getTopNClosestReturnsEmptyWithoutEmbeddingWhenTopNIsZero() {
        var repository = new FakeMemoryRepository();
        var embeddingModel = mock(EmbeddingModel.class);
        var service = new MemoryService(repository, embeddingModel);

        assertEquals(List.of(), service.getTopNClosest("app", "text", 0));
        assertEquals(0, repository.findCountCalls);
        verifyNoInteractions(embeddingModel);
    }

    @Test
    void getTopNClosestRejectsInvalidTopNWithoutEmbedding() {
        var repository = new FakeMemoryRepository();
        var embeddingModel = mock(EmbeddingModel.class);
        var service = new MemoryService(repository, embeddingModel);

        assertThrows(IllegalArgumentException.class, () -> service.getTopNClosest("app", "text", -1));
        assertThrows(IllegalArgumentException.class, () -> service.getTopNClosest("app", "text", 1001));
        assertEquals(0, repository.findCountCalls);
        verifyNoInteractions(embeddingModel);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n"})
    void getTopNClosestRejectsMissingOrBlankAppIdBeforeTopNShortcut(String appId) {
        var repository = mock(MemoryRepository.class);
        var embeddingModel = mock(EmbeddingModel.class);
        var service = new MemoryService(repository, embeddingModel);

        assertThrows(IllegalArgumentException.class, () -> service.getTopNClosest(appId, "text", 0));

        verifyNoInteractions(repository, embeddingModel);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n"})
    void getTopNClosestRejectsMissingOrBlankTextBeforeTopNShortcut(String text) {
        var repository = mock(MemoryRepository.class);
        var embeddingModel = mock(EmbeddingModel.class);
        var service = new MemoryService(repository, embeddingModel);

        assertThrows(IllegalArgumentException.class, () -> service.getTopNClosest("app", text, 0));

        verifyNoInteractions(repository, embeddingModel);
    }

    @Test
    void getTopNClosestReturnsEmptyWithoutEmbeddingWhenAppHasNoMemories() {
        var repository = new FakeMemoryRepository();
        var embeddingModel = mock(EmbeddingModel.class);
        var service = new MemoryService(repository, embeddingModel);

        assertEquals(List.of(), service.getTopNClosest("app", "text", 3));
        assertEquals("app", repository.countedAppId);
        verifyNoInteractions(embeddingModel);
    }

    @Test
    void getTopNClosestDelegatesProviderEmbeddingToRepository() {
        var repository = new FakeMemoryRepository();
        repository.memoryCount = 1;
        var expected = List.of(new Memory("memory-id", "app", "memory", null, null));
        repository.nearestMemories = expected;
        var embeddingModel = mock(EmbeddingModel.class);
        when(embeddingModel.embed("text")).thenReturn(new float[]{0.6f, 0.8f});
        var service = new MemoryService(repository, embeddingModel);

        assertSame(expected, service.getTopNClosest("app", "text", 3));
        assertEquals("app", repository.nearestAppId);
        assertEquals(3, repository.requestedTopN);
        assertArrayEquals(new double[]{0.6, 0.8}, repository.nearestEmbedding.toArray(), 0.000001);
    }

    private static class FakeMemoryRepository implements MemoryRepository {
        private Memory saved;
        private List<Memory> memories = List.of();
        private long memoryCount;
        private List<Memory> nearestMemories = List.of();
        private String requestedAppId;
        private int requestedPageSize;
        private Timestamp afterCreatedAt;
        private String afterId;
        private String deletedId;
        private int findCountCalls;
        private String countedAppId;
        private String nearestAppId;
        private com.google.cloud.firestore.VectorValue nearestEmbedding;
        private int requestedTopN;

        @Override
        public Memory save(Memory memory) {
            saved = memory;
            return new Memory("persisted-id", memory.appId(), memory.text(), null, null);
        }

        @Override
        public void deleteById(String id) {
            deletedId = id;
        }

        @Override
        public List<Memory> findByAppId(String appId, int pageSize, Timestamp afterCreatedAt, String afterId) {
            requestedAppId = appId;
            requestedPageSize = pageSize;
            this.afterCreatedAt = afterCreatedAt;
            this.afterId = afterId;
            return memories;
        }

        @Override
        public Long findCountByAppId(String appId) {
            findCountCalls++;
            countedAppId = appId;
            return memoryCount;
        }

        @Override
        public List<Memory> findNearestN(String appId, com.google.cloud.firestore.VectorValue embedding, int topN) {
            nearestAppId = appId;
            nearestEmbedding = embedding;
            requestedTopN = topN;
            return nearestMemories;
        }
    }
}
