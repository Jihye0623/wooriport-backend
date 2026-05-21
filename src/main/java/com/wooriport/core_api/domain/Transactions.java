package com.wooriport.core_api.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "transactions",
        indexes = {
                // 월간 리포트 집계 쿼리 성능용
                @Index(name = "idx_transactions_user_date",
                        columnList = "user_id, transaction_at"),
                // 이상 소비 감지 카테고리별 집계 성능용
                @Index(name = "idx_transactions_category",
                        columnList = "user_id, category, transaction_at")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Transactions {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private Users user;

    // 어떤 계좌에서 발생한 거래인지
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id", nullable = false)
    private Assets asset;

    // 양수 = 입금, 음수 = 출금
    // ex) 입금: 3000000 / 출금: -45000
    @Column(name = "amount", nullable = false)
    private Long amount;

    // 더미 스케줄러가 랜덤 배정: 식비, 교통, 쇼핑, 의료, 여가
    // 분류 후 업데이트 가능
    @Column(name = "category", length = 50)
    private String category;

    // 입금자명 또는 가맹점명
    // 월급 감지 시 "급여", "월급", 회사명 포함 여부 체크
    @Column(name = "sender_name", length = 100)
    private String senderName;

    // 실제 거래 발생 시각 (created_at과 구분)
    @Column(name = "transaction_at", nullable = false)
    private LocalDateTime transactionAt;

    // 비즈니스 메서드
    public boolean isExpense() {
        return this.amount < 0;
    }

    public boolean isIncome() {
        return this.amount > 0;
    }

    public Long getAbsAmount() {
        return Math.abs(this.amount);
    }
}
