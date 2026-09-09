// Spring Data repository for insurance_policy (PK = document_id)
package com.docshelf.insurance;

import com.docshelf.insurance.entity.InsurancePolicy;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InsurancePolicyRepository extends JpaRepository<InsurancePolicy, UUID> {

    List<InsurancePolicy> findByMemberId(UUID memberId);

    List<InsurancePolicy> findByPolicyExpiryIsNotNull();

    List<InsurancePolicy> findByPremiumDueDateIsNotNull();
}
