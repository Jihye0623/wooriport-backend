# Phase 3 — Kafka 파라미터 튜닝 스윕

- 브랜치: `exp/kafka-3-tuning` / 태그: `kafka-exp-v3`
- 기준선: Phase 2-1 결과 기반
- 데이터셋: 유저 18명 / 카드 18장 / N=100,000 건
- 목적: `max-poll-records` → `partitions × concurrency` 순서로 스윕해 최적값을 수치로 확정.

> 실험 절차 상세는 `PHASE3-GUIDE.md` 참고.

---

## 실험 대상 파라미터

| 파라미터 | 위치 | 기본값 | 역할 |
|---------|------|--------|------|
| `max-poll-records` | `spring.kafka.consumer` | 500 | poll 1회당 처리 건수. DB batch insert 크기 직접 결정 |
| `partitions` | `wooriport.kafka.partitions` | 3 | 토픽 파티션 수. 병렬 처리 단위 |
| `consumer-concurrency` | `wooriport.kafka.consumer-concurrency` | 1 | 컨슈머 스레드 수. 반드시 ≤ partitions |

> **스킵 파라미터**
> - `fetch.min.bytes` / `fetch.max.wait.ms`: 100k 메시지를 먼저 전부 적재 후 소비하는 구조라
>   fetch 조건이 항상 즉시 충족됨. `max-poll-records` 가 더 강한 병목이라 효과 없음.
> - `linger.ms` / `batch.size` / `compression.type` / `acks`: `application.yml` producer 설정은
>   Spring `KafkaTemplate`(급여 retry 토픽)에만 적용됨. 부하 생성원인 `mock_payment.py`(Python)와 무관.

---

## 스윕 계획

### 원칙
단일 스레드 효율(`max-poll-records`)을 먼저 확정하고, 그 위에 병렬화(`partitions × concurrency`)를 얹는다.
반대 순서로 하면 어느 파라미터가 처리량에 기여했는지 분리할 수 없다.

---

### 1단계: max-poll-records 스윕

**고정**: `partitions=3`, `concurrency=1`

```yaml
# application.yml
spring:
  kafka:
    consumer:
      max-poll-records: 500   # ← 이 값만 변경

wooriport:
  kafka:
    partitions: 3
    consumer-concurrency: 1
```

| Run | max-poll-records |
|-----|-----------------|
| MPR-A | 500 |
| MPR-B | 1000 |
| MPR-C | 2000 |

---

### 2단계: partitions × concurrency 스윕

**고정**: `max-poll-records = 1단계 최적값`

```yaml
# application.yml
spring:
  kafka:
    consumer:
      max-poll-records: <1단계 최적값>

wooriport:
  kafka:
    partitions: 3             # ← Run 마다 변경
    consumer-concurrency: 1   # ← Run 마다 변경 (반드시 ≤ partitions)
```

> 파티션 **늘리기**: `wooriport.kafka.partitions` 변경 후 재기동 → NewTopics 빈이 자동 증가.
> 파티션 **줄이기**: Kafka UI(localhost:8081) → Topics → transaction-events → Delete → 재기동.

| Run | partitions | concurrency |
|-----|-----------|-------------|
| PAR-A | 3 | 1 |
| PAR-B | 3 | 3 |
| PAR-C | 6 | 6 |

---

## 결과 표

### 1단계: max-poll-records (partitions=3, concurrency=1 고정)

| Run | max-poll-records | poll 횟수 | persist 합산 | persist avg/poll | E2E max | 처리량 (msg/s) | MPR-A 대비 |
|-----|-----------------|---------|------------|----------------|--------|--------------|----------|
| MPR-A | 500 | | | | | | 기준 |
| MPR-B | 1000 | | | | | | |
| MPR-C | 2000 | | | | | | |

> **간이 테스트 참고값** (N=10k, 재측정 필요):
>
> | max-poll-records | poll 횟수 | persist 합산 | persist avg/poll | E2E max | 처리량 |
> |-----------------|---------|------------|----------------|--------|------|
> | 500 | 20 | 3.87 s | 0.194 s | 24.07 s | ~415 msg/s |
> | **1000** | 12 | 2.23 s | 0.186 s | 11.27 s | **~887 msg/s ← 최적** |
> | 2000 | 5 | 2.26 s | 0.453 s | 15.51 s | ~645 msg/s |
>
> 2000에서 persist avg/poll 이 2.4배 증가 → DB 트랜잭션 비용 비선형 증가 확인.

---

### 2단계: partitions × concurrency (max-poll-records=최적값 고정)

| Run | partitions | concurrency | persist 합산 | persist wall-clock | persist p50 / p95 | E2E max | 처리량 (msg/s) | PAR-A 대비 |
|-----|-----------|-------------|------------|------------------|-----------------|--------|--------------|----------|
| PAR-A | 3 | 1 | | | | | | 기준 |
| PAR-B | 3 | 3 | | | | | | |
| PAR-C | 6 | 6 | | | | | | |

> **간이 테스트 참고값** (N=10k, max-poll-records=1000, 재측정 필요):
>
> | Run | partitions | concurrency | persist wall-clock | E2E max | 처리량 | 비고 |
> |-----|-----------|-------------|------------------|--------|------|-----|
> | PAR-A | 3 | 1 | 2.23 s | 11.27 s | ~887 msg/s | 기준 |
> | **PAR-B** | **3** | **3** | **0.90 s** | **6.73 s** | **~1,486 msg/s** | **최적** |
> | PAR-C | 6 | 6 | 1.31 s | 20.62 s | ~485 msg/s | DB 경합으로 역전 |
>
> PAR-C 역전 원인: 6스레드 × 1000건 동시 batch insert → PostgreSQL 커넥션/락 경합.
> persist p50 이 0.19 s → 1.14 s 로 6배 폭증. concurrency 늘릴수록 mpr 을 줄여야 함.

---

## 최적 설정 (측정 완료 후 기입)

```yaml
# 결론: Run ___ 기준
spring:
  kafka:
    consumer:
      max-poll-records: ?

wooriport:
  kafka:
    partitions: ?
    consumer-concurrency: ?
```

---

## 재현 방법

```powershell
# 1. application.yml 파라미터 변경 후 저장

# 2. DB 리셋
powershell -ExecutionPolicy Bypass -File .\scripts\kafka-exp\reset-db.ps1

# 3. 백엔드 재기동
.\gradlew.bat bootRun --console=plain

# 4. 파티션 할당 완료 대기 (필수)
#    로그에서 "partitions assigned: [transaction-events-0, ...]" 확인 후 다음 단계

# 5. 부하 발생
..\mock-server\venv\Scripts\python.exe ..\mock-server\mock_payment.py --count 100000 --rate 0

# 6. 메트릭 수집
(Invoke-WebRequest -UseBasicParsing http://localhost:8080/actuator/prometheus).Content `
    -split "`n" | Select-String '^tx_'
```

> 통제 변수: 유저 18명 · 카드 18장 · N=100,000 · 서버 재기동(JVM 웜업 포함) · 다른 부하 없음.
> 파티션 수 변경 후 rebalance 전 부하 시작 시 E2E 가 비정상적으로 높게 찍힘 — 반드시 대기.
