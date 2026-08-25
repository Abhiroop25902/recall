package com.abhiroop.recall.service;

import com.abhiroop.recall.dto.SaveMemoryRequestDto;
import com.abhiroop.recall.entity.Memory;
import com.abhiroop.recall.repository.MemoryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MemoryServiceTest {

    @Test
    void saveMemoryMapsDtoAndDelegatesToRepository() {
        var repository = new FakeMemoryRepository();
        var embeddingModel = mock(EmbeddingModel.class);
        when(embeddingModel.embed("text")).thenReturn(new float[]{0.1f, 0.2f});
        var service = new MemoryService(repository, embeddingModel);

        Memory saved = service.saveMemory(new SaveMemoryRequestDto("app", "text"));

        assertEquals("persisted-id", saved.id());
        assertEquals("app", repository.saved.appId());
        assertEquals("text", repository.saved.text());
        assertArrayEquals(new double[]{0.4472136, 0.8944272}, repository.saved.embedding().toArray(), 0.000001);
    }

    @Test
    void getMemoriesDelegatesToRepository() {
        var repository = new FakeMemoryRepository();
        var expected = List.of(new Memory(null, "app", "text", null, null));
        repository.memories = expected;
        var service = new MemoryService(repository, mock(EmbeddingModel.class));

        assertSame(expected, service.getMemories("app"));
        assertEquals("app", repository.requestedAppId);
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
    void getTopNClosestNormalizesEmbeddingAndDelegatesToRepository() {
        var repository = new FakeMemoryRepository();
        repository.memoryCount = 1;
        var expected = List.of(new Memory("memory-id", "app", "memory", null, null));
        repository.nearestMemories = expected;
        var embeddingModel = mock(EmbeddingModel.class);
        when(embeddingModel.embed("text")).thenReturn(new float[]{3f, 4f});
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
        public List<Memory> findByAppId(String appId) {
            requestedAppId = appId;
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
