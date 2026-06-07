# Kafka 실험 계획 (transaction-events 정합성 & 성능 튜닝)

> 목적: `transaction-events` 처리 파이프라인을 단계적으로 개선하며 **정합성 보장 ↔ 성능**의
> 트레이드오프를 수치/스샷으로 증명하는 포트폴리오용 실험.
> `service-logs`(로그 파이프라인)는 **건드리지 않음**.
>
> 이 문서는 다른 세션에서 이어받을 수 있도록 자체 완결적으로 작성됨. 코드 위치 포함.

---

## 0. 현재 구조 스냅샷 (실험의 "before")

### 브로커 / 인프라
- `infra/docker-compose.yml` — `apache/kafka:3.7.2`, KRaft 모드(Zookeeper 없음).
  - 리스너: `EXTERNAL localhost:9092`(로컬 Spring·스크립트), `INTERNAL kafka:29092`(도커 내부: Kafka UI, Logstash).
  - 기본 파티션 3, 복제계수 1(단일 노드).
- Kafka UI: http://localhost:8081
- 측정 백본 **이미 존재**: Actuator + `micrometer-registry-prometheus` + Prometheus(9090) + Grafana(3000).

### transaction-events 흐름
```
[Producer] mock-server/mock_payment.py  (Python, confluent_kafka)
   key = asset_number (같은 카드 → 같은 파티션, 순서 보장)
   value = {asset_number, amount, category, sender_name, transactionAt}
        │
        ▼
[Topic] transaction-events (파티션 3)
        │
        ▼
[Consumer] TransactionConsumer  (groupId: approval-detect-group, auto-offset-reset: earliest)
   1) TransactionService.persist()         — 적재, 자기 @Transactional 커밋 (핵심 사실)
   2) ChallengeService.updateProgress()    — Redis 증분, REQUIRES_NEW, best-effort
   3) SalaryService.handleIfSalary()       — 이체계획 생성(액션), best-effort
```

### 핵심 파일 위치
| 역할 | 경로 |
|------|------|
| 컨슈머 | `src/main/java/com/wooriport/core_api/service/TransactionConsumer.java` |
| 적재 | `src/main/java/com/wooriport/core_api/service/TransactionService.java` |
| 챌린지 | `src/main/java/com/wooriport/core_api/service/ChallengeService.java` |
| 급여 | `src/main/java/com/wooriport/core_api/service/SalaryService.java` |
| 이벤트 DTO | `src/main/java/com/wooriport/core_api/base/dto/transaction/TransactionEventDto.java` |
| 프로듀서(mock) | `../mock-server/mock_payment.py` |
| Spring kafka 설정 | `src/main/resources/application.yml` (23~29줄) |
| 통합테스트(EmbeddedKafka) | `src/test/java/com/wooriport/core_api/integration/TransactionFlowIntegrationTest.java` |
| 시드 SQL | `sql/dummy_kafka_test.sql` |
| Grafana provisioning | `../infra/grafana/provisioning/` |

### 현재 코드의 약점 (= 개선 스토리의 출발점)
| 단계 | 현재 | 약점 |
|------|------|------|
| 적재 | event_id 없음 | 중복 전달 시 **중복 행 적재** (at-least-once 미보장) |
| 급여 | best-effort | 실패 시 **유실** (이체계획 = 유실 불가 액션인데) |
| 챌린지 | Redis 증분 | 중복/실패 시 **카운트 틀어짐** (증분은 멱등 X) |

---

## 1. 설계 확정 사항 (결정 완료)

- **멱등성 키**: 이벤트에 `event_id`(UUID) 추가. 실제 카드결제도 PG/VAN이 거래고유번호(승인번호)를
  멱등키로 내려주므로 현실적. → `transactions`에 unique 제약 + `ON CONFLICT DO NOTHING`으로 dedup.
- **부하 생성**: `mock_payment.py`에 버스트 모드(`--count N`, `--rate R`) 추가. 기존 무한루프는 유지.
- **급여 실패 전략 (1-1)**: 실패 → 재처리 토픽 발행 → 전용 컨슈머가 **급여 스텝만** 재실행 (멱등).
- **챌린지 실패 전략 (2-2)**: 실패 → 증분 replay 금지, **DB 재조회 후 재계산** (멱등 by construction).
- **착수 순서**: Phase 0(baseline)부터. 단계별 선형 브랜치 + 태그.
- **기본 부하 파라미터**: N=100k, rate=0(최대속도), 워밍업 5k 폐기. (조정 가능)

---

## 2. Git 브랜치 전략

선형 체인 + 단계별 태그. 각 단계가 이전 위에 쌓여 "개선 서사"를 만들고, 태그로 재측정 가능.

```
main (제품 — 머지하지 않음)
 └─ exp/kafka-0-baseline   ─ tag kafka-exp-v0  (측정 하네스만, 로직 무변경)
     └─ exp/kafka-1-resilience ─ tag kafka-exp-v1 (멱등+재처리+재계산+결함주입)
         └─ exp/kafka-2-batch  ─ tag kafka-exp-v2 (배치+트랜잭션 재설계)
             └─ exp/kafka-3-tuning ─ tag kafka-exp-v3 (카프카 파라미터 튜닝)
```

규칙:
1. **측정 하네스(부하생성·메트릭·대시보드·런북)는 Phase 0에서 한 번 만들고 이후 단계로 동결** → 단계 간 비교 공정성(변수 통제).
2. 각 브랜치에 결과 문서 동반 커밋: `docs/kafka-exp/phase-N.md`.
3. 최종 비교표는 `docs/kafka-exp/RESULTS.md`.
4. 실험 브랜치는 main 머지 X. 승자(보통 v3)만 추후 `develop`에 정리 머지.
5. 네임스페이스 `exp/` (기존 컨벤션 feat/·refactor/·chore/ 와 분리).
6. git 저장소 루트는 `backend/` 임에 유의 (mock-server·infra는 같은 루트 아래 별도 폴더).

---

## 3. 공통 사전작업 (Phase 0에서 1회 구현, 이후 동결)

### A-1. 이벤트 스키마 변경
`mock_payment.py generate_transaction()` 페이로드에 추가:
```python
"event_id":   str(uuid.uuid4()),        # PG 거래고유번호 = 멱등키
"producedAt": int(time.time() * 1000),  # epoch ms, E2E 지연 측정용
```
`TransactionEventDto.java`에 `eventId`, `producedAt` 매핑 추가.

### A-2. 버스트 모드 부하 생성
`mock_payment.py` 인자: `--count N`(총 건수), `--rate R`(0이면 sleep 없이 최대속도).
N건 선생성 후 일괄 produce, 종료 시 `flush()` 1회. 기존 무한루프는 `--rate` 기본값으로 유지.

### A-3. 측정 하네스
- **E2E Timer**: 컨슈머 진입에서 `now - producedAt` → Micrometer `Timer("tx.e2e.latency")`.
  스텝별 Timer: `tx.persist`, `tx.challenge`, `tx.salary`.
- **카운터**: `tx.consumed`, `tx.duplicate.detected`, `tx.salary.failed`, `tx.challenge.failed`,
  `tx.salary.retry`, `tx.salary.dlt`.
- **Kafka client 메트릭 바인딩**: consumer lag을 Prometheus로 노출(Spring kafka micrometer + `KafkaClientMetrics`).
- **Grafana 대시보드**: 처리량(rate)·E2E p50/95/99·lag·에러율 패널. JSON을 `infra/grafana/provisioning/`에 커밋.
- **DB 리셋 + 고정 시드**: `sql/dummy_kafka_test.sql` 기반 truncate+seed 스크립트. 매 런 전 동일 자산/챌린지 셋.

### A-4. 측정 프로토콜 → `docs/kafka-exp/RUNBOOK.md` 참고

---

## 4. 단계별 정밀 설계

### Phase 0 — `exp/kafka-0-baseline` (tag kafka-exp-v0)
- **로직 무변경**. A-1~A-4만 적용.
- 약점 증명: 동일 event_id 재전송 → 중복 행 N개 적재 수치화. 컨슈머 예외 주입 → 급여 유실/챌린지 오차.
- 산출: baseline 처리량·지연·"정합성 결함 수".

### Phase 1 — `exp/kafka-1-resilience` (tag kafka-exp-v1)
**① 멱등 적재**: `transactions.event_id` 컬럼 + unique 제약. 적재 `INSERT ... ON CONFLICT (event_id) DO NOTHING` → 중복 0.

**② 급여 재처리 (1-1)**: `handleIfSalary` 실패 → `transaction-events.salary.retry` 발행 →
`SalaryRetryConsumer`가 급여 스텝만 재실행. N회 초과 → `transaction-events.salary.DLT`로 격리.
멱등: 동일 event_id 이체계획 중복 생성 방지(존재 검사). 구현은 Spring `@RetryableTopic` 또는 수동 발행 중 택1.

**③ 챌린지 재계산 (2-2)**: 실패 → 증분 replay 금지, `recomputeFromDb(userId)`로
transactions 재조회→재집계→Redis/DB 동기화. 멱등 by construction.

예상: **정합성 ↑(중복0·급여유실0), 처리량 ↓**.

#### Phase 1 결함 주입 & 복구 증명 트랙
**F-1. 주입 메커니즘** — `chaos` Spring 프로파일에서만 활성(운영 빌드 영향 0). 환경변수 토글:
```
CHAOS_SALARY_FAIL=once|always   # 급여 스텝 강제 예외
CHAOS_REDIS_DOWN=true           # 챌린지 Redis 호출 강제 실패
CHAOS_KILL_BEFORE_COMMIT=true   # 적재 커밋 후 offset commit 전 강제 종료
```
중복 전달은 producer가 동일 event_id 재전송으로 재현(주입 불필요).

**F-2. 결함 시나리오 매트릭스** (각 행 = 1 증거 세트):

| ID | 결함 주입 | 기대 동작 | 증거 |
|----|----------|----------|------|
| D1 중복적재 | 같은 event_id 2회 | 1행만, `tx.duplicate.detected`=1 | DB count + Grafana |
| D2 커밋전 종료 | 적재 후 kill→재기동 | 재처리되나 중복 0 | Kafka UI offset + DB count |
| S1 급여 1회 실패 | SALARY_FAIL=once | retry→재실행 성공, 이체계획 1건 | Kafka UI(retry) + Kibana trace |
| S2 급여 영구 실패 | SALARY_FAIL=always | N회 후 DLT 격리(유실 X) | Kafka UI(.DLT) + 로그 |
| S3 급여 재처리 중복 | retry 메시지 중복 | 이체계획 여전히 1건 | DB count |
| C1 챌린지 Redis 다운 | REDIS_DOWN=true | recomputeFromDb로 재집계, 정확 | before/after Redis + Kibana |
| C2 챌린지 중복 | 중복 전달 | 증분 2배 안 됨 | Redis 값 비교 |

**F-3. 이중 증명**:
1. **통합테스트** — `TransactionFlowIntegrationTest.java`(EmbeddedKafka) 패턴 확장. 시나리오별 불변식 단정
   (예: "급여 1회 실패 후 이체계획 정확히 1건"). 자동·반복 검증.
2. **라이브 캡쳐** — 같은 시나리오 실제 실행 → Kafka UI(retry/DLT)·Kibana(로그 trace)·Grafana(카운터) 스샷.

→ `docs/kafka-exp/phase-1.md`에 "결함 시나리오 → 기대결과 → 증거(테스트명+스샷)" 표로 정리.

### Phase 2 — `exp/kafka-2-batch` (tag kafka-exp-v2)
- `@KafkaListener(batch=true)` + `max.poll.records` 튜닝.
- 배치 dedup 1쿼리 + `saveAll`/JDBC batch insert + poll 단위 트랜잭션 재설계.
- 예상: **처리량 회복/초과, 정합성 유지**.

### Phase 3 — `exp/kafka-3-tuning` (tag kafka-exp-v3)
- 스윕: 파티션 수, consumer `concurrency`, `fetch.min.bytes`/`fetch.max.wait.ms`,
  producer `linger.ms`·`batch.size`·`compression.type`(lz4/zstd)·`enable.idempotence`, `acks`.
- 각 조합을 phase-3.md 표로 기록 → 최적값 선정.

---

## 5. 산출물 구조
```
docs/kafka-exp/
 ├─ PLAN.md        (이 문서)
 ├─ RUNBOOK.md     (측정 절차)
 ├─ phase-0.md ~ phase-3.md   (단계별 설정 + 원시수치 + 스샷)
 └─ RESULTS.md     (최종 비교표: 처리량/지연/정합성/튜닝값)
```

**스토리라인**: at-least-once의 중복·유실 문제 → 멱등+재처리로 정합성 확보(성능 하락)
→ 배치/트랜잭션 재설계 + 카프카 튜닝으로 성능 회복·초과.
핵심 포인트 = 트레이드오프를 수치로 증명 + 장애 복구를 스샷/테스트로 증명.

---

## 6. 다음 작업 체크리스트 (이어받는 세션용)

- [ ] `git checkout -b exp/kafka-0-baseline` (backend 저장소에서)
- [ ] A-1 스키마: mock_payment.py + TransactionEventDto.java 에 event_id/producedAt
- [ ] A-2 버스트 모드: mock_payment.py --count/--rate
- [ ] A-3 하네스: Micrometer Timer/Counter + Kafka 메트릭 바인딩 + Grafana 대시보드 JSON
- [ ] A-4 DB 리셋+시드 스크립트
- [ ] RUNBOOK대로 baseline 측정 → docs/kafka-exp/phase-0.md 기록
- [ ] tag kafka-exp-v0
- [ ] → Phase 1 착수

> 미정/조정 가능: 부하 규모 N(기본 100k), 테스트 머신 사양(로컬 도커 기준),
> F-2에 추가 시나리오(급여+챌린지 동시 실패, 부분 배치 실패 등) 필요 여부.
