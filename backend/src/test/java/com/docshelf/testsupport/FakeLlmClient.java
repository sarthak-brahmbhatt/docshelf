// LlmClient test double returning canned responses by purpose (no network)
package com.docshelf.testsupport;

import com.docshelf.llm.LlmClient;
import com.docshelf.llm.LlmRequest;
import java.io.IOException;

public class FakeLlmClient implements LlmClient {

    private final FakeResponseRegistry registry;

    public FakeLlmClient(FakeResponseRegistry registry) {
        this.registry = registry;
    }

    @Override
    public String complete(LlmRequest req) {
        return registry.respond(req);
    }

    @Override
    public <T> T completeJson(LlmRequest req, Class<T> schemaType) {
        String json = registry.respond(req);
        try {
            return registry.objectMapper().readValue(json, schemaType);
        } catch (IOException e) {
            throw new IllegalStateException("Canned response for '" + req.purpose() + "' is not valid "
                    + schemaType.getSimpleName() + " JSON", e);
        }
    }
}
