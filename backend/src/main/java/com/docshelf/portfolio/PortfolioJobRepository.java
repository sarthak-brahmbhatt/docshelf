// Spring Data repository for portfolio_job
package com.docshelf.portfolio;

import com.docshelf.portfolio.entity.PortfolioJob;
import com.docshelf.common.JobStatus;
import com.docshelf.portfolio.entity.PortfolioJobKind;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PortfolioJobRepository extends JpaRepository<PortfolioJob, Long> {

    boolean existsByKindAndStatusIn(PortfolioJobKind kind, Collection<JobStatus> statuses);

    List<PortfolioJob> findTop20ByOrderByIdDesc();
}
