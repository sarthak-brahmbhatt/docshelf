// Spring Data repository for benchmark_history
package com.docshelf.portfolio;

import com.docshelf.portfolio.entity.BenchmarkHistory;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BenchmarkHistoryRepository extends JpaRepository<BenchmarkHistory, BenchmarkHistory.Key> {

    Optional<BenchmarkHistory> findFirstByIndexNameAndPriceDateLessThanEqualOrderByPriceDateDesc(String indexName, LocalDate date);

    List<BenchmarkHistory> findByIndexNameOrderByPriceDateAsc(String indexName);
}
