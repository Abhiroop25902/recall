package com.abhiroop.recall.repository;

import com.abhiroop.recall.entity.Memory;
import com.google.cloud.firestore.VectorValue;

import java.util.List;

public interface MemoryRepository {

    Memory save(Memory memory);

    void deleteById(String id);

    List<Memory> findByAppId(String appId);

    Long findCountByAppId(String appId);

    List<Memory> findNearestN(String appId, VectorValue embedding, int topN);
}
