package com.abhiroop.recall.entity;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.FieldPath;
import com.google.cloud.firestore.VectorValue;
import com.google.cloud.firestore.annotation.DocumentId;
import lombok.Builder;
import lombok.Getter;

@Builder
public record Memory(
        @DocumentId String id,
        String appId,
        String text,
        VectorValue embedding,
        Timestamp createdAt
) {
    public static final String COLLECTION_ID = "memories";

    public Memory {
        if (appId == null || appId.isBlank()) {
            throw new IllegalArgumentException("appId must not be blank");
        }
    }

    @Getter
    public enum Fields {
        APP_ID("appId"),
        TEXT("text"),
        EMBEDDING("embedding"),
        CREATED_AT("createdAt");

        private final FieldPath fieldPath;

        Fields(String fieldName) {
            this.fieldPath = FieldPath.of(fieldName);
        }
    }
}
