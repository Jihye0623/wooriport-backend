package com.wooriport.core_api.service;

import com.wooriport.core_api.base.dto.Notification.NagNotificationResponseDto;
import com.wooriport.core_api.base.dto.stock.StockDetailResponseDto;
import com.wooriport.core_api.domain.MiniChallenges;
import com.wooriport.core_api.domain.Notifications;
import com.wooriport.core_api.domain.Products;
import com.wooriport.core_api.domain.Users;
import com.wooriport.core_api.repository.MiniChallengesRepository;
import com.wooriport.core_api.repository.NotificationRepository;
import com.wooriport.core_api.repository.ProductRepository;
import com.wooriport.core_api.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock NotificationRepository notificationRepository;
    @Mock UserRepository userRepository;
    @Mock MiniChallengesRepository miniChallengesRepository;
    @Mock ProductRepository productRepository;
    @Mock YahooFinanceService yahooFinanceService;
    @InjectMocks NotificationService notificationService;

    private final UUID userId = UUID.randomUUID();
    private final UUID notificationId = UUID.randomUUID();

    // ──────────────────────────────────────
    // subscribe — SSE 생명주기
    // ──────────────────────────────────────

    @Test
    @DisplayName("구독 시 connect 이벤트를 전송하고 emitter를 등록한다")
    void subscribe_sendsConnectEvent_andRegisters() throws IOException {
        try (MockedConstruction<SseEmitter> mocked = Mockito.mockConstruction(SseEmitter.class)) {
            SseEmitter result = notificationService.subscribe(userId);

            SseEmitter constructed = mocked.constructed().get(0);
            assertThat(result).isSameAs(constructed);
            verify(constructed).send(any(SseEmitter.SseEventBuilder.class));
        }
    }

    @Test
    @DisplayName("connect 이벤트 전송이 실패(IOException)하면 emitter를 즉시 제거해 이후 알림이 전송되지 않는다")
    void subscribe_initialSendFails_removesEmitterImmediately() throws IOException {
        try (MockedConstruction<SseEmitter> mocked = Mockito.mockConstruction(SseEmitter.class,
                (mock, context) -> doThrow(new IOException("연결 끊김")).when(mock).send(any(SseEmitter.SseEventBuilder.class)))) {

            notificationService.subscribe(userId);
            SseEmitter constructed = mocked.constructed().get(0);

            given(userRepository.getReferenceById(userId)).willReturn(Users.builder().id(userId).build());
            notificationService.saveAndSend(userId, Notifications.NotificationType.REPORT_READY, "제목", "내용");

            // connect 시도(실패) 1회만 있고, saveAndSend로 인한 추가 전송 시도는 없어야 한다 (이미 제거됨)
            verify(constructed, times(1)).send(any(SseEmitter.SseEventBuilder.class));
        }
    }

    @Test
    @DisplayName("onCompletion 콜백이 실행되면 emitter가 제거되어 이후 알림은 DB에만 저장된다")
    void subscribe_onCompletionCallback_removesEmitter() throws IOException {
        try (MockedConstruction<SseEmitter> mocked = Mockito.mockConstruction(SseEmitter.class)) {
            notificationService.subscribe(userId);
            SseEmitter constructed = mocked.constructed().get(0);

            ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
            verify(constructed).onCompletion(captor.capture());
            captor.getValue().run(); // 연결 종료 시뮬레이션

            given(userRepository.getReferenceById(userId)).willReturn(Users.builder().id(userId).build());
            notificationService.saveAndSend(userId, Notifications.NotificationType.REPORT_READY, "제목", "내용");

            verify(notificationRepository).save(any());
            verify(constructed, times(1)).send(any(SseEmitter.SseEventBuilder.class)); // connect 1회뿐, 알림 전송 없음
        }
    }

    @Test
    @DisplayName("onError 콜백이 실행되면 emitter가 제거된다")
    void subscribe_onErrorCallback_removesEmitter() throws IOException {
        try (MockedConstruction<SseEmitter> mocked = Mockito.mockConstruction(SseEmitter.class)) {
            notificationService.subscribe(userId);
            SseEmitter constructed = mocked.constructed().get(0);

            @SuppressWarnings("unchecked")
            ArgumentCaptor<Consumer<Throwable>> captor = ArgumentCaptor.forClass(Consumer.class);
            verify(constructed).onError(captor.capture());
            captor.getValue().accept(new RuntimeException("소켓 오류"));

            given(userRepository.getReferenceById(userId)).willReturn(Users.builder().id(userId).build());
            notificationService.saveAndSend(userId, Notifications.NotificationType.REPORT_READY, "제목", "내용");

            verify(constructed, times(1)).send(any(SseEmitter.SseEventBuilder.class));
        }
    }

    @Test
    @DisplayName("서로 다른 유저의 emitter는 독립적으로 관리되어 알림이 섞이지 않는다")
    void subscribe_multipleUsers_emittersAreIsolated() throws IOException {
        UUID otherUserId = UUID.randomUUID();
        try (MockedConstruction<SseEmitter> mocked = Mockito.mockConstruction(SseEmitter.class)) {
            notificationService.subscribe(userId);
            notificationService.subscribe(otherUserId);

            SseEmitter mine = mocked.constructed().get(0);
            SseEmitter others = mocked.constructed().get(1);

            given(userRepository.getReferenceById(userId)).willReturn(Users.builder().id(userId).build());
            stubSaveAssignsId();
            notificationService.saveAndSend(userId, Notifications.NotificationType.REPORT_READY, "제목", "내용");

            verify(mine, times(2)).send(any(SseEmitter.SseEventBuilder.class)); // connect + 알림
            verify(others, times(1)).send(any(SseEmitter.SseEventBuilder.class)); // connect만
        }
    }

    // ──────────────────────────────────────
    // saveAndSend
    // ──────────────────────────────────────

    @Test
    @DisplayName("접속 중이면 DB 저장과 SSE 전송이 둘 다 일어난다")
    void saveAndSend_connected_savesAndSends() throws IOException {
        try (MockedConstruction<SseEmitter> mocked = Mockito.mockConstruction(SseEmitter.class)) {
            notificationService.subscribe(userId);
            SseEmitter constructed = mocked.constructed().get(0);

            given(userRepository.getReferenceById(userId)).willReturn(Users.builder().id(userId).build());
            stubSaveAssignsId();

            notificationService.saveAndSend(userId, Notifications.NotificationType.REPORT_READY, "제목", "내용");

            verify(notificationRepository).save(any());
            verify(constructed, times(2)).send(any(SseEmitter.SseEventBuilder.class)); // connect + 알림
        }
    }

    @Test
    @DisplayName("미접속 상태면 DB에만 저장하고 SSE 전송은 시도하지 않는다")
    void saveAndSend_notConnected_savesOnly() {
        given(userRepository.getReferenceById(userId)).willReturn(Users.builder().id(userId).build());
        given(notificationRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        notificationService.saveAndSend(userId, Notifications.NotificationType.REPORT_READY, "제목", "내용");

        verify(notificationRepository).save(any());
        // 구독한 적이 없으므로 emitter 자체가 없음 — 예외 없이 정상 종료되면 충분
    }

    @Test
    @DisplayName("SSE 전송 중 IOException이 발생하면 emitter를 제거한다")
    void saveAndSend_sendFails_removesEmitter() throws IOException {
        try (MockedConstruction<SseEmitter> mocked = Mockito.mockConstruction(SseEmitter.class)) {
            notificationService.subscribe(userId);
            SseEmitter constructed = mocked.constructed().get(0);

            given(userRepository.getReferenceById(userId)).willReturn(Users.builder().id(userId).build());
            stubSaveAssignsId();
            doThrow(new IOException("전송 실패")).when(constructed).send(any(SseEmitter.SseEventBuilder.class));

            notificationService.saveAndSend(userId, Notifications.NotificationType.REPORT_READY, "제목", "내용");
            // 두 번째 호출: 이미 제거되었으므로 send()가 다시 시도되지 않아야 함
            notificationService.saveAndSend(userId, Notifications.NotificationType.REPORT_READY, "제목2", "내용2");

            // connect(구독 시, 성공) + 첫 saveAndSend의 실패한 전송 시도 = 2회. 두 번째 saveAndSend는 이미 제거되어 시도 자체가 없음
            verify(constructed, times(2)).send(any(SseEmitter.SseEventBuilder.class));
            verify(notificationRepository, times(2)).save(any()); // DB 저장은 두 번 다 일어남
        }
    }

    // ──────────────────────────────────────
    // readNotification / readAllNotifications
    // ──────────────────────────────────────

    @Test
    @DisplayName("존재하지 않거나 권한 없는 알림을 읽음 처리하려 하면 예외가 발생한다")
    void readNotification_notFoundOrNoPermission_throws() {
        given(notificationRepository.findByIdAndUserId(notificationId, userId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> notificationService.readNotification(userId, notificationId))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("정상적인 알림은 읽음 처리된다")
    void readNotification_marksAsRead() {
        Notifications notification = notification(Notifications.NotificationType.REPORT_READY, "내용");
        given(notificationRepository.findByIdAndUserId(notificationId, userId)).willReturn(Optional.of(notification));

        notificationService.readNotification(userId, notificationId);

        assertThat(notification.getIsRead()).isTrue();
    }

    @Test
    @DisplayName("전체 읽음 처리는 리포지토리의 일괄 업데이트 쿼리를 호출한다")
    void readAllNotifications_callsBulkUpdate() {
        notificationService.readAllNotifications(userId);

        verify(notificationRepository).markAllAsReadByUserId(userId);
    }

    // ──────────────────────────────────────
    // getNagNotification
    // ──────────────────────────────────────

    @Test
    @DisplayName("존재하지 않거나 권한 없는 알림 조회 시 예외가 발생한다")
    void getNagNotification_notFoundOrNoPermission_throws() {
        given(notificationRepository.findByIdAndUserId(notificationId, userId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> notificationService.getNagNotification(userId, notificationId))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("CHALLENGE_COMPLETE 타입은 완료된 챌린지를 완료일 desc로 조회한다")
    void getNagNotification_challengeComplete_queriesCompletedByCompletedAtDesc() {
        Notifications nag = notification(Notifications.NotificationType.CHALLENGE_COMPLETE, "원본 문구");
        given(notificationRepository.findByIdAndUserId(notificationId, userId)).willReturn(Optional.of(nag));
        given(miniChallengesRepository.findFirstByUserIdAndStatusOrderByCompletedAtDesc(
                userId, MiniChallenges.ChallengeStatus.COMPLETED)).willReturn(Optional.empty());

        notificationService.getNagNotification(userId, notificationId);

        verify(miniChallengesRepository).findFirstByUserIdAndStatusOrderByCompletedAtDesc(
                userId, MiniChallenges.ChallengeStatus.COMPLETED);
    }

    @Test
    @DisplayName("CHALLENGE_FAILED 타입은 실패한 챌린지를 시작일 desc로 조회한다")
    void getNagNotification_challengeFailed_queriesFailedByStartedAtDesc() {
        Notifications nag = notification(Notifications.NotificationType.CHALLENGE_FAILED, "원본 문구");
        given(notificationRepository.findByIdAndUserId(notificationId, userId)).willReturn(Optional.of(nag));
        given(miniChallengesRepository.findFirstByUserIdAndStatusOrderByStartedAtDesc(
                userId, MiniChallenges.ChallengeStatus.FAILED)).willReturn(Optional.empty());

        notificationService.getNagNotification(userId, notificationId);

        verify(miniChallengesRepository).findFirstByUserIdAndStatusOrderByStartedAtDesc(
                userId, MiniChallenges.ChallengeStatus.FAILED);
    }

    @Test
    @DisplayName("그 외 타입(NAG_50 등)은 진행 중인 챌린지를 조회한다")
    void getNagNotification_otherType_queriesInProgress() {
        Notifications nag = notification(Notifications.NotificationType.NAG_50, "원본 문구");
        given(notificationRepository.findByIdAndUserId(notificationId, userId)).willReturn(Optional.of(nag));
        given(miniChallengesRepository.findFirstByUserIdAndStatus(
                userId, MiniChallenges.ChallengeStatus.IN_PROGRESS)).willReturn(Optional.empty());

        notificationService.getNagNotification(userId, notificationId);

        verify(miniChallengesRepository).findFirstByUserIdAndStatus(
                userId, MiniChallenges.ChallengeStatus.IN_PROGRESS);
    }

    @Test
    @DisplayName("매칭되는 챌린지가 없으면 챌린지·주식 관련 필드는 null이고 원본 content가 유지된다")
    void getNagNotification_noMatchingChallenge_fieldsAreNull() {
        Notifications nag = notification(Notifications.NotificationType.NAG_50, "원본 문구");
        given(notificationRepository.findByIdAndUserId(notificationId, userId)).willReturn(Optional.of(nag));
        given(miniChallengesRepository.findFirstByUserIdAndStatus(any(), any())).willReturn(Optional.empty());

        NagNotificationResponseDto result = notificationService.getNagNotification(userId, notificationId);

        assertThat(result.getChallengeId()).isNull();
        assertThat(result.getChallengeTitle()).isNull();
        assertThat(result.getStockName()).isNull();
        assertThat(result.getContent()).isEqualTo("원본 문구");
    }

    @Test
    @DisplayName("리워드 주식이 없으면 시세 조회를 하지 않는다")
    void getNagNotification_noRewardStock_skipsYahooCall() {
        Notifications nag = notification(Notifications.NotificationType.NAG_50, "원본 문구");
        MiniChallenges challenge = MiniChallenges.builder()
                .id(UUID.randomUUID()).title("챌린지").rewardStockTicker(null).build();
        given(notificationRepository.findByIdAndUserId(notificationId, userId)).willReturn(Optional.of(nag));
        given(miniChallengesRepository.findFirstByUserIdAndStatus(any(), any())).willReturn(Optional.of(challenge));

        NagNotificationResponseDto result = notificationService.getNagNotification(userId, notificationId);

        assertThat(result.getChallengeTitle()).isEqualTo("챌린지");
        assertThat(result.getStockName()).isNull();
        verify(yahooFinanceService, never()).getStockDetail(any(), any());
    }

    @Test
    @DisplayName("stockName은 productRepository에 등록된 상품명을 우선 사용한다")
    void getNagNotification_stockName_prefersProductRepository() {
        Notifications nag = notification(Notifications.NotificationType.NAG_50, "원본 문구");
        MiniChallenges challenge = MiniChallenges.builder()
                .id(UUID.randomUUID()).title("챌린지").rewardStockTicker("AAPL")
                .estimatedSaving(10_000L).build();
        given(notificationRepository.findByIdAndUserId(notificationId, userId)).willReturn(Optional.of(nag));
        given(miniChallengesRepository.findFirstByUserIdAndStatus(any(), any())).willReturn(Optional.of(challenge));
        given(yahooFinanceService.getStockDetail("AAPL", 10_000L))
                .willReturn(StockDetailResponseDto.builder().name("애플(야후)").currentPrice(200.0).affordableShares(0.5).build());
        given(productRepository.findFirstByTicker("AAPL"))
                .willReturn(Optional.of(Products.builder().name("애플(상품DB)").build()));

        NagNotificationResponseDto result = notificationService.getNagNotification(userId, notificationId);

        assertThat(result.getStockName()).isEqualTo("애플(상품DB)");
    }

    @Test
    @DisplayName("productRepository에 없으면 야후 응답의 이름을 사용한다")
    void getNagNotification_stockName_fallsBackToYahooName() {
        Notifications nag = notification(Notifications.NotificationType.NAG_50, "원본 문구");
        MiniChallenges challenge = MiniChallenges.builder()
                .id(UUID.randomUUID()).title("챌린지").rewardStockTicker("AAPL")
                .estimatedSaving(10_000L).build();
        given(notificationRepository.findByIdAndUserId(notificationId, userId)).willReturn(Optional.of(nag));
        given(miniChallengesRepository.findFirstByUserIdAndStatus(any(), any())).willReturn(Optional.of(challenge));
        given(yahooFinanceService.getStockDetail("AAPL", 10_000L))
                .willReturn(StockDetailResponseDto.builder().name("애플(야후)").currentPrice(200.0).affordableShares(0.5).build());
        given(productRepository.findFirstByTicker("AAPL")).willReturn(Optional.empty());

        NagNotificationResponseDto result = notificationService.getNagNotification(userId, notificationId);

        assertThat(result.getStockName()).isEqualTo("애플(야후)");
    }

    @Test
    @DisplayName("productRepository와 야후 시세 둘 다 없으면 ticker 그대로 사용한다")
    void getNagNotification_stockName_fallsBackToTicker() {
        Notifications nag = notification(Notifications.NotificationType.NAG_50, "원본 문구");
        MiniChallenges challenge = MiniChallenges.builder()
                .id(UUID.randomUUID()).title("챌린지").rewardStockTicker("AAPL")
                .estimatedSaving(10_000L).build();
        given(notificationRepository.findByIdAndUserId(notificationId, userId)).willReturn(Optional.of(nag));
        given(miniChallengesRepository.findFirstByUserIdAndStatus(any(), any())).willReturn(Optional.of(challenge));
        given(yahooFinanceService.getStockDetail("AAPL", 10_000L)).willReturn(null); // 외부 API 실패
        given(productRepository.findFirstByTicker("AAPL")).willReturn(Optional.empty());

        NagNotificationResponseDto result = notificationService.getNagNotification(userId, notificationId);

        assertThat(result.getStockName()).isEqualTo("AAPL");
        assertThat(result.getAffordableShares()).isNull();
    }

    @Test
    @DisplayName("CHALLENGE_COMPLETE이고 절약액·구매가능주식수가 있으면 현재가 기준으로 문구를 재계산한다")
    void getNagNotification_challengeComplete_recalculatesContentWithCurrentPrice() {
        Notifications nag = notification(Notifications.NotificationType.CHALLENGE_COMPLETE, "저장된 옛날 문구");
        MiniChallenges challenge = MiniChallenges.builder()
                .id(UUID.randomUUID()).title("챌린지").rewardStockTicker("AAPL")
                .estimatedSaving(50_000L).build();
        given(notificationRepository.findByIdAndUserId(notificationId, userId)).willReturn(Optional.of(nag));
        given(miniChallengesRepository.findFirstByUserIdAndStatusOrderByCompletedAtDesc(any(), any()))
                .willReturn(Optional.of(challenge));
        given(yahooFinanceService.getStockDetail("AAPL", 50_000L))
                .willReturn(StockDetailResponseDto.builder().name("애플").currentPrice(250.0).affordableShares(1.5).build());
        given(productRepository.findFirstByTicker("AAPL")).willReturn(Optional.empty());

        NagNotificationResponseDto result = notificationService.getNagNotification(userId, notificationId);

        assertThat(result.getContent()).isNotEqualTo("저장된 옛날 문구");
        assertThat(result.getContent()).contains("50,000원", "애플", "1.50주", "250원");
    }

    @Test
    @DisplayName("CHALLENGE_COMPLETE라도 절약액이 없으면 저장된 content를 그대로 사용한다")
    void getNagNotification_challengeComplete_noEstimatedSaving_keepsOriginalContent() {
        Notifications nag = notification(Notifications.NotificationType.CHALLENGE_COMPLETE, "저장된 문구");
        MiniChallenges challenge = MiniChallenges.builder()
                .id(UUID.randomUUID()).title("챌린지").rewardStockTicker("AAPL")
                .estimatedSaving(null).build();
        given(notificationRepository.findByIdAndUserId(notificationId, userId)).willReturn(Optional.of(nag));
        given(miniChallengesRepository.findFirstByUserIdAndStatusOrderByCompletedAtDesc(any(), any()))
                .willReturn(Optional.of(challenge));
        given(yahooFinanceService.getStockDetail(eq("AAPL"), any()))
                .willReturn(StockDetailResponseDto.builder().name("애플").currentPrice(250.0).affordableShares(1.5).build());
        given(productRepository.findFirstByTicker("AAPL")).willReturn(Optional.empty());

        NagNotificationResponseDto result = notificationService.getNagNotification(userId, notificationId);

        assertThat(result.getContent()).isEqualTo("저장된 문구");
    }

    @Test
    @DisplayName("조회한 잔소리 알림은 읽음 처리된다")
    void getNagNotification_marksAsRead() {
        Notifications nag = notification(Notifications.NotificationType.NAG_50, "내용");
        given(notificationRepository.findByIdAndUserId(notificationId, userId)).willReturn(Optional.of(nag));
        given(miniChallengesRepository.findFirstByUserIdAndStatus(any(), any())).willReturn(Optional.empty());

        notificationService.getNagNotification(userId, notificationId);

        assertThat(nag.getIsRead()).isTrue();
    }

    // ──────────────────────────────────────
    // helpers
    // ──────────────────────────────────────

    /** notificationRepository.save()가 실제 DB insert처럼 생성된 id를 채워 반환하도록 스텁 (Map.of()는 null 값을 허용하지 않음) */
    private void stubSaveAssignsId() {
        given(notificationRepository.save(any())).willAnswer(inv -> {
            Notifications n = inv.getArgument(0);
            ReflectionTestUtils.setField(n, "id", UUID.randomUUID());
            return n;
        });
    }

    private Notifications notification(Notifications.NotificationType type, String content) {
        return Notifications.builder()
                .id(notificationId)
                .type(type)
                .title("제목")
                .content(content)
                .isRead(false)
                .sentAt(LocalDateTime.now())
                .build();
    }
}
