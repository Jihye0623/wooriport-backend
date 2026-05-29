package com.wooriport.core_api.service;

import com.wooriport.core_api.base.dto.Notification.NotificationDto;
import com.wooriport.core_api.base.dto.Notification.SpendingTrendDetailResponseDto;
import com.wooriport.core_api.domain.Notifications;
import com.wooriport.core_api.domain.Transactions;
import com.wooriport.core_api.repository.NotificationRepository;
import com.wooriport.core_api.repository.TransactionRepository;
import com.wooriport.core_api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.wooriport.core_api.domain.Users;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;

    // 접속 중인 사용자 SSE 연결 관리
    private final ConcurrentHashMap<UUID, SseEmitter> emitters = new ConcurrentHashMap<>();

    // ──────────────────────────────────────
    // SSE 구독 (프론트 앱 진입 시 호출)
    // ──────────────────────────────────────
    public SseEmitter subscribe(UUID userId) {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);

        emitters.put(userId, emitter);

        // 연결 종료 시 제거
        emitter.onCompletion(() -> {
            emitters.remove(userId);
            log.info("[SSE] 연결 종료 — userId: {}", userId);
        });
        emitter.onTimeout(() -> {
            emitters.remove(userId);
            log.info("[SSE] 연결 타임아웃 — userId: {}", userId);
        });
        emitter.onError(e -> {
            emitters.remove(userId);
            log.warn("[SSE] 연결 오류 — userId: {}, 사유: {}", userId, e.getMessage());
        });

        // 연결 확인용 초기 이벤트 전송
        try {
            emitter.send(SseEmitter.event()
                    .name("connect")
                    .data("connected"));
        } catch (IOException e) {
            emitters.remove(userId);
        }

        log.info("[SSE] 구독 시작 — userId: {}, 현재 접속: {}명", userId, emitters.size());
        return emitter;
    }

    // ──────────────────────────────────────
    // 알림 저장 + SSE 실시간 전송
    // AlertConsumer, TransferPlanService 등에서 호출
    // ──────────────────────────────────────
    @Async
    @Transactional
    public void saveAndSend(UUID userId, Notifications.NotificationType type,
                            String title, String content) {
        Users user = userRepository.getReferenceById(userId);

        // 1. DB 저장 (앱 꺼져있어도 나중에 조회 가능)
        Notifications notification = notificationRepository.save(
                Notifications.builder()
                        .user(user)
                        .type(type)
                        .title(title)
                        .content(content)
                        .isRead(false)
                        .sentAt(LocalDateTime.now())
                        .build());

        // 2. SSE 실시간 전송 (접속 중인 경우만)
        SseEmitter emitter = emitters.get(userId);
        if (emitter == null) {
            log.info("[SSE] 미접속 상태 — DB만 저장 — userId: {}", userId);
            return;
        }

        try {
            emitter.send(SseEmitter.event()
                    .name("notification")
                    .data(Map.of(
                            "id",      notification.getId(),
                            "type",    type.name(),
                            "title",   title,
                            "content", content,
                            "sentAt",  notification.getSentAt().toString()
                    )));
            log.info("[SSE] 전송 완료 — userId: {}, type: {}", userId, type);

        } catch (IOException e) {
            emitters.remove(userId);
            log.warn("[SSE] 전송 실패 — userId: {}, 사유: {}", userId, e.getMessage());
        }
    }

    @Async
    @Transactional
    public void saveAndSend(UUID userId, Notifications.NotificationType type,
                            String title, String content, String aiComment) {
        Users user = userRepository.getReferenceById(userId);

        Notifications notification = notificationRepository.save(
                Notifications.builder()
                        .user(user)
                        .type(type)
                        .title(title)
                        .content(content)
                        .aiComment(aiComment)
                        .isRead(false)
                        .sentAt(LocalDateTime.now())
                        .build());

        SseEmitter emitter = emitters.get(userId);
        if (emitter == null) {
            log.info("[SSE] 미접속 상태 — DB만 저장 — userId: {}", userId);
            return;
        }

        try {
            emitter.send(SseEmitter.event()
                    .name("notification")
                    .data(Map.of(
                            "id",      notification.getId(),
                            "type",    type.name(),
                            "title",   title,
                            "content", content,
                            "sentAt",  notification.getSentAt().toString()
                    )));
            log.info("[SSE] 전송 완료 — userId: {}, type: {}", userId, type);

        } catch (IOException e) {
            emitters.remove(userId);
            log.warn("[SSE] 전송 실패 — userId: {}, 사유: {}", userId, e.getMessage());
        }
    }

    // ──────────────────────────────────────
    // SPENDING_TREND 상세 조회
    // ──────────────────────────────────────
    @Transactional(readOnly = true)
    public SpendingTrendDetailResponseDto getSpendingTrendDetail(UUID userId, UUID notificationId) {
        Notifications notification = notificationRepository.findByIdAndUserId(notificationId, userId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않거나 권한이 없는 알림입니다."));

        LocalDateTime sentAt = notification.getSentAt();
        int year  = sentAt.getYear();
        int month = sentAt.getMonthValue();
        LocalDateTime monthStart = LocalDate.of(year, month, 1).atStartOfDay();

        int prevYear  = month == 1 ? year - 1 : year;
        int prevMonth = month == 1 ? 12 : month - 1;

        // 이번달: monthStart ~ sentAt
        List<Transactions> thisMonthTx = transactionRepository.findExpensesBetween(userId, monthStart, sentAt);
        // 지난달: 전체
        List<Transactions> lastMonthTx = transactionRepository.findMonthlyExpenses(userId, prevYear, prevMonth);

        // 주차별 그래프
        long[] thisWeekly = new long[5];
        long[] lastWeekly = new long[5];
        for (Transactions t : thisMonthTx) {
            int week = Math.min((t.getTransactionAt().getDayOfMonth() - 1) / 7, 4);
            thisWeekly[week] += -t.getAmount();
        }
        for (Transactions t : lastMonthTx) {
            int week = Math.min((t.getTransactionAt().getDayOfMonth() - 1) / 7, 4);
            lastWeekly[week] += -t.getAmount();
        }

        List<SpendingTrendDetailResponseDto.WeeklyItem> weeklyGraph = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            if (thisWeekly[i] == 0 && lastWeekly[i] == 0) continue;
            weeklyGraph.add(SpendingTrendDetailResponseDto.WeeklyItem.builder()
                    .week(i + 1)
                    .lastMonth(lastWeekly[i])
                    .thisMonth(thisWeekly[i])
                    .build());
        }

        // 카테고리별 소비
        Map<String, Long> categoryMap = thisMonthTx.stream()
                .collect(Collectors.groupingBy(
                        t -> t.getCategory() != null ? t.getCategory() : "기타",
                        Collectors.summingLong(t -> -t.getAmount())));

        List<SpendingTrendDetailResponseDto.CategoryItem> categorySpending = categoryMap.entrySet().stream()
                .map(e -> SpendingTrendDetailResponseDto.CategoryItem.builder()
                        .category(e.getKey())
                        .amount(e.getValue())
                        .build())
                .collect(Collectors.toList());

        return SpendingTrendDetailResponseDto.builder()
                .aiComment(notification.getAiComment())
                .weeklyGraph(weeklyGraph)
                .categorySpending(categorySpending)
                .build();
    }

    // ──────────────────────────────────────
    // 알림 목록 조회
    // ──────────────────────────────────────
    @Transactional(readOnly = true)
    public List<NotificationDto.Response> getNotifications(UUID userId) {
        return notificationRepository.findByUserIdOrderBySentAtDesc(userId).stream()
                .map(NotificationDto.Response::from)
                .collect(Collectors.toList());
    }

    // ──────────────────────────────────────
    // 단건 읽음 처리
    // ──────────────────────────────────────
    @Transactional
    public void readNotification(UUID userId, UUID notificationId) {
        Notifications notification = notificationRepository.findByIdAndUserId(notificationId, userId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않거나 권한이 없는 알림입니다."));

        notification.markAsRead();
    }

    // ──────────────────────────────────────
    // 전체 읽음 처리
    // ──────────────────────────────────────
    @Transactional
    public void readAllNotifications(UUID userId) {
        notificationRepository.markAllAsReadByUserId(userId);
    }
}