// EmbeddingClient over the OpenAI embeddings API (text-embedding-3-small, 1536 dims); inputs must be redacted chunk text
package com.docshelf.llm;

import com.docshelf.common.UpstreamException;
import com.docshelf.config.DocshelfProperties;
import com.openai.client.OpenAIClient;
import com.openai.models.embeddings.CreateEmbeddingResponse;
import com.openai.models.embeddings.Embedding;
import com.openai.models.embeddings.EmbeddingCreateParams;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class OpenAiEmbeddingClient implements EmbeddingClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiEmbeddingClient.class);
    public static final int DIMENSIONS = 1536;
    private static final int BATCH = 64;

    private final OpenAIClient client;
    private final DocshelfProperties props;
    private final Redactor redactor;
    private final LlmCallLogService callLog;

    public OpenAiEmbeddingClient(OpenAIClient client, DocshelfProperties props, Redactor redactor,
                                 LlmCallLogService callLog) {
        this.client = client;
        this.props = props;
        this.redactor = redactor;
        this.callLog = callLog;
    }

    @Override
    public List<float[]> embed(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }
        String model = props.openai().embeddingModel();
        List<float[]> out = new ArrayList<>(texts.size());
        for (int from = 0; from < texts.size(); from += BATCH) {
            List<String> batch = texts.subList(from, Math.min(texts.size(), from + BATCH));
            List<String> redacted = new ArrayList<>(batch.size());
            java.util.Map<String, Integer> counts = new java.util.LinkedHashMap<>();
            for (String t : batch) {
                Redaction r = redactor.redact(t == null ? "" : t);
                r.counts().forEach((k, v) -> counts.merge(k, v, Integer::sum));
                redacted.add(r.text().isBlank() ? " " : r.text());
            }
            long t0 = System.nanoTime();
            try {
                CreateEmbeddingResponse response = client.embeddings().create(EmbeddingCreateParams.builder()
                        .model(model)
                        .input(EmbeddingCreateParams.Input.ofArrayOfStrings(redacted))
                        .dimensions((long) DIMENSIONS)
                        .build());
                int latency = (int) ((System.nanoTime() - t0) / 1_000_000);
                List<Embedding> data = new ArrayList<>(response.data());
                data.sort(java.util.Comparator.comparingLong(Embedding::index));
                for (Embedding e : data) {
                    List<Float> floats = e.embedding();
                    float[] v = new float[floats.size()];
                    for (int i = 0; i < v.length; i++) {
                        v[i] = floats.get(i);
                    }
                    out.add(v);
                }
                callLog.log(new LlmCallLogService.Entry("embed", model, null, null,
                        (int) response.usage().promptTokens(), 0, counts, false, latency,
                        LlmCallLogService.STATUS_OK, null, null));
            } catch (RuntimeException e) {
                int latency = (int) ((System.nanoTime() - t0) / 1_000_000);
                UpstreamException mapped = OpenAiSupport.mapError("embeddings", e);
                callLog.log(new LlmCallLogService.Entry("embed", model, null, null, null, null, counts, false,
                        latency, LlmCallLogService.STATUS_ERROR, OpenAiSupport.safeMessage(mapped), null));
                log.warn("Embedding call failed batch={} retryable={}", batch.size(), mapped.retryable());
                throw mapped;
            }
        }
        return out;
    }

    @Override
    public int dimensions() {
        return DIMENSIONS;
    }
}
