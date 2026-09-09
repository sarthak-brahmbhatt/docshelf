// Spring Data repository for document
package com.docshelf.document;

import com.docshelf.document.entity.Document;
import com.docshelf.extract.DocStatus;
import com.docshelf.extract.DocType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentRepository extends JpaRepository<Document, UUID> {

    Optional<Document> findBySha256(String sha256);

    List<Document> findByMemberId(UUID memberId);

    List<Document> findByDocType(DocType docType);

    List<Document> findByDocTypeAndStatusIn(DocType docType, Collection<DocStatus> statuses);

    Page<Document> findByStatus(DocStatus status, Pageable pageable);

    long countByMemberId(UUID memberId);
}
