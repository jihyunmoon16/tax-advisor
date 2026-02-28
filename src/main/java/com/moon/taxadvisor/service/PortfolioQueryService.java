package com.moon.taxadvisor.service;

import com.moon.taxadvisor.domain.Portfolio;
import com.moon.taxadvisor.domain.RealizedGain;
import com.moon.taxadvisor.repository.PortfolioRepository;
import com.moon.taxadvisor.repository.RealizedGainRepository;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class PortfolioQueryService {

    private static final DecimalFormat WON_FORMAT = new DecimalFormat("#,###");

    private final PortfolioRepository portfolioRepository;
    private final RealizedGainRepository realizedGainRepository;

    public List<Portfolio> findPortfolioEntities(String userId) {
        return portfolioRepository.findByUserId(userId);
    }

    public List<RealizedGain> findRealizedGainEntities(String userId) {
        return realizedGainRepository.findByUserId(userId);
    }

    public List<PortfolioView> getUserPortfolio(String userId) {
        log.info("AI가 사용자의 포트폴리오 조회를 요청했습니다. userId={}", userId);
        List<Portfolio> portfolios = findPortfolioEntities(userId);

        portfolios.stream()
                .filter(portfolio -> portfolio.getUnrealizedGain().signum() > 0)
                .max((left, right) -> left.getUnrealizedGain().compareTo(right.getUnrealizedGain()))
                .ifPresent(portfolio -> log.info(
                        "DB에서 실시간 수익 {} 원 감지 (종목: {})",
                        WON_FORMAT.format(portfolio.getUnrealizedGain()),
                        portfolio.getStockName()));

        return portfolios.stream()
                .map(portfolio -> new PortfolioView(
                        portfolio.getMarket().name(),
                        portfolio.getStockName(),
                        portfolio.getAveragePrice(),
                        portfolio.getCurrentPrice(),
                        portfolio.getQuantity(),
                        portfolio.getUnrealizedGain(),
                        portfolio.getUnrealizedRatePercent()
                ))
                .toList();
    }

    public RealizedGainView getRealizedGains(String userId) {
        log.info("AI가 확정 손익 조회를 요청했습니다. userId={}", userId);
        List<RealizedGain> gains = findRealizedGainEntities(userId);
        BigDecimal total = gains.stream()
                .map(RealizedGain::getGainAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        log.info("DB에서 확정 손익 합계 {} 원 조회", WON_FORMAT.format(total));

        return new RealizedGainView(
                total,
                gains.stream()
                        .map(gain -> new RealizedGainItem(
                                gain.getStockName(),
                                gain.getGainAmount(),
                                gain.getRealizedDate()
                        ))
                        .toList()
        );
    }

    public record PortfolioView(
            String market,
            String stockName,
            BigDecimal averagePrice,
            BigDecimal currentPrice,
            Long quantity,
            BigDecimal unrealizedGain,
            BigDecimal unrealizedRatePercent
    ) {
    }

    public record RealizedGainItem(
            String stockName,
            BigDecimal gainAmount,
            java.time.LocalDate realizedDate
    ) {
    }

    public record RealizedGainView(
            BigDecimal totalRealizedGain,
            List<RealizedGainItem> items
    ) {
    }
}
