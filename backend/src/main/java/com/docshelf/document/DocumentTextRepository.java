// Spring Data repository for document_text (redacted page text)
package com.docshelf.document;

import com.docshelf.document.entity.DocumentText;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentTextRepository extends JpaRepository<DocumentText, DocumentText.Key> {

    List<DocumentText> findByDocumentIdOrderByPageNoAsc(UUID documentId);

    void deleteByDocumentId(UUID documentId);
}
