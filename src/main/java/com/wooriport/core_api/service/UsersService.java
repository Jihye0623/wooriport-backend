package com.wooriport.core_api.service;

import com.wooriport.core_api.base.dto.user.PortiSurveyRequestDto;
import com.wooriport.core_api.base.dto.user.PortiSurveyResultDto;
import com.wooriport.core_api.base.dto.user.UserGoalResponseDto;
import com.wooriport.core_api.base.dto.user.UserGoalUpdateRequestDto;
import com.wooriport.core_api.base.exception.UserNotFoundException;
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

    @Transactional(readOnly = true)
    public UserGoalResponseDto getGoal(UUID userId) {
        Users user = userRepository.findById(userId)
                .orElseThrow(UserNotFoundException::new);
        return UserGoalResponseDto.builder()
                .stockThemes(user.getStockThemes())
                .lifeGoal(user.getLifeGoal())
                .build();
    }

    @Transactional
    public void updateGoal(UUID userId, UserGoalUpdateRequestDto request) {
        Users user = userRepository.findById(userId)
                .orElseThrow(UserNotFoundException::new);
        if (request.getStockThemes() != null) user.updateStockThemes(request.getStockThemes());
        if (request.getLifeGoal() != null) user.updateLifeGoal(request.getLifeGoal());
    }

    @Transactional
    public void withdraw(UUID userId) {
        Users user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException());

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

        int investScore = 0;   // 투자성향 (0~7, 높을수록 공격적) — Q1·Q3·Q5·Q6·Q7·Q8·Q9
        int longTermScore = 0; // 시간관념 (0~3, 높을수록 장기 지향) — Q2·Q4·Q10

        // Q1: 기다리던 보너스 — 투자성향
        // A = 바로 쓸 곳 떠올림(소비·적극) +1, B = 일단 넣어두자(저축·안전)
        if ("A".equals(answers.get(0))) investScore++;

        // Q2: 매달 저축 스타일 — 시간관념
        // A = 월 얼마씩 꾸준히 적립(장기) +1, B = 남은 돈 저축(즉흥·단기)
        if ("A".equals(answers.get(1))) longTermScore++;

        // Q3: 돈 모으는 이유 — 투자성향
        // A = 원하는 것 위해(욕구·적극) +1, B = 비상금 대비(안전)
        if ("A".equals(answers.get(2))) investScore++;

        // Q4: 3년 내 목돈 이벤트 — 시간관념
        // A = 결혼·이사·차 등 임박(단기), B = 딱히 없음·먼 일(장기) +1
        if ("B".equals(answers.get(3))) longTermScore++;

        // Q5: 주식 -15% 대응 — 투자성향
        // A = 손절·회피(안전), B = 추가매수(공격) +1
        if ("B".equals(answers.get(4))) investScore++;

        // Q6: 예적금 vs 투자상품 — 투자성향
        // A = 원금보장 예적금(안전), B = 손실가능 투자상품(공격) +1
        if ("B".equals(answers.get(5))) investScore++;

        // Q7: 핫한 주식 접근법 — 투자성향
        // A = 분석 후 신중 시작(안전), B = 일단 넣어보는 적극 행동(공격) +1
        if ("B".equals(answers.get(6))) investScore++;

        // Q8: 유튜브 영상 선택 — 투자성향
        // A = 적금 비법 영상(안전), B = 주식 수익 영상(공격) +1
        if ("B".equals(answers.get(7))) investScore++;

        // Q9: 이상적 자산 관리 — 투자성향
        // A = 남이 관리해줬으면(소극·안전), B = 직접 굴리고 컨트롤(적극·공격) +1
        if ("B".equals(answers.get(8))) investScore++;

        // Q10: 재테크 슬로건 — 시간관념
        // A = 쓸 땐 쓰고 모을 땐(균형·단기), B = 바짝 모아 시드머니(장기) +1
        if ("B".equals(answers.get(9))) longTermScore++;

        // ──────────────────────────────────────
        // 축별 판정 — 10문항을 두 축에 모두 배분
        // 투자성향 0~7 (Q1·Q3·Q5·Q6·Q7·Q8·Q9) → 0~2 안전형 / 3~4 중립형 / 5~7 투자형
        // 시간관념 0~3 (Q2·Q4·Q10)            → 0~1 단기형 / 2~3 장기형
        // ──────────────────────────────────────

        String investTendency;
        if (investScore <= 2) {
            investTendency = "안전형";
        } else if (investScore <= 4) {
            investTendency = "중립형";
        } else {
            investTendency = "투자형";
        }

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

        log.info("[PortiService] 유형 계산 완료 — userId: {}, 투자:{}, 시간:{} → {}",
                userId, investTendency, timePerspective, portiType);

        return PortiSurveyResultDto.builder()
                .portiType(portiType)
                .typeName(getTypeName(portiType))
                .description(user.getPortiComment())
                .investScore(investScore)
                .longTermScore(longTermScore)
                .investTendency(investTendency)
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

}
