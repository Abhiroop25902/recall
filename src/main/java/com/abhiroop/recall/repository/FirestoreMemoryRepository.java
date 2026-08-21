package com.abhiroop.recall.repository;

import com.abhiroop.recall.entity.Memory;
import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import org.springframework.stereotype.Repository;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

@Repository
public class FirestoreMemoryRepository implements MemoryRepository {

    private static final String COLLECTION = "memories";

    private final Firestore firestore;

    public FirestoreMemoryRepository(Firestore firestore) {
        this.firestore = firestore;
    }

    @Override
    public Memory save(Memory memory) {
        String memoryId = memory.id();
        boolean newDocument = memoryId == null || memoryId.isBlank();
        DocumentReference document = newDocument
                ? firestore.collection(COLLECTION).document()
                : firestore.collection(COLLECTION).document(requireId(memoryId));
        Memory persisted = newDocument
                ? new Memory(document.getId(), memory.appId(), memory.text(), memory.embedding(), memory.createdAt())
                : memory;

        await(document.create(persisted));
        return persisted;
    }

    @Override
    public Optional<Memory> findById(String id) {
        DocumentSnapshot document = await(firestore.collection(COLLECTION).document(requireId(id)).get());
        return document.exists() ? Optional.ofNullable(document.toObject(Memory.class)) : Optional.empty();
    }

    @Override
    public void deleteById(String id) {
        await(firestore.collection(COLLECTION).document(requireId(id)).delete());
    }

    @Override
    public List<Memory> findByAppId(String appId) {
        if (appId == null || appId.isBlank()) {
            throw new IllegalArgumentException("appId must not be blank");
        }

        return await(firestore.collection(COLLECTION)
                        .whereEqualTo("appId", appId)
                        .get())
                .getDocuments()
                .stream()
                .map(document -> document.toObject(Memory.class))
                .toList();
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
            throw new IllegalStateException("Firestore operation interrupted", exception);
        } catch (ExecutionException exception) {
            throw new IllegalStateException("Firestore operation failed", exception.getCause());
        }
    }
}
