// One text completion request: purpose (for accounting and test stubbing), prompts, output cap, optional doc/session ids
package com.docshelf.llm;

import java.util.UUID;

public record LlmRequest(
        String purpose,
        String systemPrompt,
        String userText,
        int maxOutputTokens,
        UUID documentId,
        UUID sessionId) {

    public static final int DEFAULT_MAX_OUTPUT_TOKENS = 1500;

    public LlmRequest {
        if (purpose == null || purpose.isBlank()) {
            throw new IllegalArgumentException("purpose is required");
        }
        if (maxOutputTokens <= 0) {
            maxOutputTokens = DEFAULT_MAX_OUTPUT_TOKENS;
        }
    }

    public static LlmRequest of(String purpose, String systemPrompt, String userText) {
        return new LlmRequest(purpose, systemPrompt, userText, DEFAULT_MAX_OUTPUT_TOKENS, null, null);
    }

    public static LlmRequest of(String purpose, String systemPrompt, String userText, int maxOutputTokens) {
        return new LlmRequest(purpose, systemPrompt, userText, maxOutputTokens, null, null);
    }

    public LlmRequest withDocument(UUID documentId) {
        return new LlmRequest(purpose, systemPrompt, userText, maxOutputTokens, documentId, sessionId);
    }

    public LlmRequest withSession(UUID sessionId) {
        return new LlmRequest(purpose, systemPrompt, userText, maxOutputTokens, documentId, sessionId);
    }
}
