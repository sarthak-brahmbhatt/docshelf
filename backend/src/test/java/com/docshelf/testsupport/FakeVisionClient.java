// VisionClient test double: canned JSON keyed by 'vision:<SchemaType>' or the generic 'vision' purpose
package com.docshelf.testsupport;

import com.docshelf.llm.LlmRequest;
import com.docshelf.llm.VisionClient;
import java.io.IOException;

public class FakeVisionClient implements VisionClient {

    private final FakeResponseRegistry registry;

    public FakeVisionClient(FakeResponseRegistry registry) {
        this.registry = registry;
    }

    @Override
    public <T> T extractFromImage(byte[] image, String mimeType, String instructions, Class<T> schemaType) {
        String specific = FakeResponseRegistry.VISION_PURPOSE + ":" + schemaType.getSimpleName();
        String purpose = registry.has(specific) ? specific : FakeResponseRegistry.VISION_PURPOSE;
        String json = registry.respond(LlmRequest.of(purpose, instructions,
                "image " + mimeType + " bytes=" + (image == null ? 0 : image.length)));
        try {
            return registry.objectMapper().readValue(json, schemaType);
        } catch (IOException e) {
            throw new IllegalStateException("Canned vision response is not valid " + schemaType.getSimpleName(), e);
        }
    }
}
