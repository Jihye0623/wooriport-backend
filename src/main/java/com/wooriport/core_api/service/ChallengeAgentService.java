package com.wooriport.core_api.service;

import com.wooriport.core_api.base.dto.challenge.ChallengeNagResponseDto;
import com.wooriport.core_api.base.dto.challenge.ChallengeProposalResponseDto;
import com.wooriport.core_api.base.dto.challenge.ChallengeRewardResponseDto;
import com.wooriport.core_api.domain.MiniChallenges;
import com.wooriport.core_api.base.exception.AiServiceException;
import com.wooriport.core_api.base.exception.UserNotFoundException;
import com.wooriport.core_api.domain.Transactions;
import com.wooriport.core_api.domain.Users;
import com.wooriport.core_api.repository.ProductRepository;
import com.wooriport.core_api.repository.TransactionRepository;
import com.wooriport.core_api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChallengeAgentService {

    private final WebClient webClient;
    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;

    @Value("${flask.ml-url}")
    private String flaskMlUrl;

    @Transactional(readOnly = true)
    public ChallengeProposalResponseDto recommend(UUID userId) {
        Map<String, Object> body = new HashMap<>();
        body.put("user_id", userId.toString());
        body.put("category_expense", fetchExpenses(userId));
        body.put("stock_themes", fetchStockThemes(userId));
        ChallengeProposalResponseDto res = callFlask("/mini_challenge", body, ChallengeProposalResponseDto.class);
        fillTickerName(res);
        return res;
    }

    public ChallengeProposalResponseDto adjust(UUID userId, String feedback) {
        Map<String, Object> body = Map.of(
                "user_id",  userId.toString(),
                "feedback", feedback
        );
        ChallengeProposalResponseDto res = callFlask("/mini_challenge/adjust", body, ChallengeProposalResponseDto.class);
        fillTickerName(res);
        return res;
    }

    public ChallengeRewardResponseDto reward(UUID userId, MiniChallenges challenge) {
        Map<String, Object> body = Map.of(
                "user_id",          userId.toString()
        );
        return callFlask("/mini_challenge/reward", body, ChallengeRewardResponseDto.class);
    }

    public ChallengeNagResponseDto nag(UUID userId, MiniChallenges challenge, int progressPct) {
        Map<String, Object> body = Map.of(
                "user_id",        userId.toString(),
                "title",          challenge.getTitle(),
                "category",       challenge.getCategory(),
                "challenge_type", challenge.getChallengeType().name(),
                "target",         challenge.getTarget(),
                "current",        challenge.getCurrentValue(),
                "progress_pct",   progressPct
        );
        return callFlask("/mini_challenge/nag", body, ChallengeNagResponseDto.class);
    }

    private void fillTickerName(ChallengeProposalResponseDto dto) {
        if (dto.getTicker() == null || dto.getTicker().isBlank()) return;
        productRepository.findFirstByTicker(dto.getTicker())
                .ifPresent(p -> dto.setTickerName(p.getName()));
    }

    private List<Map<String, Object>> fetchExpenses(UUID userId) {
        LocalDateTime from = LocalDateTime.now().minusMonths(1);
        LocalDateTime to   = LocalDateTime.now();
        return transactionRepository.findExpensesBetween(userId, from, to).stream()
                .map(this::toExpenseMap)
                .toList();
    }

    private Map<String, Object> toExpenseMap(Transactions t) {
        Map<String, Object> m = new HashMap<>();
        m.put("amount",         t.getAbsAmount());
        m.put("category",       t.getCategory());
        m.put("sender_name",    t.getSenderName());
        m.put("transaction_at", t.getTransactionAt().toString());
        return m;
    }

    private List<String> fetchStockThemes(UUID userId) {
        Users user = userRepository.findById(userId).orElseThrow(UserNotFoundException::new);
        return user.getStockThemes();
    }

    private <T> T callFlask(String path, Map<String, Object> body, Class<T> responseType) {
        try {
            T response = webClient.post()
                    .uri(flaskMlUrl + path)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(responseType)
                    .block();

            if (response == null) {
                throw new IllegalStateException("AI 서버 응답이 없습니다.");
            }
            return response;

        } catch (Exception e) {
            log.error("[ChallengeAgent] FastAPI 호출 실패 — path: {}, 사유: {}", path, e.getMessage());
            throw new AiServiceException("AI 서버 호출 실패: " + e.getMessage());
        }
    }
}
