# Phase 3 실험 가이드 (담당자 인계용)

> **작성 배경**: 간이 테스트로 실험 방향을 확인한 뒤 정식 측정을 위해 작성.
> 데이터셋이 유저 3명 → 18명으로 변경됐으므로 Phase 0/1/2 재측정이 선행되어야 함.
> Phase 3 담당자는 §1 을 먼저 읽고 재측정 여부를 판단하세요.

---

## 1. 데이터셋 변경 영향 — Phase 0/1/2 재측정 필요 여부

### 왜 18명인가
`mock_payment.py` 는 DB 의 `CREDIT_CARD` asset_number 를 Kafka 메시지 키로 사용한다.
Kafka 는 murmur2 해시로 `키 → 파티션`을 결정하므로, 고유 키 수 < 파티션 수이면
일부 파티션이 비어 스레드가 낭비된다.

| 고유 카드 수 | 파티션 수 | 문제 |
|------------|---------|------|
| 3 | 3 | 해시 충돌 시 특정 파티션 과부하 가능 |
| 3 | 6 | 3개 파티션만 활성 → 3 스레드 idle |
| **18** | **6** | 파티션당 카드 3장 보장 → 균등 분산 |

**규칙: 고유 키 수 ≥ 파티션 수 × 3**

### Phase 0/1/2 재측정 여부

Phase 0/1/2 의 측정 지표는 `tx.persist`, `tx.e2e`, 처리량이고, 이 값들은 **DB 적재·정합성 로직**에 의존한다.
유저 수는 parallelStream 의 그룹 수에만 영향을 주는데, Phase 0/1/2 에서는 챌린지가 거의 없어 영향이 미미하다.

**결론: Phase 0/1/2 절대값 비교보다 같은 데이터셋으로 Phase 3 를 측정하는 것이 더 중요하다.**

| 재측정 필요도 | 이유 |
|-------------|------|
| Phase 0 — 낮음 | baseline 처리량은 카드 수에 무관 |
| Phase 1 — 낮음 | 정합성 로직 검증은 유저 수에 무관 |
| Phase 2 — 권장 | Phase 3 의 직접 기준선이므로 **동일 환경(18명)으로 맞추는 것이 깔끔함** |
| **Phase 3 — 필수** | 18명/18카드/N=100k 기준으로 측정 |

---

## 2. 사전 준비 (한 번)

```powershell
# 1. 인프라 기동 (postgres, redis, kafka, kafka-ui:8081, prometheus:9090)
cd C:\it\infra
docker compose up -d
docker ps   # 전부 Up 확인

# 2. backend 브랜치 확인
cd C:\Users\jihye\Desktop\fisache\backend
git branch  # exp/kafka-3-tuning 에 있어야 함
git log --oneline -5

# 3. application-secret.yml 존재 확인 (gitignore 파일 — 없으면 팀원에게 요청)
ls src\main\resources\application-secret.yml
```

**현재 seed**: `sql/seed_kafka_test.sql` — 유저 18명 + 카드 18장 (파티션당 3장)
**reset 스크립트**: `scripts/kafka-exp/reset-db.ps1` — transactions 초기화 + 시드 재적재

---

## 3. 공통 측정 절차 (매 Run 반복)

```powershell
# ── [1] application.yml 파라미터 변경 후 저장 ──────────────────────────────

# ── [2] DB 리셋 ────────────────────────────────────────────────────────────
powershell -ExecutionPolicy Bypass -File .\scripts\kafka-exp\reset-db.ps1

# ── [3] 백엔드 재기동 ──────────────────────────────────────────────────────
# 이전 프로세스 종료 후:
.\gradlew.bat bootRun --console=plain

# ── [4] 파티션 할당 완료 대기 (중요!) ────────────────────────────────────
# 로그에서 아래 메시지가 파티션 수만큼 뜰 때까지 기다린 후 부하 시작
# "partitions assigned: [transaction-events-0, ...-1, ...]"
# → 이 메시지 없이 바로 발행하면 E2E latency 에 rebalance 대기 시간이 포함됨

# ── [5] 부하 발생 ──────────────────────────────────────────────────────────
..\mock-server\venv\Scripts\python.exe ..\mock-server\mock_payment.py --count 100000 --rate 0

# ── [6] 메트릭 수집 (소비 완료 후) ────────────────────────────────────────
(Invoke-WebRequest -UseBasicParsing http://localhost:8080/actuator/prometheus).Content `
    -split "`n" | Select-String '^tx_'
```

**처리량 계산**:
- `처리량 = N ÷ E2E_max` (E2E_max = 마지막 메시지가 소비되는 데 걸린 시간)
- `persist wall-clock = tx_persist_seconds_sum ÷ concurrency` (실제 DB 처리 시간)

---

## 4. Phase 3 실험 순서

> **원칙**: 단일 스레드 효율(max-poll-records) 을 먼저 확정하고, 그 위에 병렬화(파티션/concurrency) 를 얹는다.
> 반대 순서로 하면 어느 것이 처리량에 기여했는지 알 수 없다.

---

### Step 1. max-poll-records 스윕

**목적**: poll 한 번에 처리하는 메시지 수의 최적값을 찾는다. 이 값이 DB batch insert 효율을 직접 결정한다.

**고정값**: `partitions=3`, `concurrency=1` (단일 스레드로 변수 분리)

#### 설정 변경 위치 (`application.yml`)

```yaml
spring:
  kafka:
    consumer:
      max-poll-records: 500    # ← 이 값만 바꿔가며 재기동
```

```yaml
wooriport:
  kafka:
    partitions: 3
    consumer-concurrency: 1
```

#### 실험 목록

| Run | max-poll-records | 예상 효과 |
|-----|-----------------|---------|
| MPR-A | 500 | 기준 |
| MPR-B | 1000 | poll 횟수 절반 → DB 왕복 감소 |
| MPR-C | 2000 | 배치 커지면 DB 트랜잭션 비용 증가 가능성 |

#### 간이 테스트 결과 (참고용, 재측정 필요)

| max-poll-records | poll 횟수 | persist 합산 | E2E max | 처리량 |
|-----------------|---------|------------|--------|------|
| 500 | 20 | 3.87 s | 24.07 s | ~415 msg/s |
| **1000** | 12 | 2.23 s | 11.27 s | **~887 msg/s** ← 최적 |
| 2000 | 5 | 2.26 s | 15.51 s | ~645 msg/s |

> **핵심 관찰**: 1000→2000 에서 persist avg/poll 이 0.19 s → 0.45 s 로 2.4배 증가.
> 배치가 너무 크면 DB 트랜잭션 비용이 비선형으로 늘어난다.
> **최적값: 1000** (재측정 후 확정)

---

### Step 2. partitions × concurrency 스윕

**목적**: Step 1 에서 확정된 max-poll-records 를 고정하고 파티션/스레드 수를 늘려 병렬화 효과를 측정한다.

**고정값**: `max-poll-records = Step 1 최적값 (예: 1000)`

#### 파티션 수 조절 방법

```
파티션 늘리기: application.yml 의 wooriport.kafka.partitions 값 증가 → 재기동
              (NewTopics 빈이 자동으로 증가시킴)

파티션 줄이기: Kafka UI (http://localhost:8081)
              → Topics → transaction-events → Delete
              → 백엔드 재기동 (설정값으로 재생성)
```

#### 설정 변경 위치 (`application.yml`)

```yaml
wooriport:
  kafka:
    partitions: 3              # ← Run 마다 변경
    consumer-concurrency: 1   # ← Run 마다 변경 (반드시 ≤ partitions)
```

#### 실험 목록

| Run | partitions | concurrency | 비고 |
|-----|-----------|-------------|-----|
| PAR-A | 3 | 1 | Step 1 과 동일 — 기준선 재확인 |
| PAR-B | 3 | 3 | 스레드 3배, 파티션은 유지 |
| PAR-C | 6 | 6 | 파티션 + 스레드 모두 2배 |

#### 간이 테스트 결과 (참고용, 재측정 필요)

| Run | partitions | concurrency | persist wall-clock | E2E max | 처리량 | A 대비 |
|-----|-----------|-------------|------------------|--------|------|------|
| PAR-A | 3 | 1 | 2.23 s | 11.27 s | ~887 msg/s | 기준 |
| **PAR-B** | **3** | **3** | **0.90 s** | **6.73 s** | **~1,486 msg/s** | **+68%** |
| PAR-C | 6 | 6 | 1.31 s | 20.62 s | ~485 msg/s | -45% |

> **주의**: PAR-C 가 PAR-B 보다 느린 이유 — 6 스레드가 동시에 1000건씩 batch insert 시도
> → PostgreSQL 커넥션/락 경합 발생. persist p50 이 0.19 s → 1.14 s 로 6배 폭증.
> **max-poll-records 가 클수록 고 concurrency 에서 DB 가 먼저 병목이 된다.**
>
> concurrency=6 을 쓰려면 max-poll-records 를 낮추거나 DB 커넥션 풀을 늘려야 한다.

---

### Step 3. (선택) concurrency × max-poll-records 조합 탐색

Step 2 에서 PAR-C 가 예상보다 느렸다면, 조합을 바꿔 추가 탐색한다.

| Run | partitions | concurrency | max-poll-records | 가설 |
|-----|-----------|-------------|-----------------|-----|
| OPT-A | 6 | 6 | 500 | mpr 줄이면 DB 부담 줄어 C(6-6) 가 빨라질 것 |
| OPT-B | 6 | 3 | 1000 | 파티션만 늘리고 스레드는 줄여 DB 경합 완화 |

---

## 5. 건너뛰어도 되는 실험

### fetch 튜닝 (fetch.min.bytes / fetch.max.wait.ms)

**건너뜀 이유**: 이 설정은 Kafka 브로커에 데이터가 부족할 때 대기 시간을 제어한다.
우리 실험은 100k 메시지를 먼저 전부 쌓아두고 소비하는 구조라 브로커에는 항상 데이터가 넘친다.
fetch 조건이 즉시 충족되므로 이 파라미터를 바꿔도 측정값이 변하지 않는다.
`max-poll-records` 제한이 더 강한 병목이다.

### producer 튜닝 (acks / compression-type / batch-size / linger.ms)

**건너뜀 이유**: `application.yml` 의 producer 설정은 Spring `KafkaTemplate` 에만 적용된다.
`KafkaTemplate` 은 급여 재처리 retry 토픽에만 쓴다.
실험 부하를 만드는 `mock_payment.py` 는 Python Confluent Kafka producer 로, `application.yml` 설정이 닿지 않는다.

---

## 6. 결과 기록

측정 결과는 `docs/kafka-exp/phase-3.md` 의 결과 표에 기입한다.

수집해야 할 값:
```
tx_persist_seconds_sum      → persist 합산
tx_persist_seconds_count    → poll 횟수
tx_persist_seconds{quantile="0.5"/"0.95"}  → persist p50/p95
tx_e2e_latency_seconds_max  → E2E max (처리량 계산 기준)
tx_e2e_latency_seconds{quantile="0.5"}     → E2E p50
tx_consumed_total           → 총 소비 건수 (N 확인용)
```

계산:
```
처리량 (msg/s)       = N ÷ tx_e2e_latency_seconds_max
persist wall-clock   = tx_persist_seconds_sum ÷ concurrency
persist avg/poll     = tx_persist_seconds_sum ÷ tx_persist_seconds_count
```

---

## 7. 알려진 함정

| 상황 | 증상 | 해결 |
|------|------|------|
| rebalance 전 부하 시작 | E2E max 이 30~40 s 로 폭증 (실제 처리는 1~2 s) | 백엔드 로그에서 "partitions assigned" 확인 후 발행 |
| 파티션 줄이기 시도 | 재기동해도 파티션 수 안 줄어듦 | Kafka UI 에서 토픽 삭제 후 재기동 |
| percentile 값이 모두 0 | histogram 버킷 미달 (짧은 latency) | `_max` 와 `_sum ÷ _count` 로 대체 계산 |
| PAR-C (6-6) 가 PAR-B (3-3) 보다 느림 | persist p50 이 1 s 이상 | DB 커넥션 경합 — mpr 줄이거나 concurrency 낮출 것 |
| 18명 유저 seed 미적용 | mock_payment.py 기동 시 "카드 N개 로드" 로그가 3이면 구 시드 | `reset-db.ps1` 재실행 후 "18개" 로그 확인 |
