package com.wooriport.core_api.domain;

import com.wooriport.core_api.domain.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "master_portfolio_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class MasterPortfolioItems extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "master_id", nullable = false)
    private InvestorMasters master;

    @Column(name = "stock_name", nullable = false, length = 100)
    private String stockName;

    @Column(name = "change_rate", precision = 6, scale = 2)
    private BigDecimal changeRate;

    @Column(name = "shares_held")
    private Long sharesHeld;

    @Column(name = "prev_quarter_ratio", precision = 5, scale = 2)
    private BigDecimal prevQuarterRatio;

    @Column(name = "current_ratio", nullable = false, precision = 5, scale = 2)
    private BigDecimal currentRatio;

    @Column(name = "holding_months")
    private Integer holdingMonths;
}
