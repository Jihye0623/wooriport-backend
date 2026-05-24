package com.wooriport.core_api.domain;


import com.wooriport.core_api.domain.common.SoftDeleteEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "assets")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Assets extends SoftDeleteEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private Users user;

    // 금융 기관명
    @Column(name = "institution", nullable = false, length = 100)
    private String institution;

    @Column(name = "asset_number", length = 50)
    private String assetNumber;

    // "입출금 통장", "파킹 통장", "적금", "신용카드", "체크카드",
    // "채권", "예적금", "증권 계좌", "CMA"
    @Enumerated(EnumType.STRING)
    @Column(name = "asset_type")
    private AccountType assetType;

    // 카드명 또는 계좌 상품명 (신규 추가)
    // ex) "삼성 iD VISA", "현대카드 M", "우리 WON 파킹통장"
    @Column(name = "account_name", length = 100)
    private String accountName;

    @Column(name = "account_purpose", length = 100)
    private String accountPurpose;  // "생활비", "비상금", "여행 적금" 등 자유값

    @Column(name = "is_salary", nullable = false)
    @Builder.Default
    private Boolean isSalary = false;  // 급여 통장 여부

    // 마지막 동기화 시점의 잔액
    @Column(name = "balance", nullable = false)
    @Builder.Default
    private Long balance = 0L;

    @Column(name = "synced_at", nullable = false)
    private LocalDateTime syncedAt;

    // WOORI(우리은행) / OTHER(타은행)
    @Enumerated(EnumType.STRING)
    @Column(name = "bank_type", nullable = false, length = 20)
    private BankType bankType;

    // 비즈니스 메서드
    public void updateBalance(Long balance) {
        this.balance = balance;
        this.syncedAt = LocalDateTime.now();
    }

    public void updateAccountPurpose(String accountPurpose) {
        this.accountPurpose = accountPurpose;
    }

    public void markAsSalary() {
        this.isSalary = true;
    }

    public void unmarkAsSalary() {
        this.isSalary = false;
    }

    public boolean isWooriBank() {
        return this.bankType == BankType.WOORI;
    }

    public enum AccountType {
        CHECKING,       // 입출금 통장
        PARKING,        // 파킹 통장
        SAVINGS,        // 적금
        CREDIT_CARD,    // 신용카드
        DEBIT_CARD,     // 체크카드
        DEPOSIT,        // 예금
        STOCK,          // 증권 계좌
        CMA,            // CMA
        IRP             // 개인형 퇴직연금
    }

    public enum BankType {
        WOORI, OTHER
    }
}