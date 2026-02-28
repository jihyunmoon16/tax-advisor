package com.moon.taxadvisor.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.math.RoundingMode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "portfolio")
@Getter
@Setter
@NoArgsConstructor
public class Portfolio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 2)
    private Market market;

    @Column(nullable = false)
    private String stockName;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal averagePrice;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal currentPrice;

    @Column(nullable = false)
    private Long quantity;

    public BigDecimal getUnrealizedGain() {
        return currentPrice.subtract(averagePrice).multiply(BigDecimal.valueOf(quantity));
    }

    public BigDecimal getUnrealizedRatePercent() {
        if (averagePrice == null || averagePrice.signum() == 0) {
            return BigDecimal.ZERO;
        }
        return currentPrice
                .subtract(averagePrice)
                .divide(averagePrice, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
    }
}
