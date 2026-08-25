package com.abhiroop.recall.utils;

public class EmbeddingUtils {
    private EmbeddingUtils() {
        /* This utility class should not be instantiated */
    }

    public static float[] normalize(float[] embedding) {
        if (embedding.length == 0) {
            throw new IllegalArgumentException("Embedding is empty");
        }

        float sqSum = 0f;

        for (float v : embedding) {
            sqSum += v * v;
        }

        float l2Norm = (float) Math.sqrt(sqSum);

        if (l2Norm == 0) {
            throw new IllegalArgumentException("L2 Norm is Zero");
        }

        if(l2Norm == 1) return embedding;

        final float[] ret = new float[embedding.length];

        for (int i = 0; i < ret.length; i++) {
            ret[i] = embedding[i] / l2Norm;
        }

        return ret;
    }
}
