// @Primary FakeEmailGateway for integration tests
package com.docshelf.testsupport;

import com.docshelf.messaging.EmailGateway;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration
public class TestMessagingConfig {

    @Bean
    @Primary
    public EmailGateway fakeEmailGateway() {
        return new FakeEmailGateway();
    }
}
