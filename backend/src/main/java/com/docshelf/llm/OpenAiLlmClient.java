// LlmClient over the OpenAI Responses API (store=false, temperature 0); redacts defensively, logs llm_call and audit rows
package com.docshelf.llm;

import com.docshelf.audit.AuditEvent;
import com.docshelf.audit.AuditService;
import com.docshelf.audit.entity.AuditAction;
import com.docshelf.audit.entity.AuditOrigin;
import com.docshelf.common.UpstreamException;
import com.docshelf.config.DocshelfProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.client.OpenAIClient;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseCreateParams;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class OpenAiLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiLlmClient.class);
    static final String CHAT_PURPOSE_PREFIX = "chat";

    private final OpenAIClient client;
    private final DocshelfProperties props;
    private final Redactor redactor;
    private final LlmCallLogService callLog;
    private final AuditService audit;
    private final ObjectMapper objectMapper;

    public OpenAiLlmClient(OpenAIClient client, DocshelfProperties props, Redactor redactor,
                           LlmCallLogService callLog, AuditService audit, ObjectMapper objectMapper) {
        this.client = client;
        this.props = props;
        this.redactor = redactor;
        this.callLog = callLog;
        this.audit = audit;
        this.objectMapper = objectMapper;
    }

    /** Purposes starting with "chat" use the chat model; everything else (classify, extract, summarise) the extract model. */
    String modelFor(LlmRequest req) {
        return req.purpose().startsWith(CHAT_PURPOSE_PREFIX) ? props.openai().chatModel() : props.openai().extractModel();
    }

    @Override
    public String complete(LlmRequest req) {
        return call(req, null);
    }

    @Override
    public <T> T completeJson(LlmRequest req, Class<T> schemaType) {
        String json = call(req, schemaType);
        try {
            return objectMapper.readValue(json, schemaType);
        } catch (IOException e) {
            throw new UpstreamException("OpenAI returned JSON that does not match " + schemaType.getSimpleName(), e);
        }
    }

    private String call(LlmRequest req, Class<?> schemaType) {
        String model = modelFor(req);
        // Defence in depth: callers redact and keep the placeholder map; a second pass finds nothing new.
        Redaction sys = redactor.redact(req.systemPrompt() == null ? "" : req.systemPrompt());
        Redaction user = redactor.redact(req.userText() == null ? "" : req.userText());
        Map<String, Integer> counts = new LinkedHashMap<>(sys.counts());
        user.counts().forEach((k, v) -> counts.merge(k, v, Integer::sum));

        ResponseCreateParams.Builder b = ResponseCreateParams.builder()
                .model(model)
                .input(user.text())
                .maxOutputTokens((long) req.maxOutputTokens())
                .temperature(0.0)
                .store(false);
        if (!sys.text().isBlank()) {
            b.instructions(sys.text());
        }
        if (schemaType != null) {
            b.text(OpenAiSupport.strictJsonFormat(schemaType));
        }
        long t0 = System.nanoTime();
        try {
            Response response = client.responses().create(b.build());
            int latency = (int) ((System.nanoTime() - t0) / 1_000_000);
            String text = OpenAiSupport.outputText(response);
            callLog.log(new LlmCallLogService.Entry(req.purpose(), model, req.documentId(), req.sessionId(),
                    OpenAiSupport.inputTokens(response), OpenAiSupport.outputTokens(response), counts, false,
                    latency, LlmCallLogService.STATUS_OK, null, response.id()));
            audit.record(AuditEvent.of(AuditAction.LLM_CALL, AuditOrigin.SYSTEM)
                    .documentId(req.documentId()).session(req.sessionId())
                    .detail("purpose", req.purpose()).detail("model", model)
                    .detail("inputTokens", OpenAiSupport.inputTokens(response))
                    .detail("outputTokens", OpenAiSupport.outputTokens(response))
                    .detail("redacted", counts));
            return text;
        } catch (RuntimeException e) {
            int latency = (int) ((System.nanoTime() - t0) / 1_000_000);
            UpstreamException mapped = OpenAiSupport.mapError("completion", e);
            callLog.log(new LlmCallLogService.Entry(req.purpose(), model, req.documentId(), req.sessionId(),
                    null, null, counts, false, latency, LlmCallLogService.STATUS_ERROR,
                    OpenAiSupport.safeMessage(mapped), null));
            log.warn("LLM call failed purpose={} model={} retryable={}", req.purpose(), model, mapped.retryable());
            throw mapped;
        }
    }
}
