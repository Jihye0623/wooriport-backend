# Phase 1 — Resilience (멱등 + 재처리 + 재계산)

- 브랜치: `exp/kafka-1-resilience` / 태그: `kafka-exp-v1`
- 목적: at-least-once 의 중복·유실 문제를 멱등 적재 + 급여 재처리 토픽 + 챌린지 DB 재계산으로 해결.
  정합성 확보의 대가(성능)를 baseline 과 비교해 수치화.
- 측정일: 2026-06-07

## 변경 요약 (운영 로직)
| 파트 | 변경 | 효과 |
|------|------|------|
| ① 멱등 적재 | `transactions.event_id`(unique) + `existsByEventId` 선검사 + `saveAndFlush` 충돌 시 `DuplicateEventException` | 중복 전달 시 중복 행 0 |
| ② 급여 재처리(1-1) | 실패 → `transaction-events.salary.retry` 발행 → `SalaryRetryConsumer` 가 급여만 재실행, 3회 초과 시 `.DLT` | 유실 불가 액션 유실 0 |
| ③ 챌린지 재계산(2-2) | 증분 실패 → `recomputeFromDb` 로 DB 매칭 거래 합 재계산 → Redis/DB 보정 | 증분 오차 자가복구(멱등) |
| F-1 결함주입 | `FaultInjector`(chaos.salary-fail / challenge-fail), 기본 off | 트랙B 검증용 |

설계 노트: `generateFromSalary` 가 이미 미확인 플랜 delete+재생성이라 **재실행해도 이체계획 멱등**.
→ 급여 재처리에 별도 dedup 불필요(알림은 재처리 시 중복 가능 — 후속 개선 여지).

## 트랙 A — 성능 (chaos OFF, baseline 동일 프로토콜)
환경/부하 동일: Ryzen 5 5600X, N=100k, rate=0, 단일 컨슈머.

| 지표 | Baseline (v0) | Phase 1 (v1) | 변화 |
|------|---------------|--------------|------|
| **consumer 처리량(정상상태)** | ~301 msg/s | **~248 msg/s** | **약 −18%** |
| tx.persist p50 | 2.49 ms | **3.15 ms** | +0.66 ms |
| tx.persist p95 | 3.01 ms | 3.67 ms | +0.66 ms |
| tx.challenge p50 | 0.57 ms | 0.57 ms | ~동일 |
| producer 발행 | 100k / 1.48s | 100k / 1.55s | ~동일(병목 아님) |

> **원인 분석**: 멱등 적재가 메시지당 ① `existsByEventId` SELECT 1회 + ② `saveAndFlush`(즉시 flush로
> unique 충돌 감지) 를 추가 → persist 가 2.49→3.15ms 로 늘고, 단일 스레드라 그대로 처리량 −18%.
> **정합성을 얻는 대신 성능을 내준 의도된 트레이드오프.** Phase 2(배치)·Phase 3(튜닝)에서 회복 대상.
> (E2E 지연은 버스트 백로그 대기시간이라 샘플 시점 의존적 — 단계 비교 지표로는 처리량/persist 사용.)

## 트랙 B — 정합성 / 결함 복구 증명
재현 가능한 자동 증거로 **통합테스트**([KafkaResilienceIntegrationTest](../../src/test/java/com/wooriport/core_api/integration/KafkaResilienceIntegrationTest.java),
EmbeddedKafka + Testcontainers)를 채택. 4개 시나리오 전부 green.

| ID | 결함 주입 | 기대 동작 | 검증 테스트 | 결과 |
|----|----------|----------|------------|------|
| D1 중복 적재 | 같은 event_id 2회 | 행 1건만(중복0), `tx.duplicate.detected`↑ | `duplicateEventId_persistsOnce` | ✅ |
| C1 챌린지 증분 실패 | challenge-fail once | DB 재계산으로 currentValue 정확 보정 | `challengeIncrementFails_recomputedFromDb` | ✅ |
| S1 급여 1회 실패 | salary-fail once | 재처리 토픽 → 재실행 성공(이체계획 생성) | `salaryFailsOnce_retriedAndSucceeds` | ✅ |
| S2 급여 영구 실패 | salary-fail always | 3회 재시도 후 DLT 격리(유실 아님) | `salaryFailsAlways_endsInDlt` | ✅ |

> 자동 테스트라 CI 에서 반복 검증 가능 = "정확히 처리됨"의 확정 증거.
> **라이브 스샷**(포폴 시각자료)은 chaos 토글로 실서버에서 재현 가능:
> `CHAOS_SALARY_FAIL=always` 로 기동 → 급여 이벤트 발행 → Kafka UI(8081)에서
> `transaction-events.salary.retry` / `.DLT` 토픽, Kibana 에서 `salary_dlt` 로그 trace 캡쳐.

## baseline 약점 → Phase 1 해소 확인
| baseline 약점 | Phase 1 |
|----------------|---------|
| 중복 전달 시 중복 행 적재 | event_id 멱등 → 중복 0 (D1) |
| 급여 실패 시 유실 | 재처리 토픽 + DLT → 유실 0 (S1/S2) |
| 챌린지 증분 오차 | DB 재계산 자가복구 (C1) |

## 재현 방법
HANDOFF.md §5(재현 절차) 참고.
- 트랙 A: `reset-db.ps1` → `mock_payment.py --count 100000` → 60s 구간 처리량 + `actuator/prometheus` 의 `tx_*`.
- 트랙 B(자동): `gradlew test --tests "*KafkaResilienceIntegrationTest"`.
- 트랙 B(라이브): `CHAOS_SALARY_FAIL`/`CHAOS_CHALLENGE_FAIL` 환경변수로 기동 후 시나리오 발행.

## 다음 단계(Phase 2) 근거
persist 가 메시지당 SELECT+flush 로 무거워짐 + 단일 스레드 → **배치 적재**(saveAll/JDBC batch,
배치 dedup 1쿼리) + poll 단위 트랜잭션으로 −18% 회복 목표.

## 비고
- application.yml 의 chaos 블록 들여쓰기 버그(spring.batch 를 chaos 자식으로 밀어내 batch.job.enabled
  무효화 → 다중 Job 컨텍스트 로딩 실패)를 통합테스트가 검출·수정.
- 측정 하네스(producer/대시보드)는 Phase 0 동결분 그대로 → mock-server/infra 는 v0 유지.
