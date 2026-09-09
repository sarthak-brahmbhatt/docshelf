// Text completion over the OpenAI Responses API; completeJson enforces a strict JSON schema derived from the record class
package com.docshelf.llm;

public interface LlmClient {

    String complete(LlmRequest req);

    <T> T completeJson(LlmRequest req, Class<T> schemaType);
}
