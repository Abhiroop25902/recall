package com.abhiroop.recall.repository;

import com.abhiroop.recall.entity.Memory;
import com.google.api.core.ApiFutures;
import com.google.cloud.Timestamp;
import com.google.cloud.firestore.CollectionReference;
import com.google.cloud.firestore.FieldPath;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.Query;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.firestore.QuerySnapshot;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class FirestoreMemoryRepositoryTest {

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void findByAppIdBuildsOrderedLimitedQueryWithOptionalCursor(boolean hasCursor) {
        var firestore = mock(Firestore.class);
        var collection = mock(CollectionReference.class);
        var filtered = mock(Query.class);
        var byTimestamp = mock(Query.class);
        var byId = mock(Query.class);
        var limited = mock(Query.class);
        var afterCursor = mock(Query.class);
        var snapshot = mock(QuerySnapshot.class);
        var document = mock(QueryDocumentSnapshot.class);
        var memory = new Memory("next-id", "app", "text", null, null);
        when(firestore.collection("memories")).thenReturn(collection);
        when(collection.whereEqualTo(FieldPath.of("appId"), "app")).thenReturn(filtered);
        when(filtered.orderBy(FieldPath.of("createdAt"))).thenReturn(byTimestamp);
        when(byTimestamp.orderBy(FieldPath.documentId())).thenReturn(byId);
        when(byId.limit(7)).thenReturn(limited);
        when(snapshot.getDocuments()).thenReturn(List.of(document));
        when(document.toObject(Memory.class)).thenReturn(memory);
        var repository = new FirestoreMemoryRepository(firestore);
        var timestamp = hasCursor ? Timestamp.ofTimeSecondsAndNanos(1788611696L, 123456789) : null;
        var id = hasCursor ? "previous-id" : null;
        if (hasCursor) {
            when(limited.startAfter(timestamp, id)).thenReturn(afterCursor);
        }
        when((hasCursor ? afterCursor : limited).get()).thenReturn(ApiFutures.immediateFuture(snapshot));

        assertEquals(List.of(memory), repository.findByAppId("app", 7, timestamp, id));

        var order = inOrder(firestore, collection, filtered, byTimestamp, byId, limited, afterCursor);
        order.verify(firestore).collection("memories");
        order.verify(collection).whereEqualTo(FieldPath.of("appId"), "app");
        order.verify(filtered).orderBy(FieldPath.of("createdAt"));
        order.verify(byTimestamp).orderBy(FieldPath.documentId());
        order.verify(byId).limit(7);
        if (hasCursor) {
            order.verify(limited).startAfter(timestamp, id);
        }
        order.verify(hasCursor ? afterCursor : limited).get();
        verifyNoMoreInteractions(firestore, collection, filtered, byTimestamp, byId, limited, afterCursor);
    }
}
