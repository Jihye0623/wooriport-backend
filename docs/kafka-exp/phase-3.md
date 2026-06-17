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

> **측정**: 2026-06-09 · AMD Ryzen 5 5600X (6C/12T) · 18유저/18카드 · N=100k · 런마다 토픽 클린 재생성.

| Run | max-poll-records | poll 횟수 | persist 합산 | persist avg/poll | E2E max | 처리량 (msg/s) | MPR-A 대비 |
|-----|-----------------|---------|------------|----------------|--------|--------------|----------|
| MPR-A | 500 | 206 | 18.95 s | 0.092 s | 67.68 s | 1,478 | 기준 |
| MPR-B | 1000 | 104 | 18.72 s | 0.180 s | 64.72 s | 1,545 | +5% |
| **MPR-C** | **2000** | 53 | 18.15 s | 0.343 s | 61.17 s | **1,635** | **+11% ← 최적** |

> **데스크탑에선 mpr 이 클수록 단조 증가**(2000 이 최적). persist avg/poll 은 0.09→0.18→0.34 s 로
> 배치 크기에 **선형** 비례할 뿐, 간이테스트(노트북)에서 본 비선형 폭증/역전은 없었다.
> persist 합산(≈18 s)이 mpr 과 무관하게 일정 → DB 적재 총비용은 동일, 큰 배치일수록 poll/커밋 오버헤드만 줄어 이득.
>
> <details><summary>간이테스트(노트북, N=10k) 참고값 — 데스크탑에서 뒤집힘</summary>
>
> | mpr | E2E max | 처리량 | 비고 |
> |-----|--------|------|-----|
> | 500 | 24.07 s | ~415 | |
> | 1000 | 11.27 s | ~887 | 노트북 최적 |
> | 2000 | 15.51 s | ~645 | 노트북에선 역전 — 약한 머신의 DB 트랜잭션 병목 아티팩트 |
> </details>

---

### 2단계: partitions × concurrency (max-poll-records=최적값 고정)

> **측정**: mpr=2000(1단계 최적) 고정 · partitions=concurrency 로 동시 증설 · 나머지 통제변수 동일.
> 계획표(PAR-A/B/C)는 p3c1/p3c3/p6c6 였고, 6c6 에서도 처리량이 계속 올라가 **물리 스레드 수(12)까지 확장**해 피크를 확정했다.

| Run | partitions | concurrency | persist wall-clock | persist p50 / p95 | E2E max | 처리량 (msg/s) | c1 대비 |
|-----|-----------|-------------|------------------|-----------------|--------|--------------|--------|
| PAR-A (=MPR-C) | 3 | 1 | 18.15 s | 327 / 377 ms | 61.17 s | 1,635 | 기준 |
| PAR-B | 3 | 3 | 10.64 s | 566 / 868 ms | 43.34 s | 2,307 | +41% |
| PAR-C | 6 | 6 | 8.13 s | 736 / 1609 ms | 38.59 s | 2,591 | +59% |
| 확장 | 9 | 9 | 7.93 s | 972 / 2012 ms | 35.68 s | 2,803 | +71% |
| **확장** | **12** | **12** | 6.18 s | 971 / 2011 ms | 31.88 s | **3,137** | **+92% ← 피크** |
| 확장 | 18 | 18 | 5.86 s | 1274 / 2818 ms | 34.34 s | 2,912 | +78% (하락) |

> **concurrency = 물리 스레드 수(12)에서 피크**, 오버서브스크립션(18)에서 −7% 하락 — 교과서적 곡선.
> 간이테스트(노트북)의 "6c6 DB경합 붕괴(~485 msg/s)"는 **재현되지 않음**. 5600X(6C/12T)+Postgres 는
> 12 스레드 동시 batch insert 를 경합 붕괴 없이 소화한다(persist p95 는 오르지만 병렬 이득이 우위).
>
> ⚠️ **키 스큐 주의**: 18카드(=고유 키 18) 기준 균등분산 규칙은 partitions ≤ 6 (키/파티션 ≥ 3).
> p9~p12 는 일부 파티션에 키가 몰리는 스큐가 있으나 DB writer 병렬성 이득이 더 커서 계속 증가했고,
> p18(키/파티션=1)에서 오버서브스크립션 손해로 꺾였다.

---

## 최적 설정

```yaml
# 결론: mpr=2000 + partitions=12 + concurrency=12 → 3,137 msg/s (baseline 305.8 의 10.3배)
#   측정: 2026-06-09 · Ryzen 5 5600X (6C/12T) · 18유저 · N=100k
spring:
  kafka:
    consumer:
      max-poll-records: 2000

wooriport:
  kafka:
    partitions: 12
    consumer-concurrency: 12
```

> **선택 가이드**
> - `concurrency` 는 **물리 스레드 수**에 맞춘다(이 머신=12). 다른 머신은 코어/스레드 수에 맞춰 재측정.
> - 12×12 는 이 박스의 **raw 최대(3,137 msg/s)** 지만 18카드에서 키 스큐가 있다.
> - **키 분산이 깨끗한 안정 설정 = p6×c6 (2,591 msg/s)** — 균등분산 규칙(키/파티션 ≥ 3)을 지키는 최대.
>   균형을 중시하면 6×6, 이 박스에서 절대 처리량을 짜내려면 12×12.

---

## 전체 단계 진척 (데스크탑 재베이스라인, 18유저, N=100k)

| Phase | 설정 | 처리량 | 직전 대비 | baseline 대비 |
|-------|------|--------|----------|--------------|
| Phase 0 baseline | mpr500, c1 | 305.8 msg/s | — | 1.0× |
| Phase 1 resilience(멱등) | mpr500, c1 | 253.8 msg/s | −17% | 0.83× |
| Phase 2 batch | mpr500, c1 | 1,485 msg/s | +485% | 4.9× |
| Phase 3 최적 | mpr2000, p12×c12 | **3,137 msg/s** | +111% | **10.3×** |

> 정합성(멱등·재처리·재계산)을 지키며 Phase 1 에서 내준 −17% 를 Phase 2 배치가 회복(+초과),
> Phase 3 튜닝이 병렬화로 baseline 의 10.3배까지 끌어올렸다.

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
