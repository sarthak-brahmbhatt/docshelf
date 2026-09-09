// Spring Data repository for mf_transaction
package com.docshelf.portfolio;

import com.docshelf.portfolio.entity.MfTransaction;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MfTransactionRepository extends JpaRepository<MfTransaction, Long> {

    boolean existsByDedupeKey(String dedupeKey);

    List<MfTransaction> findByFolioIdAndIsinOrderByTxnDateAscIdAsc(UUID folioId, String isin);

    List<MfTransaction> findByIsinOrderByTxnDateAscIdAsc(String isin);
}
