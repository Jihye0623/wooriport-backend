package com.wooriport.core_api.domain;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

// 대시보드 포트폴리오 수익률 더미 데이터
// category_label = 프론트가 표시하는 카테고리명 ("ETF", "현금성", "적금", "IRP")
@Entity
@Table(name = "product_category_rates")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class ProductCategoryRate {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @Column(name = "category_label", nullable = false, unique = true, length = 30)
    private String categoryLabel;

    // 표시용 문자열 (예: "+4%", "+2%", "-")
    @Column(name = "rate", length = 10)
    private String rate;
}
