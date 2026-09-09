// Shared helpers for the OpenAI implementations: model selection, error mapping, output extraction, schema conversion
package com.docshelf.llm;

import com.docshelf.common.UpstreamException;
import com.openai.core.JsonValue;
import com.openai.errors.OpenAIIoException;
import com.openai.errors.OpenAIServiceException;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseFormatTextJsonSchemaConfig;
import com.openai.models.responses.ResponseOutputItem;
import com.openai.models.responses.ResponseOutputMessage;
import com.openai.models.responses.ResponseTextConfig;
import java.util.Map;

final class OpenAiSupport {

    private OpenAiSupport() {
    }

    /** Concatenated text of all output messages; refusals raise an UpstreamException. */
    static String outputText(Response response) {
        StringBuilder sb = new StringBuilder();
        for (ResponseOutputItem item : response.output()) {
            if (!item.isMessage()) {
                continue;
            }
            ResponseOutputMessage msg = item.asMessage();
            for (ResponseOutputMessage.Content content : msg.content()) {
                if (content.isOutputText()) {
                    sb.append(content.asOutputText().text());
                } else if (content.isRefusal()) {
                    throw new UpstreamException("Model refused the request: " + content.asRefusal().refusal());
                }
            }
        }
        if (response.incompleteDetails().isPresent()) {
            throw new UpstreamException("Model response incomplete: "
                    + response.incompleteDetails().get().reason().map(Object::toString).orElse("unknown"));
        }
        return sb.toString();
    }

    static Integer inputTokens(Response response) {
        return response.usage().map(u -> (int) u.inputTokens()).orElse(null);
    }

    static Integer outputTokens(Response response) {
        return response.usage().map(u -> (int) u.outputTokens()).orElse(null);
    }

    static ResponseTextConfig strictJsonFormat(Class<?> schemaType) {
        Map<String, Object> schema = JsonSchemaGenerator.generate(schemaType);
        ResponseFormatTextJsonSchemaConfig.Schema.Builder sb = ResponseFormatTextJsonSchemaConfig.Schema.builder();
        for (Map.Entry<String, Object> e : schema.entrySet()) {
            sb.putAdditionalProperty(e.getKey(), JsonValue.from(e.getValue()));
        }
        return ResponseTextConfig.builder()
                .format(ResponseFormatTextJsonSchemaConfig.builder()
                        .name(JsonSchemaGenerator.schemaName(schemaType))
                        .schema(sb.build())
                        .strict(true)
                        .build())
                .build();
    }

    /** Maps SDK exceptions to UpstreamException, marking 429 / 5xx / IO as retryable. Never includes request text. */
    static UpstreamException mapError(String what, RuntimeException e) {
        if (e instanceof UpstreamException ue) {
            return ue;
        }
        if (e instanceof OpenAIServiceException se) {
            int code = se.statusCode();
            boolean retryable = code == 429 || code >= 500;
            return new UpstreamException("OpenAI " + what + " failed with HTTP " + code, e, retryable);
        }
        if (e instanceof OpenAIIoException) {
            return new UpstreamException("OpenAI " + what + " failed: I/O error", e, true);
        }
        return new UpstreamException("OpenAI " + what + " failed: " + e.getClass().getSimpleName(), e, false);
    }

    static String safeMessage(Throwable t) {
        String m = t.getMessage();
        if (m == null) {
            return t.getClass().getSimpleName();
        }
        return m.length() > 500 ? m.substring(0, 500) : m;
    }
}
