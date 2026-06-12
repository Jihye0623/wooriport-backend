package com.wooriport.core_api.service;

import com.wooriport.core_api.base.dto.stock.StockDetailResponseDto;
import com.wooriport.core_api.repository.ProductRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class YahooFinanceService {

    private final WebClient yahooWebClient;
    private final ProductRepository productRepository;

    public YahooFinanceService(@Qualifier("yahooWebClient") WebClient yahooWebClient,
                               ProductRepository productRepository) {
        this.yahooWebClient = yahooWebClient;
        this.productRepository = productRepository;
    }

    /**
     * 특정 연월 기준 전달 대비 등락률(%) 반환.
     * 외부 API 실패 시 null 반환.
     */
    public Double getMonthlyChangeRate(String ticker, int year, int month) {
        if (ticker == null || ticker.isBlank()) return null;

        // period1 = 전달 1일, period2 = 이번달 말 다음날 (월별 종가 2개 확보)
        long period1 = LocalDate.of(year, month, 1).minusMonths(1)
                .atStartOfDay().toEpochSecond(ZoneOffset.UTC);
        long period2 = LocalDate.of(year, month, 1).plusMonths(1)
                .atStartOfDay().toEpochSecond(ZoneOffset.UTC);

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = yahooWebClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v8/finance/chart/{ticker}")
                            .queryParam("period1", period1)
                            .queryParam("period2", period2)
                            .queryParam("interval", "1mo")
                            .build(ticker))
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block(java.time.Duration.ofSeconds(5));

            if (response == null) return null;

            @SuppressWarnings("unchecked")
            Map<String, Object> chart = (Map<String, Object>) response.get("chart");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> results = (List<Map<String, Object>>) chart.get("result");
            if (results == null || results.isEmpty()) return null;

            @SuppressWarnings("unchecked")
            Map<String, Object> indicators = (Map<String, Object>) results.get(0).get("indicators");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> quoteList = (List<Map<String, Object>>) indicators.get("quote");
            if (quoteList == null || quoteList.isEmpty()) return null;

            @SuppressWarnings("unchecked")
            List<Double> closes = (List<Double>) quoteList.get(0).get("close");
            if (closes == null || closes.size() < 2) return null;

            // 마지막 두 값으로 전달 대비 계산
            double prev = closes.get(closes.size() - 2);
            double curr = closes.get(closes.size() - 1);
            if (prev == 0) return null;

            return Math.round((curr - prev) / prev * 1000.0) / 10.0;

        } catch (Exception e) {
            log.warn("[YahooFinance] 시세 조회 실패 — ticker: {}, 사유: {}", ticker, e.getMessage());
            return null;
        }
    }

    /**
     * 티커 현재가·등락·30일 일별 종가 차트 반환.
     * 외부 API 실패 시 null 반환.
     */
    public StockDetailResponseDto getStockDetail(String ticker, Long amount) {
        if (ticker == null || ticker.isBlank()) return null;

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = yahooWebClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v8/finance/chart/{ticker}")
                            .queryParam("interval", "1d")
                            .queryParam("range", "1mo")
                            .build(ticker))
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block(java.time.Duration.ofSeconds(5));

            if (response == null) return null;

            @SuppressWarnings("unchecked")
            Map<String, Object> chart = (Map<String, Object>) response.get("chart");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> results = (List<Map<String, Object>>) chart.get("result");
            if (results == null || results.isEmpty()) return null;

            Map<String, Object> result = results.get(0);

            @SuppressWarnings("unchecked")
            Map<String, Object> meta = (Map<String, Object>) result.get("meta");
            double currentPrice = toDouble(meta.get("regularMarketPrice"));
            String name = productRepository.findFirstByTicker(ticker)
                    .map(p -> p.getName())
                    .orElseGet(() -> meta.getOrDefault("shortName", ticker).toString());

            @SuppressWarnings("unchecked")
            List<Number> timestamps = (List<Number>) result.get("timestamp");
            @SuppressWarnings("unchecked")
            Map<String, Object> indicators = (Map<String, Object>) result.get("indicators");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> quoteList = (List<Map<String, Object>>) indicators.get("quote");
            @SuppressWarnings("unchecked")
            List<Double> closes = (quoteList != null && !quoteList.isEmpty())
                    ? (List<Double>) quoteList.get(0).get("close") : List.of();

            @SuppressWarnings("unchecked")
            List<Number> volumes = (quoteList != null && !quoteList.isEmpty())
                    ? (List<Number>) quoteList.get(0).get("volume") : List.of();

            List<StockDetailResponseDto.ChartPoint> chartPoints = new ArrayList<>();
            if (timestamps != null) {
                for (int i = 0; i < timestamps.size(); i++) {
                    Double close = (i < closes.size()) ? closes.get(i) : null;
                    if (close == null) continue;
                    String date = Instant.ofEpochSecond(timestamps.get(i).longValue())
                            .atZone(ZoneOffset.UTC).toLocalDate().toString();
                    chartPoints.add(StockDetailResponseDto.ChartPoint.builder()
                            .date(date).close(Math.round(close * 100.0) / 100.0).build());
                }
            }

            long latestVolume = 0;
            for (int i = volumes.size() - 1; i >= 0; i--) {
                if (volumes.get(i) != null) { latestVolume = volumes.get(i).longValue(); break; }
            }

            double previousClose = chartPoints.size() >= 2
                    ? chartPoints.get(chartPoints.size() - 2).getClose() : currentPrice;
            double changeAmount = Math.round((currentPrice - previousClose) * 100.0) / 100.0;
            double changeRate = previousClose == 0 ? 0
                    : Math.round((currentPrice - previousClose) / previousClose * 1000.0) / 10.0;
            Double affordableShares = (amount != null && currentPrice > 0)
                    ? Math.floor(amount / currentPrice * 100.0) / 100.0 : null;

            return StockDetailResponseDto.builder()
                    .ticker(ticker)
                    .name(name)
                    .currentPrice(currentPrice)
                    .changeAmount(changeAmount)
                    .changeRate(changeRate)
                    .volume(latestVolume)
                    .affordableShares(affordableShares)
                    .chart(chartPoints)
                    .build();

        } catch (Exception e) {
            log.warn("[YahooFinance] 상세 시세 조회 실패 — ticker: {}, 사유: {}", ticker, e.getMessage());
            return null;
        }
    }

    private double toDouble(Object val) {
        if (val == null) return 0;
        if (val instanceof Number) return ((Number) val).doubleValue();
        try { return Double.parseDouble(val.toString()); } catch (Exception e) { return 0; }
    }
}
