// Spring Data repository for ingestion_job (claiming uses FOR UPDATE SKIP LOCKED)
package com.docshelf.document;

import com.docshelf.document.entity.IngestionJob;
import com.docshelf.common.JobStatus;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IngestionJobRepository extends JpaRepository<IngestionJob, Long> {

    @Query(value = "SELECT * FROM ingestion_job WHERE status = 'QUEUED' AND run_after <= :now ORDER BY run_after, id LIMIT :limit FOR UPDATE SKIP LOCKED", nativeQuery = true)
    List<IngestionJob> claimable(@Param("now") Instant now, @Param("limit") int limit);

    List<IngestionJob> findByDocumentIdOrderByIdDesc(UUID documentId);

    boolean existsByDocumentIdAndStatusIn(UUID documentId, Collection<JobStatus> statuses);
}
