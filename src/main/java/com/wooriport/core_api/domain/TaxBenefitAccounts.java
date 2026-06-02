package com.wooriport.core_api.domain;

import com.wooriport.core_api.domain.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

/**
 * 세제혜택 계좌의 보조 정보.
 * 납입액/한도는 assets.balance + {@link TaxBenefitPolicy} 로 계산하지만,
 * ISA 수익률(= (잔액 - 납입원금) / 납입원금)은 납입원금을 알아야 하므로 여기에 저장한다.
 */
@Entity
@Table(name = "tax_benefit_accounts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class TaxBenefitAccounts extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id", nullable = false, unique = true)
    private Assets asset;

    // ISA 납입원금 (수익률 계산용). IRP/연금저축 계좌는 미사용(null 허용)
    @Column(name = "principal")
    private Long principal;

    public void updatePrincipal(Long principal) {
        this.principal = principal;
    }
}
