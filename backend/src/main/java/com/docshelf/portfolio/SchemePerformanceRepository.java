// Spring Data repository for scheme_performance
package com.docshelf.portfolio;

import com.docshelf.portfolio.entity.SchemePerformance;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SchemePerformanceRepository extends JpaRepository<SchemePerformance, SchemePerformance.Key> {

    Optional<SchemePerformance> findFirstByIsinOrderByAsOfDateDesc(String isin);

    List<SchemePerformance> findByAsOfDate(LocalDate asOfDate);
}
