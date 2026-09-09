// Spring Data repository for id_document (PK = document_id)
package com.docshelf.identity;

import com.docshelf.identity.entity.IdDocument;
import com.docshelf.identity.entity.IdType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IdDocumentRepository extends JpaRepository<IdDocument, UUID> {

    List<IdDocument> findByMemberId(UUID memberId);

    List<IdDocument> findByIdType(IdType idType);

    List<IdDocument> findByIdTypeAndExpiryDateIsNotNull(IdType idType);

    Optional<IdDocument> findFirstByNumberHmac(String numberHmac);
}
