# Kafka 실험 측정 런북

> 모든 단계(Phase 0~3) 공통 측정 절차. 단계 간 비교의 공정성을 위해 **이 절차와 하네스는 동결**.
> 상세 설계는 `PLAN.md` 참고.

## 통제 변수 (매 런 동일하게 유지)
- 동일 머신 / 동일 하드웨어 (사양 기록: CPU, RAM, 디스크)
- 동일 메시지 수 N, 동일 시드 RNG → 모든 단계 동일 부하
- 워밍업 1회(기본 5k) 실행 후 폐기
- 측정 하네스(부하생성·메트릭·대시보드) 코드 동결
- 다른 무거운 프로세스 종료

## 기본 파라미터
| 항목 | 기본값 |
|------|--------|
| 부하 건수 N | 100,000 |
| rate | 0 (sleep 없이 최대속도) |
| 워밍업 | 5,000 (폐기) |

---

## 트랙 A — 성능 측정 (chaos OFF)
단계 간 처리량/지연 비교용.

```
1. docker compose up -d              # infra/ 에서 (kafka, postgres, redis, prometheus, grafana, kafka-ui)
2. DB 리셋 + 시드 적용               # truncate + sql/dummy_kafka_test.sql
3. 백엔드 기동 (chaos 프로파일 OFF)
4. 워밍업: mock_payment --count 5000 --rate 0   → 폐기
5. DB 리셋 + 시드 다시 적용
6. 본런: mock_payment --count 100000 --rate 0   → 시작/종료 시각 기록
7. consumer lag 0 도달까지 drain      # Kafka UI 또는 kafka-consumer-groups --describe
8. 산출:
   - 처리량 = N / (drain wall-clock)  [msg/s]
   - E2E 지연 p50/p95/p99             [Prometheus: tx.e2e.latency]
   - lag drain 곡선                   [Grafana 스샷]
9. phase-N.md 표에 기록 + Grafana 스샷
```

### 측정 소스
- 처리량/지연/lag: Grafana 대시보드 (Prometheus 스크랩)
- Prometheus 직접 쿼리: `http://localhost:9090`
  - `rate(tx_consumed_total[1m])` — 처리량
  - `tx_e2e_latency_seconds{quantile="0.95"}` — 지연
- 컨슈머 lag: Kafka UI(8081) 또는
  `kafka-consumer-groups --bootstrap-server localhost:9092 --describe --group approval-detect-group`

---

## 트랙 B — 정합성 / 결함 복구 증명 (chaos ON, Phase 1+)
성능과 별개 목적. 소량 주입으로 복구 동작을 증명.

```
1. 백엔드를 chaos 프로파일로 기동
2. 시나리오별 환경변수 설정 (PLAN.md F-2 매트릭스)
3. 해당 시나리오 부하 주입 (소량)
4. 기대 동작 검증:
   - DB count 쿼리 (중복 행 수, 이체계획 수)
   - Redis 값 before/after
   - Kafka UI: retry / DLT 토픽 메시지
   - Kibana: 로그 trace (event_type 필터로 실패→재시도→성공 흐름)
5. 통합테스트로 불변식 자동 검증 (TransactionFlowIntegrationTest 확장)
6. phase-1.md "시나리오 → 기대결과 → 증거" 표에 테스트명 + 스샷 기록
```

### 정합성 검증 쿼리 (예시 — Phase 1에서 구체화)
```sql
-- 중복 적재 확인 (Phase 1 이후 0이어야)
SELECT event_id, COUNT(*) FROM transactions GROUP BY event_id HAVING COUNT(*) > 1;

-- 급여 이체계획 중복 확인 (event_id당 1건이어야)
-- (transfer_plan 스키마 확인 후 작성)
```

---

## 기록 템플릿 (phase-N.md)
```
## Phase N — <이름>
- 브랜치: exp/kafka-N-...   태그: kafka-exp-vN
- 머신: <CPU/RAM>
- 파라미터: N=100k, rate=0, 워밍업 5k

### 성능 (트랙 A)
| 지표 | 값 |
|------|----|
| 처리량 (msg/s) | |
| E2E p50/p95/p99 (ms) | |
| drain 시간 (s) | |
[Grafana 스샷]

### 정합성 (트랙 B, Phase 1+)
| 시나리오 | 기대 | 결과 | 증거 |
|----------|------|------|------|
[Kafka UI / Kibana 스샷]

### 변경 요약 & 관찰
- (이전 단계 대비 무엇이 바뀌었고 왜 이런 수치가 나왔는지)
```
