// Spring Data repository for scheme_master (PK = isin)
package com.docshelf.portfolio;

import com.docshelf.portfolio.entity.SchemeMaster;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SchemeMasterRepository extends JpaRepository<SchemeMaster, String> {

    Optional<SchemeMaster> findByAmfiCode(String amfiCode);

    List<SchemeMaster> findByCategory(String category);
}
