// Boots the full context against pgvector: Flyway migrates V1, Hibernate ddl-auto=validate passes, seeds are present
package com.docshelf;

import static org.assertj.core.api.Assertions.assertThat;

import com.docshelf.config.DocshelfProperties;
import com.docshelf.llm.LlmClient;
import com.docshelf.member.FamilyMemberRepository;
import com.docshelf.reminder.ReminderRuleRepository;
import com.docshelf.settings.UserSettingsRepository;
import com.docshelf.testsupport.FakeLlmClient;
import java.time.Clock;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class ContextLoadsTest extends AbstractIntegrationTest {

    @Autowired JdbcTemplate jdbc;
    @Autowired FamilyMemberRepository members;
    @Autowired ReminderRuleRepository rules;
    @Autowired UserSettingsRepository settings;
    @Autowired DocshelfProperties props;
    @Autowired Clock clock;
    @Autowired LlmClient llmClient;

    @Test
    void flywayMigratedAndSeeded() {
        Integer applied = jdbc.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE success AND version = '1'", Integer.class);
        assertThat(applied).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pg_extension WHERE extname = 'vector'", Integer.class))
                .isEqualTo(1);
        assertThat(members.findByIsSelfTrue()).isPresent().get().extracting("fullName").isEqualTo("Me");
        assertThat(rules.findAll()).hasSize(8);
        assertThat(settings.get().getVoiceLanguage()).isEqualTo("en-IN");
    }

    @Test
    void configurationBound() {
        assertThat(props.apiToken()).isEqualTo("test-token");
        assertThat(props.openai().chatModel()).isEqualTo("gpt-4.1");
        assertThat(props.openai().configured()).isFalse();
        assertThat(clock.getZone()).isEqualTo(ZoneId.of("Asia/Kolkata"));
        assertThat(llmClient).isInstanceOf(FakeLlmClient.class);
    }
}
