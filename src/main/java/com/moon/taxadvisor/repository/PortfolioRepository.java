package com.moon.taxadvisor.repository;

import com.moon.taxadvisor.domain.Portfolio;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PortfolioRepository extends JpaRepository<Portfolio, Long> {
    List<Portfolio> findByUserId(String userId);
}
