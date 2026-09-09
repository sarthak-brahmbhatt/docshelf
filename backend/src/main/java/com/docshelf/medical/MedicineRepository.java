// Spring Data repository for medicine
package com.docshelf.medical;

import com.docshelf.medical.entity.Medicine;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MedicineRepository extends JpaRepository<Medicine, UUID> {

    List<Medicine> findByPrescriptionIdOrderByNameAsc(UUID prescriptionId);

    List<Medicine> findByMemberId(UUID memberId);

    List<Medicine> findByActiveTrueAndEndDateGreaterThanEqual(LocalDate today);

    List<Medicine> findByActiveTrueAndRefillDueDateIsNotNull();

    void deleteByPrescriptionId(UUID prescriptionId);
}
