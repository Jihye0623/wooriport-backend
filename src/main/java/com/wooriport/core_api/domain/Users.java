package com.wooriport.core_api.domain;

import com.wooriport.core_api.domain.common.SoftDeleteEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Users extends SoftDeleteEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @Column(name = "password", nullable = false, length = 100)
    private String password;

    @Column(name = "email", nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private UserStatus status = UserStatus.ACTIVE;

    // 추구미 설문 결과 유형
    @Enumerated(EnumType.STRING)
    @Column(name = "porti_type", length = 20)
    private PortiType portiType;

    @Column(name = "salary_date")
    private Integer salaryDate;

    @Column(name = "auto_transfer_to_asset_id", columnDefinition = "uuid")
    private UUID autoTransferToAssetId;

    @Column(name = "monthly_invest_amount")
    private Long monthlyInvestAmount;

    @Column(name = "salary")
    private Long salary;

    public void updateMonthlyInvestAmount(Long amount) {
        this.monthlyInvestAmount = amount;
    }

    public void updateSalary(Long salary) {
        this.salary = salary;
    }

    public void connectAutoTransfer(UUID toAssetId) {
        this.autoTransferToAssetId = toAssetId;
    }

    public void updateSalaryDate(Integer salaryDate) {
        this.salaryDate = salaryDate;
    }

    // 비즈니스 메서드
    public void updatePortiType(PortiType portiType) {
        this.portiType = portiType;
    }

    public void updateProfile(String name) {
        this.name = name;
    }

    public void suspend() {
        this.status = UserStatus.SUSPENDED;
    }

    public void withdraw() {
        this.status = UserStatus.WITHDRAWN;
        this.delete();
    }

    public enum UserStatus {
        // 정상, 정지, 탈퇴
        ACTIVE, SUSPENDED, WITHDRAWN
    }

    public enum PortiType {
        SWIMMING,       // 수영
        ARCHERY,        // 양궁
        JUDO,           // 유도
        RHYTHMIC,       // 리듬체조
        FENCING,        // 펜싱
        CYCLING         // 사이클
    }

    public String getPortiComment() {
        if (this.portiType == null) return "";
        return switch (this.portiType) {
            case SWIMMING -> "안정적으로 모으고, 목표는 빠르게. 리스크는 낮추고 실행은 빠르게 하는 타입이에요.";
            case ARCHERY  -> "안전하게, 그리고 멀리. 꾸준히 쌓아가는 장기 안정형 투자자예요.";
            case JUDO     -> "균형 잡힌 감각으로 단기 목표를 빠르게 달성하는 타입이에요.";
            case RHYTHMIC -> "균형과 지속성을 중시하는 장기 중립형이에요.";
            case FENCING  -> "공격적이고 빠른 투자로 단기 수익을 노리는 타입이에요.";
            case CYCLING  -> "높은 수익을 향해 장기적으로 달려가는 투자 지향형이에요.";
        };
    }
}
