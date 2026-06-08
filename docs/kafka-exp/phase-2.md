# Phase 2 — Batch (배치 적재 + poll 단위 트랜잭션)

- 브랜치: `exp/kafka-2-batch` / 태그: `kafka-exp-v2`
- 목적: Phase 1 에서 멱등 적재가 도입한 -18% 처리량 하락을
  `@KafkaListener(batch=true)` + `saveAll` + 배치 dedup 1쿼리로 회복.
  정합성(멱등·재처리·재계산)은 유지.

---

## 변경 요약

| 레이어 | Phase 1 | Phase 2 | 효과 |
|--------|---------|---------|------|
| 컨슈머 | `consume(String)` 단건 | `consume(List<String>)` + `batchKafkaListenerContainerFactory` | poll 단위 배치 수신 |
| dedup | `existsByEventId` 메시지당 1 SELECT | intra-batch dedup(Set) + `findExistingEventIds(IN)` 1쿼리 | SELECT N→2 |
| asset 조회 | `findByAssetNumber` 메시지당 1 SELECT | `findByAssetNumberIn(IN)` 1쿼리 | SELECT N→1 |
| INSERT | `saveAndFlush` 메시지당 1건 + flush | `saveAll` Hibernate `jdbc.batch_size=100` | INSERT N개→배치 |
| 트랜잭션 | 메시지당 개별 TX | poll 전체 단일 `@Transactional` | 커밋 N→1 |
| JDBC | — | `reWriteBatchedInserts=true` (멀티로우 INSERT 재작성) | 라운드트립 감소 |
| max.poll.records | 기본값 | 명시 500 | poll 크기 고정 |

### DB 라운드트립 비교 (poll 크기 P 기준)

| 작업 | Phase 1 | Phase 2 |
|------|---------|---------|
| dedup SELECT | P × 1 | 1 (IN 쿼리) |
| asset SELECT | P × 1 | 1 (IN 쿼리) |
| INSERT + flush | P × 1 | 1 (배치) |
| 트랜잭션 커밋 | P × 1 | 1 |
| **합계** | **4P** | **4** |

P = 500 기준: 2,000 ops → 4 ops

---

## 핵심 파일 변경

| 파일 | 변경 |
|------|------|
| `service/TransactionConsumer.java` | `consume(List<String>)` + `containerFactory = "batchKafkaListenerContainerFactory"` |
| `service/TransactionService.java` | `batchPersist()` 추가 — intra-batch dedup → DB dedup → batch asset 조회 → saveAll |
| `repository/TransactionRepository.java` | `findExistingEventIds(Collection)` 추가 |
| `repository/AssetRepository.java` | `findByAssetNumberIn(Collection)` 추가 |
| `config/KafkaBatchConfig.java` | `batchKafkaListenerContainerFactory` 빈 (setBatchListener=true) |
| `dto/transaction/BatchPersistResult.java` | `persisted + duplicates` 결과 레코드 |
| `application.yml` | `max-poll-records: 500`, `jdbc.batch_size: 100`, `order_inserts: true`, `reWriteBatchedInserts=true` |

---

## 정합성 설계

### dedup 2-레이어

```
poll 수신 (최대 500건)
  └─ ① intra-batch dedup (LinkedHashMap — 동일 poll 내 중복: 첫 번째만 통과)
       └─ ② DB dedup (findExistingEventIds IN 쿼리 — 이전 poll 에서 이미 적재된 것)
            └─ saveAll (Hibernate batch INSERT)
                └─ 실패 시: TX 롤백 → Kafka 오프셋 미커밋
                             → 재시도 → ①② 에서 필터 → 멱등 보장
```

**intra-batch dedup 필요 이유**: 동일 poll 안에 같은 event_id 두 개가 오면
② 단계에서 둘 다 "신규"로 보여 saveAll 에서 unique 충돌 → TX 롤백 → 불필요한 재시도 발생.
intra-batch dedup 이 이를 poll 내에서 즉시 차단한다.

### 챌린지 / 급여 후속처리

Phase 1 그대로 per-item best-effort. `batchPersist` 커밋 이후 순차 실행.
실패 시 복구 경로 동일: 챌린지는 DB 재계산(2-2), 급여는 재처리 토픽(1-1).

---

## 트랙 A — 성능 측정 (측정일 2026-06-08)

환경: Ryzen 5 5600X (6C/12T, 16GB), N=100k, rate=0, max.poll.records=500, 단일 컨슈머.
방법: `reset-db.ps1` → `mock_payment.py --count 100000 --rate 0` → actuator `tx_consumed_total`
3초 간격 샘플링 → 정상상태 60s 구간 기울기. **v0/v1 과 동일 하드웨어·동일 시드(클린, 활성 챌린지 0)**.

| 지표 | Baseline (v0) | Phase 1 (v1) | **Phase 2 (v2)** | v1→v2 변화 |
|------|--------------|-------------|-------------|-----------|
| consumer 처리량 (msg/s, 60s 정상상태) | ~301 | ~248 | **~1,305** | **+426% (5.3×)** |
| consumer 처리량 (msg/s, end-to-end 100k) | — | — | **1,261** (100k/79.3s) | — |
| tx.persist p50 (메시지당, ms) | 2.49 | 3.15 | **0.121** | **−96%** |
| tx.persist (poll당 배치, ms) p50/p95 | — | — | 56.6 / 86.0 | — |
| tx.batch.size (poll당 처리건수) p50/avg/max | — | — | 496 / 497 / 500 | — |
| 정합성 (적재/유실/중복) | — | — | 100,000 / 0 / 0 | — |

> **persist 메시지당**: v2 의 batchPersist 총 소요 12.08s ÷ 100k = 0.121ms/건.
> v1 의 단건 persist 3.15ms/건 대비 **약 26× 단축** (IN dedup 1쿼리 + saveAll 배치 INSERT + reWriteBatchedInserts).
> poll 당 배치는 ~497건을 56.6ms(p50)에 처리.

### 결과 해석 — Phase 1 회귀 회복 + 병목 이동

- **−18% 회귀 회복 완료**: v1(248) → v2(~1,305)로 Phase 1 멱등 도입이 만든 −18% 하락을
  회복하고도 v0(301) 대비 **약 4.3×** 초과. 정합성(멱등·재처리·재계산)은 트랙 B 그대로 유지.
- **병목이 DB 적재 → 컨슈머 루프(파싱)로 이동**: 측정 가능한 작업은 persist 12.08s + 급여 후속처리 0.30s
  = **~12.4s** 인데 실제 드레인은 79.3s. 차이 ~67s 는 **메시지당 JSON 역직렬화(10만 건) + Kafka poll/fetch
  오버헤드**가 차지(단일 스레드 = concurrency 1). persist 는 더 이상 병목이 아님.
  → **Phase 3 1순위 타깃**: `concurrency` ↑ (파티션 3개 → 컨슈머 스레드 3개)로 파싱·적재 병렬화.

### ⚠️ 후속처리(챌린지) 부하 민감도 — 측정 시 반드시 통제

처리량은 **활성 챌린지를 가진 유저로 가는 트래픽 비율에 매우 민감**하다.

| 조건 | 60s 정상상태 | 비고 |
|------|------------|------|
| 활성 챌린지 0 (v0/v1 동일 클린 시드) | **~1,305 msg/s** | 본 측정. `hasActiveChallenge`=false → 챌린지 경로 전면 스킵 |
| 활성 챌린지 1개가 트래픽 ~25% 수신 | ~1,118 msg/s | `demo_chaos_seed` 잔존 시 (오염). `updateProgress` 24,829회 실행 |

- `demo_chaos_seed.sql` 은 CREDIT_CARD 카드 + IN_PROGRESS 챌린지를 만들므로, **성능 측정 전 반드시
  Redis FLUSHALL + 데모 데이터 제거**(클린 카드 3장만) 후 측정해야 v0/v1 과 비교 가능.
- 팀원 커밋의 "~5,000 msg/s(N=10k)" 는 **다른 머신·N=10k·챌린지 0** 조건의 값으로 본 데스크탑 기준선과 직접 비교 불가.
  이 데스크탑(Ryzen 5 5600X)의 canonical 값은 위 표(클린 ~1,305)다.

---

## 트랙 B — 정합성 (Phase 1 통합테스트 재사용)

Phase 2 는 dedup 로직을 `batchPersist` 내부로 이동했으므로
Phase 1 의 시나리오 테스트가 그대로 통과해야 한다.

| ID | 시나리오 | 테스트 | Phase 2 처리 |
|----|---------|--------|------------|
| D1 | 같은 event_id 두 번 | `duplicateEventId_persistsOnce` | intra-batch 또는 DB dedup → 행 1건 |
| C1 | 챌린지 증분 실패 | `challengeIncrementFails_recomputedFromDb` | 후속처리 경로 무변경 |
| S1 | 급여 1회 실패 | `salaryFailsOnce_retriedAndSucceeds` | 후속처리 경로 무변경 |
| S2 | 급여 영구 실패 | `salaryFailsAlways_endsInDlt` | 후속처리 경로 무변경 |

```bash
gradlew test --tests "*KafkaResilienceIntegrationTest"
```

---

## Phase 2 근거 — Phase 1 병목 분석

Phase 1 `persist()` 단건 처리:
```
메시지 1건당:
  existsByEventId()  → SELECT 1     (1 round-trip)
  saveAndFlush()     → INSERT+flush (1 round-trip + 커밋 오버헤드)
  = 2 DB ops × N msg
```

Phase 2 `batchPersist()` 500건 배치:
```
배치 1회당:
  findExistingEventIds() → SELECT IN   (1 round-trip)
  findByAssetNumberIn()  → SELECT IN   (1 round-trip)
  saveAll(500건)         → 배치 INSERT  (reWriteBatchedInserts 적용)
  commit                 → 1 round-trip
  = ~4 round-trips / 500건
```

---

## 재현 방법

```powershell
# 0. DB 리셋 + 시드
.\sql\reset-db.ps1

# 1. 서버 기동 (exp/kafka-2-batch 브랜치)
./gradlew bootRun

# 2. 부하 생성 (100k 건, 최대속도)
python mock-server/mock_payment.py --count 100000 --rate 0

# 3. Grafana (localhost:3000) 60초 구간 측정
#    - consumer_processed_total rate → 처리량
#    - tx_persist_seconds p50/p95    → persist 지연
#    - tx_batch_size p50/p95         → poll 크기 분포
```

---

## 다음 단계 (Phase 3)

파티션 수 · `concurrency` · `fetch.min.bytes`/`fetch.max.wait.ms` ·
프로듀서 압축(`lz4`/`zstd`) · `acks` 스윕 → `docs/kafka-exp/phase-3.md`.
Phase 2 처리량이 Phase 3 의 기준선이 된다.
