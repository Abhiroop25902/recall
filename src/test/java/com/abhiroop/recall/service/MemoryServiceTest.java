package com.abhiroop.recall.service;

import com.abhiroop.recall.dto.SaveMemoryRequestDto;
import com.abhiroop.recall.entity.Memory;
import com.abhiroop.recall.repository.MemoryRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class MemoryServiceTest {

    @Test
    void saveMemoryMapsDtoAndDelegatesToRepository() {
        var repository = new FakeMemoryRepository();
        var service = new MemoryService(repository);

        Memory saved = service.saveMemory(new SaveMemoryRequestDto("app", "text"));

        assertEquals("persisted-id", saved.id());
        assertEquals("app", repository.saved.appId());
        assertEquals("text", repository.saved.text());
    }

    @Test
    void getMemoriesDelegatesToRepository() {
        var repository = new FakeMemoryRepository();
        var expected = List.of(new Memory(null, "app", "text", null, null));
        repository.memories = expected;
        var service = new MemoryService(repository);

        assertSame(expected, service.getMemories("app"));
        assertEquals("app", repository.requestedAppId);
    }

    @Test
    void deleteMemoryDelegatesToRepository() {
        var repository = new FakeMemoryRepository();
        var service = new MemoryService(repository);

        service.deleteMemory("memory-id");

        assertEquals("memory-id", repository.deletedId);
    }

    private static class FakeMemoryRepository implements MemoryRepository {
        private Memory saved;
        private List<Memory> memories = List.of();
        private String requestedAppId;
        private String deletedId;

        @Override
        public Memory save(Memory memory) {
            saved = memory;
            return new Memory("persisted-id", memory.appId(), memory.text(), null, null);
        }

        @Override
        public Optional<Memory> findById(String id) {
            return Optional.empty();
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
    }
}
