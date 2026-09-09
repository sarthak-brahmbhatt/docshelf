// Spring Data repository for mf_folio
package com.docshelf.portfolio;

import com.docshelf.portfolio.entity.MfFolio;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MfFolioRepository extends JpaRepository<MfFolio, UUID> {

    Optional<MfFolio> findByFolioNoAndAmc(String folioNo, String amc);

    List<MfFolio> findByMemberId(UUID memberId);
}
