// JsonSchemaGenerator: nested records, enums, lists, Optional/@SchemaNullable, LocalDate, strict-mode invariants
package com.docshelf.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class JsonSchemaGeneratorTest {

    enum Freq { MONTHLY, YEARLY }

    record Insured(String name, @SchemaNullable String memberId) {
    }

    @SchemaDescription("An insurance policy")
    record Policy(
            @SchemaDescription("Insurer name") String insurer,
            BigDecimal sumAssured,
            int pages,
            boolean active,
            Freq frequency,
            Optional<Freq> altFrequency,
            LocalDate expiry,
            @SchemaNullable LocalDate start,
            List<Insured> insured,
            Optional<List<String>> tags) {
    }

    record Loop(String a, Map<String, String> m) {
    }

    @Test
    @SuppressWarnings("unchecked")
    void nestedRecordSchema() {
        Map<String, Object> s = JsonSchemaGenerator.generate(Policy.class);
        assertThat(s).containsEntry("type", "object").containsEntry("additionalProperties", false)
                .containsEntry("description", "An insurance policy");
        Map<String, Object> props = (Map<String, Object>) s.get("properties");
        assertThat((List<String>) s.get("required")).containsExactly("insurer", "sumAssured", "pages", "active",
                "frequency", "altFrequency", "expiry", "start", "insured", "tags");

        assertThat((Map<String, Object>) props.get("insurer")).containsEntry("type", "string")
                .containsEntry("description", "Insurer name");
        assertThat((Map<String, Object>) props.get("sumAssured")).containsEntry("type", "number");
        assertThat((Map<String, Object>) props.get("pages")).containsEntry("type", "integer");
        assertThat((Map<String, Object>) props.get("active")).containsEntry("type", "boolean");

        Map<String, Object> freq = (Map<String, Object>) props.get("frequency");
        assertThat(freq).containsEntry("type", "string");
        assertThat((List<Object>) freq.get("enum")).containsExactly("MONTHLY", "YEARLY");

        Map<String, Object> alt = (Map<String, Object>) props.get("altFrequency");
        assertThat((List<Object>) alt.get("type")).containsExactly("string", "null");
        assertThat((List<Object>) alt.get("enum")).containsExactly("MONTHLY", "YEARLY", null);

        assertThat((Map<String, Object>) props.get("expiry")).containsEntry("type", "string")
                .containsEntry("description", "ISO-8601 date, yyyy-MM-dd");
        assertThat((List<Object>) ((Map<String, Object>) props.get("start")).get("type")).containsExactly("string", "null");

        Map<String, Object> insured = (Map<String, Object>) props.get("insured");
        assertThat(insured).containsEntry("type", "array");
        Map<String, Object> item = (Map<String, Object>) insured.get("items");
        assertThat(item).containsEntry("type", "object").containsEntry("additionalProperties", false);
        assertThat((List<String>) item.get("required")).containsExactly("name", "memberId");
        Map<String, Object> memberId = (Map<String, Object>) ((Map<String, Object>) item.get("properties")).get("memberId");
        assertThat((List<Object>) memberId.get("type")).containsExactly("string", "null");

        Map<String, Object> tags = (Map<String, Object>) props.get("tags");
        assertThat((List<Object>) tags.get("type")).containsExactly("array", "null");
        assertThat((Map<String, Object>) tags.get("items")).containsEntry("type", "string");
    }

    @Test
    void unsupportedTypesRejected() {
        assertThatThrownBy(() -> JsonSchemaGenerator.generate(Loop.class)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Map");
        assertThatThrownBy(() -> JsonSchemaGenerator.generate(String.class)).isInstanceOf(IllegalArgumentException.class);
        assertThat(JsonSchemaGenerator.schemaName(Policy.class)).isEqualTo("Policy");
    }
}
