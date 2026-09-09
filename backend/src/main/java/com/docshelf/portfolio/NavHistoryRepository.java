// Spring Data repository for nav_history
package com.docshelf.portfolio;

import com.docshelf.portfolio.entity.NavHistory;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NavHistoryRepository extends JpaRepository<NavHistory, NavHistory.Key> {

    List<NavHistory> findByIsinAndNavDateBetweenOrderByNavDateAsc(String isin, LocalDate from, LocalDate to);

    Optional<NavHistory> findFirstByIsinOrderByNavDateDesc(String isin);

    Optional<NavHistory> findFirstByIsinAndNavDateLessThanEqualOrderByNavDateDesc(String isin, LocalDate date);
}
