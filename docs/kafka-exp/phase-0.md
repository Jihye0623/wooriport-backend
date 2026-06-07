# Phase 0 — Baseline

- 브랜치: `exp/kafka-0-baseline` / 태그: `kafka-exp-v0`
- 목적: 현재 코드(로직 무변경) 그대로의 처리량·지연·정합성 특성을 측정해 이후 단계의 기준선 확보
- 측정일: 2026-06-07

## 환경
| 항목 | 값 |
|------|----|
| 머신 | AMD Ryzen 5 5600X (6C/12T), RAM 16GB, Windows 11 |
| 인프라 | docker compose (kafka 3.7.2 단일노드, postgres pgvector:pg16, redis 7.2) |
| 백엔드 | Spring Boot 3.5.14, Java 21, 호스트에서 bootRun(:8080) |
| Kafka | 토픽 `transaction-events`, 파티션 3, 복제 1 |
| 컨슈머 | groupId `approval-detect-group`, **concurrency 1**(기본), `max.poll.records=500`, enable.auto.commit=false |
| 부하 | mock_payment.py `--count 100000` (버스트, sleep 없음) |

## 측정 파라미터
- N = 100,000, rate = 0(최대속도), 워밍업 5,000(폐기)
- chaos OFF (결함 주입 없음)

## 결과

### 처리량
| 지표 | 값 |
|------|----|
| **producer 발행 속도** | 100,000건 / **1.48s** / ~67,000 msg/s |
| **consumer 처리량(정상상태)** | **~301 msg/s** (60s 구간 델타 18,151건 기준) |
| 100k 전량 적재 | 180s 내 미완료(드레인 진행 중 ~52k) → 단일스레드 한계 |

> producer는 1.5초 만에 100k를 쏟아붓지만 consumer가 **단일 스레드·메시지당 DB 왕복**이라
> ~300 msg/s 가 천장. **컨슈머가 명확한 병목.**

### 지연 (Micrometer, 본런 직후)
| Timer | p50 | p95 | p99 |
|-------|-----|-----|-----|
| **tx.e2e.latency** (produce→consume) | **249 s** | 266 s | 266 s |
| tx.persist (DB 적재) | 2.49 ms | 3.01 ms | 4.06 ms |
| tx.challenge (Redis 체크, 조기 return) | 0.57 ms | 0.70 ms | 0.90 ms |
| tx.salary (조기 return) | ~1 µs | ~2 µs | ~2 µs |

> E2E 지연 249초는 **백로그 대기시간**. 100k가 즉시 적재돼 큐에 쌓이고 300/s로 빠지니
> 뒤쪽 메시지는 수 분을 대기. 버스트 + 느린 컨슈머의 전형적 패턴.
> 메시지당 처리비용은 persist(2.5ms)가 지배적 — 300/s(≈3.3ms/건) 예산의 대부분.

### 정합성 (baseline 특성 = 개선 전 약점)
- **멱등 적재 없음**: event_id 페이로드엔 있으나 컨슈머가 사용 안 함 → 동일 event_id 재전송 시 **중복 행 적재**(Phase 1에서 증명·해결 예정).
- **급여 유실 가능**: handleIfSalary 실패 시 best-effort 로깅만 → 유실(Phase 1 재처리 토픽으로 해결).
- **챌린지 오차 가능**: Redis 증분이라 중복/실패 시 카운트 틀어짐(Phase 1 DB 재계산으로 해결).
- 이번 baseline 부하는 CREDIT_CARD(출금=음수, income 아님) + 진행중 챌린지 없음이라
  challenge/salary는 조기 return → 실패 카운터 0. 결함 주입 증명은 Phase 1 트랙 B에서 수행.

## 병목 분석 & 개선 레버 (다음 단계 근거)
1. **단일 컨슈머 스레드** → Phase 3: `concurrency` ↑ + 파티션 ↑ (병렬 소비).
2. **메시지당 DB insert + 커밋** → Phase 2: 배치 insert(saveAll/JDBC batch) + poll 단위 트랜잭션.
3. **show-sql=true / format_sql=true** (application.yml): 매 insert 마다 SQL 콘솔 로깅 → 무시 못 할 오버헤드.
   현재 코드 설정 그대로 측정한 값이며, 성능 단계에서 끌 수 있는 명백한 레버(별도 기록).
4. persist 내부 `findByAssetNumber` 조회 1회 → 배치 단계에서 자산 캐싱/일괄조회 여지.

## 재현 방법
RUNBOOK.md 트랙 A 참고. 요약:
```
powershell -File scripts\kafka-exp\reset-db.ps1
python ..\mock-server\mock_payment.py --count 100000
# 정상상태 처리량 = 60s 구간 transactions count 델타
# 지연/카운터 = curl http://localhost:8080/actuator/prometheus | grep '^tx_'
```

## 비고
- 구 `dummy_kafka_test.sql` 은 현재 스키마(phone/finance_type 컬럼 제거)와 안 맞아 깨짐 →
  `seed_kafka_test.sql`(2026 스키마)로 대체, reset-db.ps1 이 이를 사용.
- mock_payment.py 는 Windows cp949 콘솔 대응 위해 stdout UTF-8 고정 + docker cp 기반 시드 적용.
