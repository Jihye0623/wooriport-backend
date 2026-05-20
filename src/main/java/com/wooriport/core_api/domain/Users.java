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

    @Column(name = "phone", length = 20)
    private String phone;

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

    public void connectAutoTransfer(UUID fromAssetId, UUID toAssetId) {
        this.autoTransferToAssetId = toAssetId;
    }

    public void updateSalaryDate(Integer salaryDate) {
        this.salaryDate = salaryDate;
    }

    // 비즈니스 메서드
    public void updateFinanceType(PortiType PortiType) {
        this.portiType = portiType;
    }

    public void updateProfile(String name, String phone) {
        this.name = name;
        this.phone = phone;
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
}
