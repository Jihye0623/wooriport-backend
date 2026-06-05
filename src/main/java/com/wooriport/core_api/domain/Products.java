package com.wooriport.core_api.domain;

import com.wooriport.core_api.domain.common.SoftDeleteEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "products")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Products extends SoftDeleteEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    // SAVING(적금) / DEPOSIT(예금) / STOCK(주식) / BOND(채권) / IRP / ETF / PENSION_SAVINGS / ISA
    @Enumerated(EnumType.STRING)
    @Column(name = "product_type", nullable = false, length = 20)
    private ProductType productType;

    @Column(name = "institution", nullable = false, length = 100)
    private String institution;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "ticker", length = 20)
    private String ticker;

    // 금리 (%) — Spring Batch가 금감원 API로 매일 업데이트
    @Column(name = "interest_rate")
    private Float interestRate;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // 비즈니스 메서드
    public void updateInterestRate(Float interestRate) {
        this.interestRate = interestRate;
        this.updatedAt = LocalDateTime.now();
    }

    public void discontinue() {
        this.delete(); // soft delete
    }

    public enum ProductType {
        SAVING, DEPOSIT, STOCK, BOND, IRP, ETF, PENSION_SAVINGS, ISA
    }
}