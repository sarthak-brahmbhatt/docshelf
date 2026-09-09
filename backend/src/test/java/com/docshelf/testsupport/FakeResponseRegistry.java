// Registry of canned LLM / vision / transcription responses keyed by purpose; tests stub it, fakes read it
package com.docshelf.testsupport;

import com.docshelf.llm.LlmRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public class FakeResponseRegistry {

    public static final String VISION_PURPOSE = "vision";
    public static final String TRANSCRIBE_PURPOSE = "transcribe";

    private final ObjectMapper objectMapper;
    private final Map<String, Function<LlmRequest, String>> responses = new ConcurrentHashMap<>();
    private final List<LlmRequest> calls = Collections.synchronizedList(new ArrayList<>());

    public FakeResponseRegistry(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** Canned raw text / JSON for a purpose. */
    public FakeResponseRegistry stub(String purpose, String response) {
        responses.put(purpose, req -> response);
        return this;
    }

    /** Canned response computed from the request. */
    public FakeResponseRegistry stub(String purpose, Function<LlmRequest, String> fn) {
        responses.put(purpose, fn);
        return this;
    }

    /** Canned object serialised to JSON (for completeJson / vision). */
    public FakeResponseRegistry stubObject(String purpose, Object value) {
        try {
            String json = objectMapper.writeValueAsString(value);
            return stub(purpose, json);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(e);
        }
    }

    public String respond(LlmRequest req) {
        calls.add(req);
        Function<LlmRequest, String> fn = responses.get(req.purpose());
        if (fn == null) {
            throw new IllegalStateException("No canned response registered for purpose '" + req.purpose()
                    + "'. Call FakeResponseRegistry.stub(purpose, json) in the test.");
        }
        return fn.apply(req);
    }

    public boolean has(String purpose) {
        return responses.containsKey(purpose);
    }

    public List<LlmRequest> calls() {
        return List.copyOf(calls);
    }

    public List<LlmRequest> calls(String purpose) {
        return calls.stream().filter(c -> c.purpose().equals(purpose)).toList();
    }

    public void reset() {
        responses.clear();
        calls.clear();
    }

    public ObjectMapper objectMapper() {
        return objectMapper;
    }
}
