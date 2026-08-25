package com.abhiroop.recall.utils;

public class EmbeddingUtils {
    private EmbeddingUtils() {
        /* This utility class should not be instantiated */
    }

    /**
     * default 3072 from gemini-embedding-2 is normalized, but we are using 1536 it's a recommended below 2048 (firestore vector limit)
     * so the embedding must be normalized
     *
     * @param embedding the embedding float array to normalize
     * @return normalized embedding, same length as given embedding
     */
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

        if (l2Norm == 1) return embedding;

        final float[] ret = new float[embedding.length];

        for (int i = 0; i < ret.length; i++) {
            ret[i] = embedding[i] / l2Norm;
        }

        return ret;
    }
}
