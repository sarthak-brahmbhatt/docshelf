// JSONB / array / enum mappings round trip through JPA; AuditService inserts in its own transaction and rows are immutable
package com.docshelf.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.docshelf.AbstractIntegrationTest;
import com.docshelf.audit.entity.AuditAction;
import com.docshelf.audit.entity.AuditLog;
import com.docshelf.audit.entity.AuditOrigin;
import com.docshelf.document.DocumentEventRepository;
import com.docshelf.document.DocumentRepository;
import com.docshelf.document.DocumentSummaryRepository;
import com.docshelf.document.entity.Document;
import com.docshelf.document.entity.DocumentEvent;
import com.docshelf.document.entity.DocumentSummary;
import com.docshelf.document.entity.EventKind;
import com.docshelf.extract.DocStatus;
import com.docshelf.extract.DocType;
import com.docshelf.extract.FieldMeta;
import com.docshelf.insurance.InsurancePolicyRepository;
import com.docshelf.insurance.entity.InsurancePolicy;
import com.docshelf.insurance.entity.PolicyType;
import com.docshelf.llm.LlmCallLogService;
import com.docshelf.llm.LlmCallRepository;
import com.docshelf.messaging.StubWhatsAppGateway;
import com.docshelf.messaging.entity.MessageStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class EntityRoundTripTest extends AbstractIntegrationTest {

    @Autowired DocumentRepository documents;
    @Autowired InsurancePolicyRepository policies;
    @Autowired DocumentSummaryRepository summaries;
    @Autowired DocumentEventRepository events;
    @Autowired AuditService audit;
    @Autowired LlmCallLogService llmCalls;
    @Autowired LlmCallRepository llmCallRepo;
    @Autowired StubWhatsAppGateway whatsApp;
    @Autowired JdbcTemplate jdbc;

    private Document doc() {
        Document d = new Document();
        d.setTitle("Policy");
        d.setDocType(DocType.INSURANCE_POLICY);
        d.setStatus(DocStatus.READY);
        d.setOriginalFilename("p.pdf");
        d.setMimeType("application/pdf");
        d.setSizeBytes(1);
        d.setSha256(UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", ""));
        d.setStoragePath("x");
        d.setDekWrapped(new byte[40]);
        d.setKekId("env-v1");
        d.setNeedsReviewReasons(new ArrayList<>(List.of("low confidence")));
        d.setClassifierConfidence(new BigDecimal("0.975"));
        return documents.saveAndFlush(d);
    }

    @Test
    void jsonArrayAndEnumColumnsRoundTrip() {
        Document d = doc();
        assertThat(documents.findById(d.getId()).orElseThrow().getNeedsReviewReasons()).containsExactly("low confidence");

        InsurancePolicy p = new InsurancePolicy();
        p.setDocumentId(d.getId());
        p.setInsurer("Star Health");
        p.setPolicyType(PolicyType.HEALTH);
        p.setSumAssured(new BigDecimal("1000000.00"));
        p.setPolicyExpiry(LocalDate.of(2027, 3, 14));
        p.setInsuredMembers(List.of(Map.of("name", "Sarthak", "memberId", "fm_1")));
        p.setFieldMeta(Map.of("insurer", FieldMeta.llm(0.9, 1), "sumAssured", FieldMeta.rule(1.0, 2)));
        policies.saveAndFlush(p);
        InsurancePolicy loaded = policies.findById(d.getId()).orElseThrow();
        assertThat(loaded.getFieldMeta().get("insurer").source()).isEqualTo(FieldMeta.Source.LLM);
        assertThat(loaded.getFieldMeta().get("sumAssured").page()).isEqualTo(2);
        assertThat(loaded.getInsuredMembers().get(0)).containsEntry("name", "Sarthak");

        DocumentSummary s = new DocumentSummary();
        s.setDocumentId(d.getId());
        s.setTitle("Star Health policy");
        s.setSummary("Family floater");
        s.setTags(List.of("insurance", "health"));
        s.setPeople(List.of(Map.of("name", "Sunita", "role", "policyholder")));
        s.setKeyFacts(Map.of("Sum insured", "10,00,000"));
        summaries.saveAndFlush(s);
        assertThat(summaries.findById(d.getId()).orElseThrow().getTags()).containsExactly("insurance", "health");

        DocumentEvent e = new DocumentEvent();
        e.setDocumentId(d.getId());
        e.setKind(EventKind.EXPIRY);
        e.setLabel("Policy expiry");
        e.setEventAt(Instant.parse("2027-03-14T00:00:00Z"));
        e.setLeadDays(List.of(30, 7, 1));
        events.saveAndFlush(e);
        assertThat(events.findByDocumentIdOrderByEventAtAsc(d.getId()).get(0).getLeadDays()).containsExactly(30, 7, 1);

        documents.delete(d);
    }

    @Test
    void auditIsAppendOnlyAndLlmCallsAreLogged() {
        AuditLog row = audit.record(AuditEvent.of(AuditAction.REVEAL_FIELD, AuditOrigin.UI)
                .document(UUID.randomUUID(), "Aadhaar - Sunita").field("number").detail("masked", "XXXX XXXX 1234"));
        assertThat(row.getId()).isNotNull();
        assertThat(row.getActor()).isEqualTo("owner");
        assertThat(row.getDetails()).containsEntry("masked", "XXXX XXXX 1234");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM audit_log WHERE id = ?", row.getId()))
                .hasMessageContaining("append-only");

        llmCalls.log(new LlmCallLogService.Entry("classify", "gpt-4.1-mini", null, null, 120, 30,
                Map.of("AADHAAR", 1), false, 900, LlmCallLogService.STATUS_OK, null, "resp_1"));
        assertThat(llmCallRepo.usageSince(Instant.now().minusSeconds(3600)))
                .anySatisfy(u -> {
                    assertThat(u.getPurpose()).isEqualTo("classify");
                    assertThat(u.getInputTokens()).isGreaterThanOrEqualTo(120);
                });

        var result = whatsApp.sendDocument("+919876543210", "docshelf_document", List.of("Aadhaar"), new byte[3], "a.pdf");
        assertThat(result.status()).isEqualTo(MessageStatus.SIMULATED);
        assertThat(result.providerMessageId()).startsWith("SIMULATED-");
        assertThat(whatsApp.simulated()).anyMatch(s -> s.filename().equals("a.pdf"));
    }
}
