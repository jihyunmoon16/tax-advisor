package com.moon.taxadvisor.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "realized_gain")
@Getter
@Setter
@NoArgsConstructor
public class RealizedGain {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String userId;

    @Column(nullable = false)
    private String stockName;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal gainAmount;

    @Column(nullable = false)
    private LocalDate realizedDate;
}
