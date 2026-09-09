// Spring Data repository for llm_call
package com.docshelf.llm;

import com.docshelf.llm.entity.LlmCall;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LlmCallRepository extends JpaRepository<LlmCall, Long> {

    @Query("SELECT c.purpose AS purpose, CAST(c.createdAt AS date) AS day, SUM(COALESCE(c.inputTokens, 0)) AS inputTokens, SUM(COALESCE(c.outputTokens, 0)) AS outputTokens, COUNT(c) AS calls FROM LlmCall c WHERE c.createdAt >= :since GROUP BY c.purpose, CAST(c.createdAt AS date) ORDER BY day DESC, purpose")
    List<UsageRow> usageSince(@Param("since") Instant since);

    interface UsageRow {
        String getPurpose();

        java.time.LocalDate getDay();

        Long getInputTokens();

        Long getOutputTokens();

        Long getCalls();
    }
}
