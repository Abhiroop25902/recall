package com.abhiroop.recall.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EmbeddingUtilsTest {

    @Test
    void normalizesVector() {
        assertArrayEquals(new float[]{0.6f, 0.8f}, EmbeddingUtils.normalize(new float[]{3f, 4f}));
    }

    @Test
    void returnsAlreadyNormalizedVector() {
        var embedding = new float[]{0.6f, 0.8f};

        assertSame(embedding, EmbeddingUtils.normalize(embedding));
    }

    @Test
    void rejectsEmptyVector() {
        assertThrows(IllegalArgumentException.class, () -> EmbeddingUtils.normalize(new float[]{}));
    }

    @Test
    void rejectsZeroVector() {
        assertThrows(IllegalArgumentException.class, () -> EmbeddingUtils.normalize(new float[]{0f, 0f}));
    }
}
