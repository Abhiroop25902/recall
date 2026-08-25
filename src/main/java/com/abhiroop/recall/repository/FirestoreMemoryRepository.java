package com.abhiroop.recall.repository;

import com.abhiroop.recall.entity.Memory;
import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.VectorQuery;
import com.google.cloud.firestore.VectorValue;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.concurrent.ExecutionException;

@Slf4j
@Primary
@Repository
public class FirestoreMemoryRepository implements MemoryRepository {

    private final Firestore firestore;

    public FirestoreMemoryRepository(Firestore firestore) {
        this.firestore = firestore;
    }

    @Nonnull
    private static String requireId(@Nullable String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        return id;
    }

    private static <T> T await(ApiFuture<T> future) {
        try {
            return future.get();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            log.error("Firestore operation interrupted", exception);
            throw new IllegalStateException("Firestore operation interrupted", exception);
        } catch (ExecutionException exception) {
            log.error("Firestore operation failed", exception.getCause());
            throw new IllegalStateException("Firestore operation failed", exception.getCause());
        }
    }

    @Override
    public Memory save(Memory memory) {
        String memoryId = memory.id();
        boolean newDocument = memoryId == null || memoryId.isBlank();
        DocumentReference document = newDocument
                ? firestore.collection(Memory.COLLECTION_ID).document()
                : firestore.collection(Memory.COLLECTION_ID).document(requireId(memoryId));
        Memory persisted = newDocument
                ? new Memory(document.getId(), memory.appId(), memory.text(), memory.embedding(), memory.createdAt())
                : memory;

        await(document.create(persisted));
        return persisted;
    }

    @Override
    public void deleteById(String id) {
        await(firestore.collection(Memory.COLLECTION_ID).document(requireId(id)).delete());
    }

    @Override
    public List<Memory> findByAppId(String appId) {
        if (appId == null || appId.isBlank()) {
            throw new IllegalArgumentException("appId must not be blank");
        }

        return await(firestore.collection(Memory.COLLECTION_ID)
                .whereEqualTo(Memory.Fields.APP_ID.getFieldPath(), appId)
                .get())
                .getDocuments()
                .stream()
                .map(document -> document.toObject(Memory.class))
                .toList();
    }

    @Override
    public Long findCountByAppId(String appId) {
        return await(
                firestore.collection(Memory.COLLECTION_ID)
                        .whereEqualTo(Memory.Fields.APP_ID.getFieldPath(), appId)
                        .count()
                        .get()
        ).getCount();
    }

    @Override
    public List<Memory> findNearestN(String appId, VectorValue embedding, int topN) {
        return await(
                firestore.collection(Memory.COLLECTION_ID)
                        .whereEqualTo(Memory.Fields.APP_ID.getFieldPath(), appId)
                        .select(
                                Memory.Fields.APP_ID.getFieldPath(),
                                Memory.Fields.TEXT.getFieldPath(),
                                Memory.Fields.CREATED_AT.getFieldPath()
                        )
                        .findNearest(
                                Memory.Fields.EMBEDDING.getFieldPath(),
                                embedding,
                                topN,
                                VectorQuery.DistanceMeasure.COSINE
                        )
                        .get())
                .getDocuments()
                .stream()
                .map(document -> document.toObject(Memory.class))
                .toList();
    }
}
