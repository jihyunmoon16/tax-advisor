package com.moon.taxadvisor.service;

import com.moon.taxadvisor.domain.Portfolio;
import com.moon.taxadvisor.domain.RealizedGain;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TaxCalculationService {

    private static final BigDecimal TAX_RATE = new BigDecimal("0.22");

    private final PortfolioQueryService portfolioQueryService;

    public TaxPreview calculatePreview(String userId) {
        List<RealizedGain> realizedGains = portfolioQueryService.findRealizedGainEntities(userId);
        List<Portfolio> portfolios = portfolioQueryService.findPortfolioEntities(userId);

        BigDecimal totalRealizedGain = realizedGains.stream()
                .map(RealizedGain::getGainAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalUnrealizedLoss = portfolios.stream()
                .map(Portfolio::getUnrealizedGain)
                .filter(value -> value.signum() < 0)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal taxableBefore = floorToZero(totalRealizedGain);
        BigDecimal estimatedTaxBefore = taxableBefore.multiply(TAX_RATE).setScale(0, RoundingMode.HALF_UP);

        BigDecimal taxableAfterLossHarvest = floorToZero(totalRealizedGain.add(totalUnrealizedLoss));
        BigDecimal estimatedTaxAfter = taxableAfterLossHarvest.multiply(TAX_RATE).setScale(0, RoundingMode.HALF_UP);
        BigDecimal estimatedTaxSavings = floorToZero(estimatedTaxBefore.subtract(estimatedTaxAfter));

        return new TaxPreview(
                totalRealizedGain.setScale(0, RoundingMode.HALF_UP),
                totalUnrealizedLoss.setScale(0, RoundingMode.HALF_UP),
                estimatedTaxBefore,
                estimatedTaxAfter,
                estimatedTaxSavings
        );
    }

    private BigDecimal floorToZero(BigDecimal value) {
        return value.signum() < 0 ? BigDecimal.ZERO : value;
    }

    public record TaxPreview(
            BigDecimal realizedGain,
            BigDecimal unrealizedLoss,
            BigDecimal estimatedTaxBeforeHarvest,
            BigDecimal estimatedTaxAfterHarvest,
            BigDecimal estimatedTaxSavings
    ) {
    }
}
