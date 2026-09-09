// Persists one llm_call row per OpenAI request in its own transaction (accounting survives caller rollbacks)
package com.docshelf.llm;

import com.docshelf.llm.entity.LlmCall;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LlmCallLogService {

    private static final Logger log = LoggerFactory.getLogger(LlmCallLogService.class);
    public static final String STATUS_OK = "OK";
    public static final String STATUS_ERROR = "ERROR";

    public record Entry(String purpose, String model, UUID documentId, UUID sessionId, Integer inputTokens,
                        Integer outputTokens, Map<String, Integer> redactionCounts, boolean imageSent,
                        Integer latencyMs, String status, String error, String openaiResponseId) {
    }

    private final LlmCallRepository repository;

    public LlmCallLogService(LlmCallRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public LlmCall log(Entry e) {
        LlmCall row = new LlmCall();
        row.setPurpose(e.purpose());
        row.setModel(e.model());
        row.setDocumentId(e.documentId());
        row.setSessionId(e.sessionId());
        row.setInputTokens(e.inputTokens());
        row.setOutputTokens(e.outputTokens());
        row.setRedactionCounts(e.redactionCounts() == null || e.redactionCounts().isEmpty() ? null
                : new java.util.LinkedHashMap<>(e.redactionCounts()));
        row.setImageSent(e.imageSent());
        row.setLatencyMs(e.latencyMs());
        row.setStatus(e.status());
        row.setError(truncate(e.error()));
        row.setOpenaiResponseId(e.openaiResponseId());
        LlmCall saved = repository.save(row);
        log.info("llm_call purpose={} model={} status={} in={} out={} latencyMs={} image={}",
                e.purpose(), e.model(), e.status(), e.inputTokens(), e.outputTokens(), e.latencyMs(), e.imageSent());
        return saved;
    }

    private static String truncate(String s) {
        return s == null || s.length() <= 2000 ? s : s.substring(0, 2000);
    }
}
