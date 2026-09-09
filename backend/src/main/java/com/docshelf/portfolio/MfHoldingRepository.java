// Spring Data repository for mf_holding
package com.docshelf.portfolio;

import com.docshelf.portfolio.entity.MfHolding;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MfHoldingRepository extends JpaRepository<MfHolding, UUID> {

    List<MfHolding> findByFolioId(UUID folioId);

    Optional<MfHolding> findByFolioIdAndIsin(UUID folioId, String isin);

    List<MfHolding> findByIsin(String isin);
}
