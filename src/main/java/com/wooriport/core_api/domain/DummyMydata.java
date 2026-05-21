package com.wooriport.core_api.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "dummy_mydata")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DummyMydata {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", columnDefinition = "uuid DEFAULT gen_random_uuid()")
    private UUID id;

    @Column(name = "email", nullable = false, length = 255)
    private String email;

    @Column(name = "institution", nullable = false, length = 100)
    private String institution;

    @Enumerated(EnumType.STRING)
    @Column(name = "asset_type", nullable = false, length = 50)
    private Assets.AssetType assetType;

    @Column(name = "account_name", length = 100)
    private String accountName;

    @Column(name = "account_purpose", length = 100)
    private String accountPurpose;

    @Column(name = "asset_number", length = 50)
    private String assetNumber;

    @Column(name = "balance", nullable = false)
    private Long balance;

    @Enumerated(EnumType.STRING)
    @Column(name = "bank_type", nullable = false, length = 20)
    private Assets.BankType bankType;

    @Column(name = "is_salary", nullable = false)
    private Boolean isSalary = false;
}
