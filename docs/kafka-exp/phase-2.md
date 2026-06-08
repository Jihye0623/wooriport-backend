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
3초 간격 샘플링 → 정상상태 60s 구간 기울기. **v0/v1 과 동일 하드웨어·동일 방법**(직접 비교 가능).

| 지표 | Baseline (v0) | Phase 1 (v1) | **Phase 2 (v2)** | v1→v2 변화 |
|------|--------------|-------------|-------------|-----------|
| consumer 처리량 (msg/s, 60s 정상상태) | ~301 | ~248 | **~1,118** | **+351% (4.5×)** |
| consumer 처리량 (msg/s, end-to-end 100k) | — | — | **995** | — |
| tx.persist p50 (메시지당, ms) | 2.49 | 3.15 | **0.094** | **−97%** |
| tx.persist (poll당 배치, ms) p50/p95 | — | — | 43.5 / 75.0 | — |
| tx.batch.size (poll당 처리건수) p50/avg/max | — | — | 500 / 485 / 500 | — |
| 정합성 (적재/유실/중복) | — | — | 100,000 / 0 / 0 | — |

> **persist 메시지당**: v2 의 batchPersist 총 소요 9.37s ÷ 100k = 0.094ms/건.
> v1 의 단건 persist 3.15ms/건 대비 **약 33× 단축** (IN dedup 1쿼리 + saveAll 배치 INSERT + reWriteBatchedInserts).
> poll 당 배치는 ~485건을 43.5ms(p50)에 처리.

### 결과 해석 — Phase 1 회귀 회복 + 병목 이동

- **-18% 회귀 회복 완료**: v1(248) → v2(~1,118)로 Phase 1 멱등 도입이 만든 −18% 처리량 하락을
  회복하고도 v0(301) 대비 **약 3.7×** 초과 달성. 정합성(멱등·재처리·재계산)은 트랙 B 그대로 유지.
- **병목이 DB 적재 → per-item 후속처리로 이동**: persist 는 배치로 사실상 제거(100k 전체 9.37s).
  남은 시간은 메시지당 급여 후속처리(`tx_salary` 10만 회) + 챌린지(`tx_challenge` 24,829 회)가 차지.
  후속처리는 userId 그룹 병렬이지만 카프카테스트 유저가 3명이라 **실질 3-way 병렬**이 상한.
  → **Phase 3 1순위 타깃**: 후속처리 병렬도(파티션·concurrency↑) 또는 급여/챌린지 자체 배치화.
- **꼬리 구간 감속**(t≈67 이후 ~1,100→~780 msg/s): transactions 0→100k 적재되며
  dedup IN 쿼리·급여조회 비용 증가. end-to-end(995)가 정상상태(1,118)보다 낮은 이유.

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
