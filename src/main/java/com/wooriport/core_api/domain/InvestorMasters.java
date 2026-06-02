package com.wooriport.core_api.domain;

import com.wooriport.core_api.domain.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "investor_masters")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class InvestorMasters extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @Column(name = "porti_type", nullable = false, length = 20)
    private String portiType;

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "hashtag1", length = 50)
    private String hashtag1;

    @Column(name = "hashtag2", length = 50)
    private String hashtag2;

    @Column(name = "investment_style", columnDefinition = "TEXT")
    private String investmentStyle;

    @OneToMany(mappedBy = "master", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<MasterPortfolioItems> items = new ArrayList<>();
}
