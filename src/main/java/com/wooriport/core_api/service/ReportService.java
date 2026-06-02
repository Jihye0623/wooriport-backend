package com.wooriport.core_api.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wooriport.core_api.base.dto.report.ReportDetailResponseDto;
import com.wooriport.core_api.base.dto.report.ReportListResponseDto;
import com.wooriport.core_api.base.exception.UserNotFoundException;
import com.wooriport.core_api.domain.*;
import com.wooriport.core_api.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportService {

    private final ReportRepository reportRepository;
    private final UserRepository userRepository;
    private final AssetSnapshotsRepository assetSnapshotsRepository;
    private final EventRepository eventRepository;
    private final TransactionRepository transactionRepository;
    private final PortfolioFlowRepository portfolioFlowRepository;
    private final NotificationService notificationService;
    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    @Value("${flask.ml-url}")
    private String flaskMlUrl;

    // ──────────────────────────────────────
    // GET /reports — 목록
    // ──────────────────────────────────────
    @Transactional(readOnly = true)
    public ReportListResponseDto getReports(UUID userId) {
        List<Reports> reports = reportRepository.findByUserIdOrderByYearDescMonthDesc(userId);

        List<ReportListResponseDto.ReportItem> items = reports.stream()
                .map(r -> ReportListResponseDto.ReportItem.builder()
                        .id(r.getId())
                        .year(r.getYear())
                        .month(r.getMonth())
                        .totalIncome(r.getTotalIncome())
                        .totalExpense(r.getTotalExpense())
                        .surplus(r.getSurplus())
                        .createdAt(r.getCreatedAt().toString())
                        .build())
                .collect(Collectors.toList());

        return ReportListResponseDto.builder()
                .reports(items)
                .totalCount(items.size())
                .build();
    }

    // ──────────────────────────────────────
    // GET /reports/{year}/{month} — 상세
    // ──────────────────────────────────────
    @Transactional(readOnly = true)
    public ReportDetailResponseDto getReport(UUID userId, int year, int month) {
        Reports report = reportRepository
                .findByUserIdAndYearAndMonth(userId, year, month)
                .orElseThrow(() -> new IllegalArgumentException(
                        year + "년 " + month + "월 리포트가 없습니다."));

        List<ReportDetailResponseDto.AssetSnapshot> assetSnapshots = parseAssetSnapshots(report.getAssetSnapshotsJson());
        List<ReportDetailResponseDto.WeeklyExpenseSnapshot> weeklyExpenses = parseWeeklyExpenses(report.getWeeklyExpensesJson());

        List<ReportDetailResponseDto.CategoryExpenseItem> categoryItems = report.getCategoryExpenses().stream()
                .map(c -> ReportDetailResponseDto.CategoryExpenseItem.builder()
                        .category(c.getCategory())
                        .amount(c.getAmount())
                        .prevAmount(c.getPrevAmount())
                        .ratio(c.getRatio())
                        .hoverComment(c.getHoverComment())
                        .build())
                .collect(Collectors.toList());

        return ReportDetailResponseDto.builder()
                .id(report.getId())
                .year(report.getYear())
                .month(report.getMonth())
                .totalIncome(report.getTotalIncome())
                .totalExpense(report.getTotalExpense())
                .surplus(report.getSurplus())
                .trendComment(report.getPortfolioComment())
                .eventComment(report.getEventComment())
                .marketCondition(report.getMarketSummary())
                .guideline(report.getNextMonthGuideline())
                .performanceStatus(report.getPerformanceStatus())
                .performanceComment(report.getPerformanceComment())
                .assetSnapshots(assetSnapshots)
                .weeklyExpenses(weeklyExpenses)
                .categoryExpenses(categoryItems)
                .createdAt(report.getCreatedAt().toString())
                .build();
    }

    // ──────────────────────────────────────
    // Spring Batch — 리포트 생성
    // ──────────────────────────────────────
    @Transactional
    public void generateMonthlyReport(UUID userId, int year, int month) {

        if (reportRepository.existsByUserIdAndYearAndMonth(userId, year, month)) {
            log.info("[ReportJob] 이미 생성됨 — userId: {}, {}년 {}월", userId, year, month);
            return;
        }

        Users user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException());

        // 1. 활성 이벤트 조회
        Optional<Event> eventOpt = eventRepository.findActiveByUserId(userId);

        // 2. 해당 월 전체 거래 내역
        List<Transactions> txList = transactionRepository.findAllByMonth(userId, year, month);

        // 2-1. 전달 지출 내역 (주별 누적 소비 비교용)
        int prevYear  = month == 1 ? year - 1 : year;
        int prevMonth = month == 1 ? 12 : month - 1;
        List<Transactions> prevExpenseList = transactionRepository.findMonthlyExpenses(userId, prevYear, prevMonth);

        // 3. 자산 스냅샷 (asset_snapshots 테이블)
        LocalDateTime from = LocalDate.of(year, month, 1).atStartOfDay();
        LocalDateTime to   = YearMonth.of(year, month).atEndOfMonth().plusDays(1).atStartOfDay();
        List<AssetSnapshots> snapshots = assetSnapshotsRepository.findByUserIdAndMonth(userId, from, to);
        String assetSnapshotsJson = serializeAssetSnapshotsJson(snapshots);

        // 4-2. 주별 누적 소비 JSON (이번달 vs 전달)
        String weeklyExpensesJson = buildWeeklyExpensesJson(txList, prevExpenseList, year, month);

        // 5. 거래 내역 로그
        List<Map<String, Object>> txLog = txList.stream()
                .map(t -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("amount", t.getAmount());
                    m.put("category", t.getCategory() != null ? t.getCategory() : "");
                    m.put("sender_name", t.getSenderName() != null ? t.getSenderName() : "");
                    m.put("transaction_at", t.getTransactionAt().toString());
                    return m;
                })
                .collect(Collectors.toList());

        // 6. Flask 요청 바디 구성
        Map<String, Object> flaskBody = new HashMap<>();
        flaskBody.put("user_id", userId.toString());
        flaskBody.put("year", year);
        flaskBody.put("month", month);

        Integer goalProgress = null;
        if (eventOpt.isPresent()) {
            Event ev = eventOpt.get();
            flaskBody.put("title", ev.getTitle());
            flaskBody.put("deadline", ev.getDeadline().atStartOfDay().toString());
            flaskBody.put("target_amount", ev.getTargetAmount());

            Long current = portfolioFlowRepository.findByUserIdAndEventId(userId, ev.getId())
                    .map(PortfolioFlows::getGatheringAsset)
                    .map(a -> a == null ? 0L : (a.getBalance() == null ? 0L : a.getBalance()))
                    .orElse(0L);
            Long target = ev.getTargetAmount();
            goalProgress = (target != null && target > 0)
                    ? (int) Math.min(100, Math.round(current * 100.0 / target))
                    : 0;
            flaskBody.put("goal_progress", goalProgress);
        } else {
            flaskBody.put("title", "");
            flaskBody.put("deadline", LocalDate.of(year, month, 1).atStartOfDay().toString());
            flaskBody.put("target_amount", 0);
            flaskBody.put("goal_progress", 0);
        }

        flaskBody.put("asset_snapshots", buildFlaskAssetSnapshots(snapshots));
        flaskBody.put("transaction_log", txLog);

        // 7. Flask 호출
        Map<String, Object> res = callFlask(flaskBody);
        if (res == null) {
            log.warn("[ReportJob] Flask 응답 없음 — userId: {}", userId);
            return;
        }

        // 8. 수입/지출 집계
        long totalIncome  = txList.stream().filter(t -> t.getAmount() > 0).mapToLong(Transactions::getAmount).sum();
        long totalExpense = txList.stream().filter(t -> t.getAmount() < 0).mapToLong(t -> -t.getAmount()).sum();

        // 9. Reports 저장
        Reports report = reportRepository.save(Reports.builder()
                .user(user)
                .year(year)
                .month(month)
                .totalIncome(totalIncome)
                .totalExpense(totalExpense)
                .surplus(totalIncome - totalExpense)
                .portfolioComment((String) res.get("trend_comment"))
                .eventComment((String) res.get("event_comment"))
                .marketSummary((String) res.get("market_condition"))
                .nextMonthGuideline((String) res.get("guideline"))
                .performanceStatus((String) res.get("performance_status"))
                .performanceComment((String) res.get("performance_comment"))
                .assetSnapshotsJson(assetSnapshotsJson)
                .weeklyExpensesJson(weeklyExpensesJson)
                .build());

        // 10. 카테고리별 소비 저장 (hover_description JSON 파싱 포함)
        saveCategoryExpenses(report, userId, txList, prevYear, prevMonth, res);

        notificationService.saveAndSend(
                userId,
                Notifications.NotificationType.REPORT_READY,
                year + "년 " + month + "월 월간 리포트가 도착했어요!",
                user.getName() + "님의 한달 소비, 지출, 투자를 종합 분석했어요!");

        log.info("[ReportJob] 완료 — userId: {}, {}년 {}월", userId, year, month);
    }

    // ──────────────────────────────────────
    // 카테고리별 소비 저장
    // ──────────────────────────────────────
    private void saveCategoryExpenses(Reports report, UUID userId,
                                      List<Transactions> txList, int prevYear, int prevMonth,
                                      Map<String, Object> flaskRes) {

        // hover_description: {"식비": "설명1\n설명2", "교통": "..."} 형태 (String 또는 Map 모두 처리)
        Map<String, String> hoverMap = parseHoverDescription(flaskRes.get("hover_description"));

        // 이번달 카테고리별 지출 합산
        Map<String, Long> currentAmounts = txList.stream()
                .filter(t -> t.getAmount() < 0)
                .collect(Collectors.groupingBy(
                        t -> t.getCategory() != null ? t.getCategory() : "기타",
                        Collectors.summingLong(t -> -t.getAmount())));

        // 전달 카테고리별 지출
        Map<String, Long> prevAmounts = transactionRepository
                .sumExpenseGroupByCategory(userId, prevYear, prevMonth)
                .stream()
                .collect(Collectors.toMap(
                        row -> (String) row[0],
                        row -> ((Number) row[1]).longValue(),
                        (a, b) -> a));

        long totalExpense = currentAmounts.values().stream().mapToLong(Long::longValue).sum();

        List<ReportCategoryExpenses> categoryList = currentAmounts.entrySet().stream()
                .map(e -> ReportCategoryExpenses.builder()
                        .report(report)
                        .category(e.getKey())
                        .amount(e.getValue())
                        .prevAmount(prevAmounts.getOrDefault(e.getKey(), null))
                        .ratio(totalExpense > 0 ? (int)(e.getValue() * 100 / totalExpense) : 0)
                        .hoverComment(hoverMap.getOrDefault(e.getKey(), null))
                        .build())
                .collect(Collectors.toList());

        report.getCategoryExpenses().addAll(categoryList);
        reportRepository.save(report);
    }

    // ──────────────────────────────────────
    // Flask용 asset_snapshots 리스트 빌드
    // ──────────────────────────────────────
    private List<Map<String, Object>> buildFlaskAssetSnapshots(List<AssetSnapshots> snapshots) {
        return snapshots.stream()
                .map(s -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("snapshot_at",    s.getSnapshotAt().toString());
                    m.put("total_amount",   s.getTotalAmount());
                    m.put("savings_amount", s.getSavingsAmount());
                    m.put("invest_amount",  s.getInvestAmount());
                    return m;
                })
                .collect(Collectors.toList());
    }

    // ──────────────────────────────────────
    // asset_snapshots → DB 저장용 JSON 직렬화
    // ──────────────────────────────────────
    private String serializeAssetSnapshotsJson(List<AssetSnapshots> snapshots) {
        List<Map<String, Object>> result = snapshots.stream()
                .map(s -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("snapshotDate", s.getSnapshotAt().toLocalDate().toString());
                    m.put("totalAmount",  s.getTotalAmount());
                    return m;
                })
                .collect(Collectors.toList());
        try {
            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            log.warn("[ReportJob] assetSnapshots 직렬화 실패: {}", e.getMessage());
            return "[]";
        }
    }

    // ──────────────────────────────────────
    // 주별 누적 소비 JSON 생성 (이번달 vs 전달)
    // ──────────────────────────────────────
    private String buildWeeklyExpensesJson(List<Transactions> currTxList,
                                           List<Transactions> prevExpenseList,
                                           int year, int month) {
        int prevYear      = month == 1 ? year - 1 : year;
        int prevMonth     = month == 1 ? 12 : month - 1;
        int daysInCurr    = YearMonth.of(year, month).lengthOfMonth();
        int daysInPrev    = YearMonth.of(prevYear, prevMonth).lengthOfMonth();
        int maxWeeks      = (daysInCurr > 28 || daysInPrev > 28) ? 5 : 4;

        long[] currWeekly = new long[5];
        long[] prevWeekly = new long[5];

        for (Transactions t : currTxList) {
            if (t.getAmount() < 0) {
                int week = Math.min((t.getTransactionAt().getDayOfMonth() - 1) / 7, 4);
                currWeekly[week] += -t.getAmount();
            }
        }
        for (Transactions t : prevExpenseList) {
            int week = Math.min((t.getTransactionAt().getDayOfMonth() - 1) / 7, 4);
            prevWeekly[week] += -t.getAmount();
        }

        List<Map<String, Object>> result = new ArrayList<>();
        long currCumulative = 0;
        long prevCumulative = 0;

        for (int i = 0; i < maxWeeks; i++) {
            currCumulative += currWeekly[i];
            prevCumulative += prevWeekly[i];
            Map<String, Object> entry = new HashMap<>();
            entry.put("week", i + 1);
            entry.put("currCumulative", currCumulative);
            entry.put("prevCumulative", prevCumulative);
            result.add(entry);
        }

        try {
            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            log.warn("[ReportJob] weeklyExpenses 직렬화 실패: {}", e.getMessage());
            return "[]";
        }
    }

    // ──────────────────────────────────────
    // JSON → DTO 역직렬화
    // ──────────────────────────────────────
    private List<ReportDetailResponseDto.AssetSnapshot> parseAssetSnapshots(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            List<Map<String, Object>> raw = objectMapper.readValue(json, new TypeReference<>() {});
            return raw.stream()
                    .map(m -> ReportDetailResponseDto.AssetSnapshot.builder()
                            .snapshotDate((String) m.get("snapshotDate"))
                            .totalAmount(((Number) m.get("totalAmount")).longValue())
                            .build())
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("[ReportJob] assetSnapshots 파싱 실패: {}", e.getMessage());
            return List.of();
        }
    }

    private List<ReportDetailResponseDto.WeeklyExpenseSnapshot> parseWeeklyExpenses(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            List<Map<String, Object>> raw = objectMapper.readValue(json, new TypeReference<>() {});
            return raw.stream()
                    .map(m -> ReportDetailResponseDto.WeeklyExpenseSnapshot.builder()
                            .week(((Number) m.get("week")).intValue())
                            .currCumulative(((Number) m.get("currCumulative")).longValue())
                            .prevCumulative(((Number) m.get("prevCumulative")).longValue())
                            .build())
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("[ReportJob] weeklyExpenses 파싱 실패: {}", e.getMessage());
            return List.of();
        }
    }

    private String buildItemName(Assets a) {
        String name = a.getAccountName() != null ? a.getAccountName() : a.getAssetType().name();
        return a.getInstitution() + " " + name;
    }

    // ──────────────────────────────────────
    // Flask POST 요청
    // ──────────────────────────────────────
    @SuppressWarnings("unchecked")
    private Map<String, Object> callFlask(Map<String, Object> body) {
        try {
            Map<String, Object> response = webClient.post()
                    .uri(flaskMlUrl + "/report")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (response == null) throw new IllegalStateException("Flask 응답 없음");
            return response;

        } catch (Exception e) {
            log.warn("[ReportJob] Flask 요청 실패: {}", e.getMessage());
            return null;
        }
    }

    // ──────────────────────────────────────
    // hover_description JSON 파싱
    // ──────────────────────────────────────
    // Flask: [{category: "식비", content: "..."}, ...] 또는 {"식비": "..."} 형태 모두 처리
    @SuppressWarnings("unchecked")
    private Map<String, String> parseHoverDescription(Object raw) {
        if (raw == null) return Map.of();
        try {
            if (raw instanceof List<?> list) {
                return ((List<Map<String, Object>>) list).stream()
                        .filter(m -> m.get("category") != null && m.get("content") != null)
                        .collect(Collectors.toMap(
                                m -> (String) m.get("category"),
                                m -> (String) m.get("content"),
                                (a, b) -> a));
            }
            if (raw instanceof String json) {
                if (json.isBlank()) return Map.of();
                return objectMapper.readValue(json, new TypeReference<Map<String, String>>() {});
            }
            return objectMapper.convertValue(raw, new TypeReference<Map<String, String>>() {});
        } catch (Exception e) {
            log.warn("[ReportJob] hover_description 파싱 실패: {}", e.getMessage());
            return Map.of();
        }
    }

    private Long toLong(Object v) {
        if (v == null) return 0L;
        return Long.valueOf(v.toString());
    }
}
