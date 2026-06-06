package com.wooriport.core_api.base.dto.stock;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder(toBuilder = true)
public class StockDetailResponseDto {
    private String ticker;
    private String name;
    private double currentPrice;
    private double changeAmount;
    private double changeRate;
    private long volume;
    private Double affordableShares;
    private List<ChartPoint> chart;

    @Getter
    @Builder
    public static class ChartPoint {
        private String date;
        private double close;
    }
}
