// Typed binding of every docshelf.* property (see application.yml); injected wherever configuration is needed
package com.docshelf.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "docshelf")
public record DocshelfProperties(
        @NotBlank String masterKey,
        @NotBlank String apiToken,
        @DefaultValue("./data/blobs") String blobDir,
        @DefaultValue("200") int maxUploadMb,
        @DefaultValue("Asia/Kolkata") String timezone,
        @DefaultValue OpenAi openai,
        @DefaultValue Mail mail,
        @DefaultValue WhatsApp whatsapp) {

    public record OpenAi(
            @DefaultValue("") String apiKey,
            @DefaultValue("gpt-4.1") String chatModel,
            @DefaultValue("gpt-4.1-mini") String extractModel,
            @DefaultValue("text-embedding-3-small") String embeddingModel,
            @DefaultValue("gpt-4o-transcribe") String transcriptionModel,
            @DefaultValue("true") boolean visionEnabled,
            @DefaultValue("120") int timeoutSeconds,
            @DefaultValue("2") int maxRetries) {

        /** True when a non-blank API key is configured (LLM features are otherwise degraded). */
        public boolean configured() {
            return apiKey != null && !apiKey.isBlank();
        }
    }

    public record Mail(
            @DefaultValue("docshelf@localhost") String from,
            @DefaultValue("") String to) {
    }

    public record WhatsApp(
            @DefaultValue("STUB") Provider provider,
            @DefaultValue("") String phoneNumberId,
            @DefaultValue("") String token,
            @DefaultValue("docshelf_document") String templateName) {

        public enum Provider { STUB, META }
    }
}
