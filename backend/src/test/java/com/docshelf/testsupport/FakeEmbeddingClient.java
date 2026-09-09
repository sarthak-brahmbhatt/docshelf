// EmbeddingClient test double: deterministic unit vectors derived from a hash of the text
package com.docshelf.testsupport;

import com.docshelf.llm.EmbeddingClient;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class FakeEmbeddingClient implements EmbeddingClient {

    public static final int DIMENSIONS = 1536;

    @Override
    public List<float[]> embed(List<String> texts) {
        List<float[]> out = new ArrayList<>(texts.size());
        for (String t : texts) {
            out.add(embedOne(t));
        }
        return out;
    }

    /** Same text always yields the same vector; similar texts share the token-bucket component so cosine is meaningful. */
    public static float[] embedOne(String text) {
        float[] v = new float[DIMENSIONS];
        String normalised = text == null ? "" : text.toLowerCase();
        // Token buckets make overlapping vocabularies produce higher cosine similarity.
        for (String token : normalised.split("[^a-z0-9]+")) {
            if (token.isEmpty()) {
                continue;
            }
            int bucket = Math.floorMod(token.hashCode(), DIMENSIONS);
            v[bucket] += 1.0f;
        }
        // Small deterministic noise so distinct texts are never identical.
        Random r = new Random(seed(normalised));
        for (int i = 0; i < DIMENSIONS; i++) {
            v[i] += (float) (r.nextGaussian() * 0.01);
        }
        double norm = 0;
        for (float f : v) {
            norm += f * f;
        }
        norm = Math.sqrt(norm);
        if (norm > 0) {
            for (int i = 0; i < DIMENSIONS; i++) {
                v[i] = (float) (v[i] / norm);
            }
        }
        return v;
    }

    private static long seed(String s) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
            long seed = 0;
            for (int i = 0; i < 8; i++) {
                seed = (seed << 8) | (d[i] & 0xff);
            }
            return seed;
        } catch (NoSuchAlgorithmException e) {
            return s.hashCode();
        }
    }

    @Override
    public int dimensions() {
        return DIMENSIONS;
    }
}
