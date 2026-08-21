package com.abhiroop.recall.entity;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.FieldValue;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class MemoryTest {

    @Test
    void appIdMustNotBeBlank() {
        Timestamp createdAt = Timestamp.now();
        var embedding = FieldValue.vector(new double[]{0.1, 0.2});

        assertThrows(IllegalArgumentException.class,
                () -> new Memory(null, null, "memory", embedding, createdAt));
        assertThrows(IllegalArgumentException.class,
                () -> new Memory(null, "  ", "memory", embedding, createdAt));
    }
}
