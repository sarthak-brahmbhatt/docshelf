// Spring Data repository for prescription (PK = document_id)
package com.docshelf.medical;

import com.docshelf.medical.entity.Prescription;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PrescriptionRepository extends JpaRepository<Prescription, UUID> {

    List<Prescription> findByMemberIdOrderByVisitDateDesc(UUID memberId);

    List<Prescription> findAllByOrderByVisitDateDesc();

    List<Prescription> findByFollowUpDateIsNotNull();
}
