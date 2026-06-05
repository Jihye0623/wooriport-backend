package com.wooriport.core_api.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class YahooFinanceService {

    private final WebClient yahooWebClient;

    public YahooFinanceService(@Qualifier("yahooWebClient") WebClient yahooWebClient) {
        this.yahooWebClient = yahooWebClient;
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
}
