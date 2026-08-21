package com.abhiroop.recall.repository;

import com.abhiroop.recall.entity.Memory;

import java.util.List;
import java.util.Optional;

public interface MemoryRepository {

    Memory save(Memory memory);

    Optional<Memory> findById(String id);

    void deleteById(String id);

    List<Memory> findByAppId(String appId);
}
