// OpenAI client bean: a real client when OPENAI_API_KEY is set, otherwise a proxy that fails fast with UpstreamException
package com.docshelf.config;

import com.docshelf.common.UpstreamException;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenAiConfig {

    private static final Logger log = LoggerFactory.getLogger(OpenAiConfig.class);
    public static final String NOT_CONFIGURED = "OPENAI_API_KEY not configured";

    @Bean
    public OpenAIClient openAIClient(DocshelfProperties props) {
        DocshelfProperties.OpenAi cfg = props.openai();
        if (!cfg.configured()) {
            log.warn("OPENAI_API_KEY is not set: LLM, embedding, transcription and vision features are disabled "
                    + "(rule-based extraction still works)");
            return unconfiguredClient();
        }
        log.info("OpenAI client configured (chat={}, extract={}, embeddings={}, transcription={})",
                cfg.chatModel(), cfg.extractModel(), cfg.embeddingModel(), cfg.transcriptionModel());
        return OpenAIOkHttpClient.builder()
                .apiKey(cfg.apiKey())
                .timeout(Duration.ofSeconds(cfg.timeoutSeconds()))
                .maxRetries(cfg.maxRetries())
                .build();
    }

    /** An OpenAIClient whose every service call throws {@link UpstreamException}; toString/hashCode/equals still work. */
    static OpenAIClient unconfiguredClient() {
        return (OpenAIClient) Proxy.newProxyInstance(
                OpenAIClient.class.getClassLoader(),
                new Class<?>[] {OpenAIClient.class},
                (proxy, method, args) -> {
                    if (isObjectMethod(method)) {
                        return switch (method.getName()) {
                            case "toString" -> "OpenAIClient[unconfigured]";
                            case "hashCode" -> System.identityHashCode(proxy);
                            case "equals" -> proxy == args[0];
                            default -> throw new UnsupportedOperationException(method.getName());
                        };
                    }
                    if ("close".equals(method.getName())) {
                        return null;
                    }
                    throw new UpstreamException(NOT_CONFIGURED);
                });
    }

    private static boolean isObjectMethod(Method method) {
        return method.getDeclaringClass() == Object.class;
    }

    /** Helper for tests / diagnostics: unwraps reflective invocation failures. */
    static Throwable unwrap(Throwable t) {
        return t instanceof InvocationTargetException ite && ite.getCause() != null ? ite.getCause() : t;
    }
}
