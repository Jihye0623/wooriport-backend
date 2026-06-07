package com.wooriport.core_api.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Phase 1 결함 주입기. chaos.* 프로퍼티가 설정됐을 때만 동작하고, 기본값(none)이면 완전 no-op.
 * 운영 빌드/평상시엔 아무 영향 없음. 결함 복구(재처리/재계산)를 증명하는 트랙 B 전용.
 *
 *  - salary-fail   : 급여 처리(handleIfSalary)를 강제 실패시켜 재처리 토픽 경로를 검증
 *  - challenge-fail: 챌린지 증분(updateProgress)을 강제 실패시켜 DB 재계산 경로를 검증
 *
 * "once" 는 첫 1회만 실패(재시도/재계산이 성공으로 복구됨을 보임), "always" 는 매번 실패(DLT/지속복구).
 */
@Slf4j
@Component
public class FaultInjector {

    private final String salaryFail;
    private final String challengeFail;
    private final AtomicBoolean salaryFiredOnce = new AtomicBoolean(false);
    private final AtomicBoolean challengeFiredOnce = new AtomicBoolean(false);

    public FaultInjector(@Value("${chaos.salary-fail:none}") String salaryFail,
                         @Value("${chaos.challenge-fail:none}") String challengeFail) {
        this.salaryFail = salaryFail;
        this.challengeFail = challengeFail;
    }

    public void maybeFailSalary() {
        if (shouldFail(salaryFail, salaryFiredOnce)) {
            throw new RuntimeException("[chaos] 급여 처리 강제 실패 (mode=" + salaryFail + ")");
        }
    }

    public void maybeFailChallenge() {
        if (shouldFail(challengeFail, challengeFiredOnce)) {
            throw new RuntimeException("[chaos] 챌린지 증분 강제 실패 (mode=" + challengeFail + ")");
        }
    }

    private boolean shouldFail(String mode, AtomicBoolean firedOnce) {
        if ("always".equals(mode)) return true;
        if ("once".equals(mode)) return firedOnce.compareAndSet(false, true);
        return false;
    }
}
