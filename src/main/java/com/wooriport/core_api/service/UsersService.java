package com.wooriport.core_api.service;

import com.wooriport.core_api.base.dto.user.PortiSurveyRequestDto;
import com.wooriport.core_api.base.dto.user.PortiSurveyResultDto;
import com.wooriport.core_api.domain.Users;
import com.wooriport.core_api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UsersService {
    private final UserRepository userRepository;

    @Transactional
    public void withdraw(UUID userId) {
        Users user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 유저입니다."));

        if (user.getStatus() == Users.UserStatus.WITHDRAWN) {
            throw new IllegalArgumentException("이미 탈퇴 처리된 계정입니다.");
        }

        // Soft Delete 상태로 변경 (status = WITHDRAWN, deleteAt = 현재시간)
        user.withdraw();
    }

    @Transactional
    public PortiSurveyResultDto calculateAndSave(UUID userId, PortiSurveyRequestDto request) {
        List<String> answers = request.getAnswers();

        if (answers.size() != 10) {
            throw new IllegalArgumentException("10개 문항에 모두 답변해주세요.");
        }

        // ──────────────────────────────────────
        // 축별 채점
        // ──────────────────────────────────────

        int investScore = 0;   // 축 A: 투자형 점수 (높을수록 투자형)
        int activeScore = 0;   // 축 B: 능동형 점수 (높을수록 능동형)
        int longTermScore = 0; // 축 C: 장기형 점수 (높을수록 장기형)

        // Q1: ETF 카톡 반응 — 축 A
        // A = 안전형, B = 투자형 +1
        if ("B".equals(answers.get(0))) investScore++;

        // Q2: 이상적인 월급날 — 축 B
        // A = 시스템형, B = 능동형 +1
        if ("B".equals(answers.get(1))) activeScore++;

        // Q3: 종잣돈 1000만원 — 축 C
        // A = 단기형, B = 장기형 +1
        if ("B".equals(answers.get(2))) longTermScore++;

        // Q4: 투자앱 -5% 확인 — 축 A
        // A = 안전형, B = 투자형 +1
        if ("B".equals(answers.get(3))) investScore++;

        // Q5: 유튜브 영상 선택 — 축 A + 축 B
        // A = 안전형 / 시스템형, B = 투자형 +1 / 능동형 +1
        if ("B".equals(answers.get(4))) {
            investScore++;
            activeScore++;
        }

        // Q6: 돈 모으는 이유 — 축 C
        // A = 단기형, B = 장기형 +1
        if ("B".equals(answers.get(5))) longTermScore++;

        // Q7: 적금 상품 선택 — 축 A
        // A = 안전형, B = 투자형 +1
        if ("B".equals(answers.get(6))) investScore++;

        // Q8: 돈 현황 체크 빈도 — 축 B
        // A = 시스템형, B = 능동형 +1
        if ("B".equals(answers.get(7))) activeScore++;

        // Q9: 취미 생활 — 축 A + 축 B
        // A = 투자형 +1 / 능동형 +1, B = 안전형 / 시스템형
        if ("A".equals(answers.get(8))) {
            investScore++;
            activeScore++;
        }

        // Q10: 5년 안에 결혼 계획 — 축 C
        // A = 단기형, B = 장기형 +1
        if ("B".equals(answers.get(9))) longTermScore++;

        // ──────────────────────────────────────
        // 축별 판정
        // 축 A: 투자형 점수 0~5 → 0~1 안전형 / 2~3 중립형 / 4~5 투자형
        // 축 C: 장기형 점수 0~3 → 0~1 단기형 / 2~3 장기형
        // ──────────────────────────────────────

        String investTendency;
        if (investScore <= 1) {
            investTendency = "안전형";
        } else if (investScore <= 3) {
            investTendency = "중립형";
        } else {
            investTendency = "투자형";
        }

        String managementStyle = activeScore >= 2 ? "능동형" : "시스템형";
        String timePerspective = longTermScore >= 2 ? "장기형" : "단기형";

        // ──────────────────────────────────────
        // 최종 유형 결정
        // 축 A(투자성향 3단계) × 축 C(시간관념 2단계) = 6유형
        //
        // 안전형 + 단기형 → SWIMMING  (수영)
        // 안전형 + 장기형 → ARCHERY   (양궁)
        // 중립형 + 단기형 → JUDO      (유도)
        // 중립형 + 장기형 → RHYTHMIC  (리듬체조)
        // 투자형 + 단기형 → FENCING   (펜싱)
        // 투자형 + 장기형 → CYCLING   (사이클)
        // ──────────────────────────────────────

        Users.PortiType portiType = switch (investTendency + "_" + timePerspective) {
            case "안전형_단기형" -> Users.PortiType.SWIMMING;
            case "안전형_장기형" -> Users.PortiType.ARCHERY;
            case "중립형_단기형" -> Users.PortiType.JUDO;
            case "중립형_장기형" -> Users.PortiType.RHYTHMIC;
            case "투자형_단기형" -> Users.PortiType.FENCING;
            case "투자형_장기형" -> Users.PortiType.CYCLING;
            default              -> Users.PortiType.JUDO;
        };

        // DB 저장
        Users user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        user.updatePortiType(portiType);

        log.info("[PortiService] 유형 계산 완료 — userId: {}, 투자:{}, 관리:{}, 시간:{} → {}",
                userId, investTendency, managementStyle, timePerspective, portiType);

        return PortiSurveyResultDto.builder()
                .portiType(portiType)
                .typeName(getTypeName(portiType))
                .description(getDescription(portiType))
                .investScore(investScore)
                .activeScore(activeScore)
                .longTermScore(longTermScore)
                .investTendency(investTendency)
                .managementStyle(managementStyle)
                .timePerspective(timePerspective)
                .build();
    }

    private String getTypeName(Users.PortiType type) {
        return switch (type) {
            case SWIMMING -> "수영";
            case ARCHERY  -> "양궁";
            case JUDO     -> "유도";
            case RHYTHMIC -> "리듬체조";
            case FENCING  -> "펜싱";
            case CYCLING  -> "사이클";
        };
    }

    private String getDescription(Users.PortiType type) {
        return switch (type) {
            case SWIMMING -> "안정적으로 모으고, 목표는 빠르게. 리스크는 낮추고 실행은 빠르게 하는 타입이에요.";
            case ARCHERY  -> "안전하게, 그리고 멀리. 꾸준히 쌓아가는 장기 안정형 투자자예요.";
            case JUDO     -> "균형 잡힌 감각으로 단기 목표를 빠르게 달성하는 타입이에요.";
            case RHYTHMIC -> "균형과 지속성을 중시하는 장기 중립형이에요.";
            case FENCING  -> "공격적이고 빠른 투자로 단기 수익을 노리는 타입이에요.";
            case CYCLING  -> "높은 수익을 향해 장기적으로 달려가는 투자 지향형이에요.";
        };
    }
}
