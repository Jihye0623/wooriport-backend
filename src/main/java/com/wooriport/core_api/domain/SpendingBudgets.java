package com.wooriport.core_api.domain;

import com.wooriport.core_api.domain.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "spending_budgets")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class SpendingBudgets extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private Users user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    // 식비, 문화, 교통 등
    @Column(name = "category", nullable = false, length = 50)
    private String category;

    // 월 예산 금액
    @Column(name = "amount", nullable = false)
    private Long amount;

    // 비율 (식비 80%, 문화 20% 등)
    @Column(name = "ratio")
    private Integer ratio;

    // 어느 달 예산인지
    @Column(name = "year", nullable = false)
    private Integer year;

    @Column(name = "month", nullable = false)
    private Integer month;

    public void updateAmount(Long amount) {
        this.amount = amount;
    }
}