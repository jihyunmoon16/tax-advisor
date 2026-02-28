package com.moon.taxadvisor.repository;

import com.moon.taxadvisor.domain.RealizedGain;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RealizedGainRepository extends JpaRepository<RealizedGain, Long> {
    List<RealizedGain> findByUserId(String userId);
}
