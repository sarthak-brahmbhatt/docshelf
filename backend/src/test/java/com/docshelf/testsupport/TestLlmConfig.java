// @Primary fake LLM / embedding / transcription / vision beans for integration tests (import via AbstractIntegrationTest)
package com.docshelf.testsupport;

import com.docshelf.llm.EmbeddingClient;
import com.docshelf.llm.LlmClient;
import com.docshelf.llm.TranscriptionClient;
import com.docshelf.llm.VisionClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration
public class TestLlmConfig {

    @Bean
    public FakeResponseRegistry fakeResponseRegistry(ObjectMapper objectMapper) {
        return new FakeResponseRegistry(objectMapper);
    }

    @Bean
    @Primary
    public LlmClient fakeLlmClient(FakeResponseRegistry registry) {
        return new FakeLlmClient(registry);
    }

    @Bean
    @Primary
    public EmbeddingClient fakeEmbeddingClient() {
        return new FakeEmbeddingClient();
    }

    @Bean
    @Primary
    public TranscriptionClient fakeTranscriptionClient(FakeResponseRegistry registry) {
        return new FakeTranscriptionClient(registry);
    }

    @Bean
    @Primary
    public VisionClient fakeVisionClient(FakeResponseRegistry registry) {
        return new FakeVisionClient(registry);
    }
}
