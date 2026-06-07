# Phase 1 결함 복구 — 캡쳐 가이드 & 라이브 증거

> 포폴용 시각자료(스샷) 캡쳐 방법 + 실제로 돌려서 뽑은 텍스트 증거.
> 자동 재현 증거는 통합테스트([KafkaResilienceIntegrationTest](../../src/test/java/com/wooriport/core_api/integration/KafkaResilienceIntegrationTest.java)),
> 이 문서는 **실서버에서 결함을 일으켜 화면으로 보이는** 방법.

## 캡쳐 surface 3곳
| 화면 | URL | 무엇이 보이나 |
|------|-----|--------------|
| **Kafka UI** | http://localhost:8081 | `transaction-events.salary.retry`(재시도 1·2·3) / `.DLT`(격리) 토픽 메시지 |
| **Kibana** | http://localhost:5601 | `event_type` 로 결함 trace 로그(`salary_dlt`, `salary_retry_again`, `kafka_duplicate_skipped`) |
| **Grafana** | http://localhost:3000 (admin/admin) | `tx.duplicate.detected`, `tx.salary.dlt`, `tx.challenge.recomputed` 카운터 패널 |

## chaos 켜고 백엔드 기동 (결함 주입 ON)
```powershell
$env:JAVA_HOME = "C:\Users\tkafk\.jdks\ms-21.0.11"
$env:CHAOS_SALARY_FAIL = "always"      # S2 DLT 데모.  S1 성공 데모는 "once"
$env:CHAOS_CHALLENGE_FAIL = "once"     # C1 재계산 데모
& C:\it\backend\gradlew.bat -p C:\it\backend bootRun --console=plain
```
데모 시드 적용(자동이체 급여 유저 + 진행중 챌린지):
```powershell
docker cp C:\it\backend\sql\demo_chaos_seed.sql wooriport-db:/tmp/s.sql
docker exec wooriport-db psql -U wooriport -d wooriport -f /tmp/s.sql
```
> ⚠️ 데모가 끝나면 **chaos 환경변수 없이 재기동**해야 정상 동작. (기본값 none → 결함 주입 off)

발행기: `mock-server/send_demo_event.py <asset_number> <amount> <category> [event_id]`
(venv: `C:\it\mock-server\venv\Scripts\python.exe`)

---

## 시나리오별 트리거 + 캡쳐 포인트 + 라이브 증거

### C1 — 챌린지 증분 실패 → DB 재계산 (CHAOS_CHALLENGE_FAIL=once)
```powershell
python send_demo_event.py 8888-0000-0000-6666 12500 식비
```
- **캡쳐**: Grafana `tx.challenge.failed`/`tx.challenge.recomputed` 카운터 / Kibana `[Challenge] DB 재계산 보정` 로그
- **라이브 증거**:
  - `tx_challenge_failed_total = 1`, `tx_challenge_recomputed_total = 1`
  - Redis `HGET challenge:<userId> currentValue` → **12500** (증분 0건이었지만 DB 재계산으로 정확 복구)

### D1 — 같은 event_id 2회 → 1건만 적재 (chaos 불필요)
```powershell
python send_demo_event.py 5429-4494-5284-1111 9000 식비 DUP-DEMO-001
python send_demo_event.py 5429-4494-5284-1111 9000 식비 DUP-DEMO-001
```
- **캡쳐**: Grafana `tx.duplicate.detected` / Kibana `event_type:kafka_duplicate_skipped`
- **라이브 증거**:
  - `SELECT COUNT(*) WHERE event_id='DUP-DEMO-001'` → **1**
  - `tx_duplicate_detected_total = 1`

### S2 — 급여 영구 실패 → 재시도 끝에 DLT 격리 (CHAOS_SALARY_FAIL=always)
```powershell
python send_demo_event.py 7777-0000-0000-5555 3000000 급여
```
- **캡쳐**: Kafka UI 에서 `transaction-events.salary.retry`(메시지 3건) + `.DLT`(1건) / Kibana `event_type:salary_dlt`
- **라이브 증거**:
  - 카운터: `failed=1 → retry=1 → retry_again=2(2·3차) → dlt=1`
  - `transaction-events.salary.retry` 토픽 (attempt 진행이 그대로 보임):
    ```
    {"tx":{...,"category":"급여","rawAmount":3000000,"isIncome":true},"attempt":1}
    {"tx":{...},"attempt":2}
    {"tx":{...},"attempt":3}
    ```
  - `transaction-events.salary.DLT` 토픽 (유실 아님, 격리됨):
    ```
    {"tx":{...,"category":"급여",...},"attempt":3}
    ```
  - Kibana `salary_dlt` 로그: `fail_reason="급여 재처리 3회 실패 → DLT 격리(유실 아님)", attempt=3, error="[chaos] salary fail always"`

### S1 — 급여 1회 실패 → 재처리로 성공 (CHAOS_SALARY_FAIL=once)
> 성공 경로라 포트폴리오/급여거래 시드가 추가로 필요(통합테스트 `salaryFailsOnce_retriedAndSucceeds` 가 자동 검증).
> 라이브로 보려면 chaos=once 로 기동 후 위 급여 이벤트 발행 → `salary.retry` 에 1건, `.DLT` 없음, `tx.salary.retry.success=1`,
> `transfer_plans` 에 이체계획 생성. (Kafka UI: retry 토픽만, DLT 비어있음)

---

## 토픽/카운터 덤프 명령 (증거 재수집용)
```powershell
# 카운터
(Invoke-WebRequest -UseBasicParsing http://localhost:8080/actuator/prometheus).Content -split "`n" |
  Select-String '^tx_(duplicate_detected|salary_failed|salary_retry|salary_dlt|challenge_recomputed)'
# 재처리/DLT 토픽 메시지
docker exec wooriport-kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 `
  --topic transaction-events.salary.retry --from-beginning --timeout-ms 6000
docker exec wooriport-kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 `
  --topic transaction-events.salary.DLT --from-beginning --timeout-ms 6000
# Kibana 대신 ES 직접 조회
Invoke-RestMethod "http://localhost:9200/service-logs-*/_count?q=event_type:salary_dlt"
```

> 측정일 2026-06-07 기준 위 값들은 실제로 재현되어 캡쳐됨.
