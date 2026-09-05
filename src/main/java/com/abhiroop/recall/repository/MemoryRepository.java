package com.abhiroop.recall.repository;

import com.abhiroop.recall.entity.Memory;
import com.google.cloud.Timestamp;
import com.google.cloud.firestore.VectorValue;
import org.jspecify.annotations.Nullable;

import java.util.List;

public interface MemoryRepository {

    Memory save(Memory memory);

    void deleteById(String id);

    /** Returns one page ordered by creation time then ID; continuation requires both cursor values. */
    List<Memory> findByAppId(String appId,
                             int pageSize,
                             @Nullable Timestamp afterCreatedAt,
                             @Nullable String afterId);

    Long findCountByAppId(String appId);

    List<Memory> findNearestN(String appId, VectorValue embedding, int topN);
}
