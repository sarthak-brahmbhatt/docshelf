// Base class for Spring Boot integration tests: one shared pgvector Testcontainer, test profile, fake LLM and mail beans
package com.docshelf;

import com.docshelf.testsupport.TestLlmConfig;
import com.docshelf.testsupport.TestMessagingConfig;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@ActiveProfiles("test")
@Import({TestLlmConfig.class, TestMessagingConfig.class})
public abstract class AbstractIntegrationTest {

    public static final String IMAGE = "pgvector/pgvector:pg16";

    /** Singleton container shared by every test class in the JVM (started once, reused; Ryuk cleans up). */
    static final PostgreSQLContainer<?> POSTGRES;

    static {
        POSTGRES = new PostgreSQLContainer<>(DockerImageName.parse(IMAGE).asCompatibleSubstituteFor("postgres"))
                .withDatabaseName("docshelf_test")
                .withUsername("docshelf")
                .withPassword("docshelf");
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("docshelf.blob-dir", () -> {
            try {
                return Files.createTempDirectory("docshelf-test-blobs").toString();
            } catch (java.io.IOException e) {
                return Path.of("target", "test-blobs").toString();
            }
        });
    }
}
