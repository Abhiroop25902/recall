package com.abhiroop.recall.repository;

import com.abhiroop.recall.entity.Memory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Slf4j
@Primary
@Repository
public class ConsoleTestMemoryRepository implements MemoryRepository {

    @Override
    public Memory save(Memory memory) {
        log.info("Save: {}", memory);
        return memory;
    }

    @Override
    public Optional<Memory> findById(String id) {
        log.info("Find: {}", id);
        return Optional.empty();
    }

    @Override
    public void deleteById(String id) {
        log.info("Delete: {}", id);
    }

    @Override
    public List<Memory> findByAppId(String appId) {
        log.info("Find By App Id: {}", appId);
        return List.of();
    }
}
