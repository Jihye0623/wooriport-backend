package com.wooriport.core_api.base.dto.demo;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class DemoStatusDto {
    private String email;
    private boolean userExists;
    private int productCount;
    private int mydataCount;
    private int assetCount;
    private boolean salaryAssetSet;
    private int transactionCount;
    private boolean taxBenefitSeeded;
    private String nextStep;
}
