// Spring Data repository for document_summary
package com.docshelf.document;

import com.docshelf.document.entity.DocumentSummary;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentSummaryRepository extends JpaRepository<DocumentSummary, UUID> {
}
