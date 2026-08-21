package com.abhiroop.recall.entity;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.VectorValue;
import com.google.cloud.firestore.annotation.DocumentId;

public record Memory(
        @DocumentId String id,
        String appId,
        String text,
        VectorValue embedding,
        Timestamp createdAt
) {
    public Memory {
        if (appId == null || appId.isBlank()) {
            throw new IllegalArgumentException("appId must not be blank");
        }
    }
}
