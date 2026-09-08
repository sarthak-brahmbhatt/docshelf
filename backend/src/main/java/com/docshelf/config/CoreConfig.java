// Core beans shared by every module: Clock (Asia/Kolkata), Jackson customisation, scheduling and async enablement
package com.docshelf.config;

import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Clock;
import java.time.ZoneId;
import java.util.concurrent.Executor;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableScheduling
@EnableAsync
public class CoreConfig {

    public static final String ASYNC_EXECUTOR = "docshelfTaskExecutor";

    @Bean
    public ZoneId docshelfZone(DocshelfProperties props) {
        return ZoneId.of(props.timezone());
    }

    @Bean
    public Clock clock(ZoneId docshelfZone) {
        return Clock.system(docshelfZone);
    }

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer docshelfJacksonCustomizer() {
        return builder -> builder
                .modules(new JavaTimeModule(), new Jdk8Module())
                .featuresToDisable(
                        com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS,
                        com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    /** General-purpose async executor; use {@code @Async(CoreConfig.ASYNC_EXECUTOR)} for background work. */
    @Bean(name = ASYNC_EXECUTOR)
    public Executor docshelfTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("docshelf-async-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
