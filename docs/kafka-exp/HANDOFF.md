# Kafka 실험 핸드오프 (Phase 0~1 완료 → Phase 2 인계)

> 한 장으로 보는 인계 문서. 상세는 같은 폴더의 PLAN/phase-0/phase-1/capture-guide 참고.
> **Phase 2 담당자는 §4(하드웨어) → §5(재현) 를 먼저 읽으세요.**

---

## 1. 한눈에 보기

- **대상**: `transaction-events` 카프카 파이프라인 (적재 + 챌린지 + 급여). `service-logs` 로그 파이프라인은 건드리지 않음.
- **목표**: at-least-once 의 중복·유실 문제를 단계적으로 해결하며 **정합성 ↔ 성능 트레이드오프를 수치로 증명** (포폴).
- **로드맵**: `Phase0 baseline → Phase1 정합성(멱등/재처리/재계산) → Phase2 배치로 성능회복 → Phase3 카프카 튜닝`
  - ✅ Phase 0 완료, ✅ Phase 1 완료, ⬜ **Phase 2 (다음)**, ⬜ Phase 3

### 저장소·브랜치·태그 맵 (3개 저장소, 각자 별도 git)
| 저장소 | 경로 | Phase 1 브랜치 | 태그 |
|--------|------|----------------|------|
| backend | `c:\it\backend` | `exp/kafka-1-resilience` | `kafka-exp-v0`(baseline), `kafka-exp-v1`(정합성) |
| mock-server | `c:\it\mock-server` | `exp/kafka-baseline` | `kafka-exp-v0` |
| infra | `c:\it\infra` | `exp/kafka-baseline` | `kafka-exp-v0` |

> 측정 하네스(producer 버스트모드·Grafana 대시보드)는 Phase 0 에서 동결 → mock-server/infra 는 v0 그대로,
> 단계별 로직 변화는 backend 에만. **Phase 2 는 backend 에서 `exp/kafka-2-batch` 를 `exp/kafka-1-resilience` 에서 분기**.

---

## 2. 결과 요약 (측정 머신: AMD Ryzen 5 5600X 6C/12T, RAM 16GB, Win11, 단일 컨슈머)

| 지표 | v0 baseline | v1 resilience | 변화 |
|------|-------------|---------------|------|
| consumer 처리량(정상상태) | ~301 msg/s | ~248 msg/s | **−18%** |
| tx.persist p50 | 2.49 ms | 3.15 ms | +0.66 ms |
| producer 발행 | 100k / 1.48s | 100k / 1.55s | ~동일(병목 아님) |
| 중복 적재 | 발생(약점) | **0** (멱등) | ✅ |
| 급여 유실 | 가능(약점) | **0** (재처리+DLT) | ✅ |
| 챌린지 오차 | 가능(약점) | 자가복구(재계산) | ✅ |

> **핵심 서사**: producer 빠름(67k/s) ↔ **consumer 가 병목**(단일 스레드·메시지당 DB 왕복).
> Phase 1 은 멱등 적재(`existsByEventId` SELECT + `saveAndFlush`)로 정합성을 얻고 처리량 −18% 를 내줌 = **의도된 트레이드오프**.
> Phase 2 의 목표 = 배치 적재로 이 −18% 회복(+초과).
> ⚠️ **숫자(301/248)는 이 머신 전용**. 하드웨어 바뀌면 §4 참고.

---

## 3. Phase 1 구현 (backend, `exp/kafka-1-resilience`)

| 파트 | 무엇 | 파일 |
|------|------|------|
| ① 멱등 적재 | `transactions.event_id`(unique) + `existsByEventId` + `saveAndFlush`→`DuplicateEventException` 후 컨슈머가 카운트·스킵 | `TransactionService`, `Transactions`, `TransactionConsumer` |
| ② 급여 재처리 | 실패→`transaction-events.salary.retry`→`SalaryRetryConsumer` 재실행, 3회 초과→`.DLT` | `SalaryRetryConsumer`, `SalaryRetryMessage` |
| ③ 챌린지 재계산 | 증분 실패→`recomputeFromDb`(DB 매칭합 재계산→Redis/DB 보정, 멱등) | `ChallengeService` |
| ③' DLT 관리 | DLT→`failed_salary_actions` 적재 + 관리자 조회/redrive(attempt 0 리셋) | `SalaryDltConsumer`, `AdminSalaryDltService`, `AdminSalaryDltController` |
| 결함주입 | `FaultInjector`(chaos.salary-fail/challenge-fail), 기본 off | `FaultInjector` |

설계 노트: `generateFromSalary` 는 미확인 플랜 delete+재생성이라 **재실행해도 멱등**(이체계획 중복 없음).

**검증**: 단위테스트 + 통합테스트(`KafkaResilienceIntegrationTest`, EmbeddedKafka+Testcontainers) — D1/C1/S1/S2/S3 전부 green.
- D1 중복적재→1건 / C1 챌린지 재계산 / S1 급여 1회실패→재처리 성공 / S2 영구실패→DLT / S3 redrive→RESOLVED

**DLT 다음 처리(미완·논의중)**: 관리자 대시보드가 Grafana/Kibana(보기전용)뿐이라 redrive 트리거는 프론트 버튼이 아니라
**Swagger UI / 스크립트 + Grafana 알림** 방향으로 갈 예정(REST·DB감사는 구현됨). → capture-guide.md 참고.

---

## 4. ⚠️ 하드웨어가 바뀌면 (Phase 2 가기 전 필수)

처리량·지연 **절대값은 머신마다 다름**. 다른 컴퓨터에서 Phase 2 를 측정하면 v0/v1 수치와 직접 비교 불가.

**규칙: 새 머신에서 Phase 2 를 하려면, 같은 머신에서 v0·v1 을 먼저 재측정해 새 기준선을 만든다.**
비교는 **절대값이 아니라 같은 머신 안의 상대 델타**로 한다. (예: "이 서버에서 v0 = X, v1 = 0.82X, v2 = ...")

재측정 절차:
```
1) git checkout kafka-exp-v0  → §5 대로 100k 측정 → phase-0.md 의 표를 "<머신명>" 행으로 추가
2) git checkout kafka-exp-v1  → 동일 측정 → phase-1.md 표에 추가
3) 이후 Phase 2 측정과 비교
```
통제 변수(§9-3): 동일 머신·동일 N(100k)·동일 시드·워밍업 5k 폐기·하네스 동결·다른 부하 끄기.

---

## 5. 처음부터 재현 (셋업 → 실행) — Windows/PowerShell 기준

### 5-0. 사전 준비 (한 번)
1. **JDK 21** 설치 후 `JAVA_HOME` 설정 (현재 머신은 `C:\Users\tkafk\.jdks\ms-21.0.11` — 머신마다 다름).
   각 gradlew 호출 전에 `$env:JAVA_HOME="<JDK21 경로>"`.
2. **Docker Desktop** 실행.
3. **Python 3.11+** + mock-server venv:
   ```powershell
   cd c:\it\mock-server
   python -m venv venv
   .\venv\Scripts\python.exe -m pip install -r requirements.txt   # confluent-kafka, psycopg2-binary 등
   ```
4. **`backend/src/main/resources/application-secret.yml`** 생성 (⚠️ gitignore라 git 에 없음 — 별도 공유 필요).
   템플릿:
   ```yaml
   spring:
     datasource:
       username: wooriport
       password: wooriport1234
   jwt:
     secret-key: <base64 32바이트 이상 임의문자열>
     access-expiration-time: 3600000
   ```

### 5-1. 인프라 기동
```powershell
cd c:\it\infra
docker compose up -d          # postgres, redis, kafka, kafka-ui(8081), prometheus(9090), grafana(3000), ELK
docker ps                     # wooriport-redis 포함 전부 Up 확인 (redis 빠지면 챌린지 경로 에러)
```

### 5-2. 백엔드 기동 (측정할 단계로 checkout)
```powershell
cd c:\it\backend
git checkout kafka-exp-v0      # Phase 0 측정 시.  Phase 1 은 kafka-exp-v1
$env:JAVA_HOME = "<JDK21 경로>"
.\gradlew.bat bootRun --console=plain        # :8080, health: http://localhost:8080/actuator/health
```
> 첫 기동 시 ddl-auto=update 가 스키마 생성(v1 은 transactions.event_id, failed_salary_actions 등 추가).

### 5-3. 트랙 A 성능 측정 (chaos OFF)
```powershell
# DB 리셋 + 시드 (UTF-8 안전: docker cp + psql -f)
powershell -File c:\it\backend\scripts\kafka-exp\reset-db.ps1
# 워밍업(폐기)
c:\it\mock-server\venv\Scripts\python.exe c:\it\mock-server\mock_payment.py --count 5000
powershell -File c:\it\backend\scripts\kafka-exp\reset-db.ps1
# 본런 100k
c:\it\mock-server\venv\Scripts\python.exe c:\it\mock-server\mock_payment.py --count 100000
# 정상상태 처리량 = 60초간 transactions 증가량 / 60
#   (docker exec wooriport-db psql -U wooriport -d wooriport -t -c "SELECT COUNT(*) FROM transactions;")
# 지연/카운터:
(Invoke-WebRequest -UseBasicParsing http://localhost:8080/actuator/prometheus).Content -split "`n" | Select-String '^tx_'
# Grafana 대시보드 "Kafka 실험 — transaction-events" (http://localhost:3000, admin/admin)
```

### 5-4. 테스트 (정합성 자동 증거)
```powershell
$env:JAVA_HOME = "<JDK21 경로>"
.\gradlew.bat test --console=plain         # 단위 + 통합(KafkaResilienceIntegrationTest) 전부
```

### 5-5. 결함 복구 라이브 데모 (트랙 B, chaos ON) — capture-guide.md 참고
```powershell
$env:CHAOS_SALARY_FAIL="always"; $env:CHAOS_CHALLENGE_FAIL="once"
.\gradlew.bat bootRun --console=plain
# 데모 시드 + 이벤트 발행은 capture-guide.md 의 send_demo_event.py 사용
# 끝나면 chaos 환경변수 없이 재기동(정상화)
```

---

## 6. 문서 인덱스 (docs/kafka-exp/)
| 파일 | 내용 |
|------|------|
| PLAN.md | 전체 실험 계획(0~3), 설계 결정 |
| phase-0.md | baseline 측정 + 병목 분석 |
| phase-1.md | 정합성 로직 + 성능 트레이드오프 측정 |
| capture-guide.md | 결함 복구 스샷 캡쳐법 + 라이브 증거 |
| **HANDOFF.md** | (이 문서) 인계 요약 + 재현 |

---

## 7. Phase 2 (다음 담당자가 할 일)

**목표**: Phase 1 에서 −18% 떨어진 처리량을 **배치 적재**로 회복(+초과). 정합성은 유지.

시작:
```powershell
cd c:\it\backend
git checkout exp/kafka-1-resilience
git checkout -b exp/kafka-2-batch
```
방향(PLAN.md §Phase 2):
- `@KafkaListener(batch=true)` + `max.poll.records` 조정 → poll 당 여러 건 한 번에 수신
- 배치 dedup 1쿼리(`existsByEventId` 반복 SELECT → `findAllByEventIdIn` 한 번) + `saveAll`/JDBC batch insert
- poll 단위 트랜잭션 재설계
- 멱등 유지(중복 0) 확인 + 처리량 재측정 → phase-2.md, 태그 `kafka-exp-v2`

검증: 기존 `KafkaResilienceIntegrationTest` 가 계속 green 이어야 함(정합성 회귀 방지).

---

## 8. 알려진 함정 (셋업 중 실제로 겪은 것들)
- **JAVA_HOME 미설정** → gradlew 가 "JAVA_HOME is not set". 매 호출 전 set.
- **redis 미기동** → 챌린지 경로 RedisConnectionFailure. `docker compose up -d` 로 전부 띄울 것.
- **시드 스키마 불일치**: 구 `dummy_kafka_test.sql` 은 사라진 컬럼(phone 등) 참조로 깨짐 → `seed_kafka_test.sql` 사용(reset-db.ps1 이 사용).
- **PowerShell→docker 파이프 한글 깨짐** → `docker cp` 후 `psql -f` (reset-db.ps1 방식).
- **Git Bash 가 `/tmp/` 를 Windows 경로로 변환**(MSYS) → docker 명령은 PowerShell 로.
- **application.yml YAML 들여쓰기**: 최상위 블록을 `spring:` 하위 사이에 끼우면 자식으로 빨려듦(batch.job.enabled 무효화 사례). 통합테스트가 검출.
- **cp949 콘솔**에서 파이썬 이모지/한글 print 죽음 → mock_payment.py 가 stdout UTF-8 고정.
- **JPA 수동 id + save()** → merge 로 "unsaved-value" 에러. 테스트에서 챌린지 id 수동지정 금지(@GeneratedValue 사용).
- **application-secret.yml 은 gitignore** → 팀원에게 별도 공유 필요(§5-0).

---

## 9. 서버컴 셋업 체크리스트 (Windows, all-in-one 실험 장비)

> 결정: **서버 OS Windows, infra+backend+producer 전부 서버 localhost 에서, 서버에서 v0·v1 부터 재측정.**
> → 네트워크 변수 제거 + 모든 단계 동일 머신 = 공정한 비교.

### 9-0. ⚠️ 먼저: 실험 브랜치/태그를 remote 에 push (현재 로컬 전용)
`exp/*` 브랜치와 `kafka-exp-v0/v1` 태그는 아직 push 안 됨. 3개 저장소 각각:
```powershell
# backend
git -C c:\it\backend push origin exp/kafka-0-baseline exp/kafka-1-resilience
git -C c:\it\backend push origin kafka-exp-v0 kafka-exp-v1
# mock-server
git -C c:\it\mock-server push origin exp/kafka-baseline
git -C c:\it\mock-server push origin kafka-exp-v0
# infra
git -C c:\it\infra push origin exp/kafka-baseline
git -C c:\it\infra push origin kafka-exp-v0
```
> 공용 팀 레포라 push 전 팀과 합의. (단계별 태그가 있어야 서버에서 `git checkout kafka-exp-v0` 로 재현 가능)

### 9-1. 서버에서 준비
1. JDK 21 설치 → `JAVA_HOME` 설정 (서버 경로로). Docker Desktop, Python 3.11+ 설치.
2. 3개 저장소 clone, 각 브랜치 checkout (backend=exp/kafka-1-resilience, mock-server·infra=exp/kafka-baseline).
3. mock-server venv 생성 + `pip install -r requirements.txt` (§5-0).
4. `backend/src/main/resources/application-secret.yml` 생성(§5-0 템플릿) — git 에 없으니 직접.
5. **서버 사양을 phase-0.md / phase-1.md 측정환경에 새 행으로 기록** (CPU/코어/RAM/OS).

### 9-2. 서버에서 재측정 (Phase 2 전 필수)
```powershell
cd c:\it\infra; docker compose up -d
cd c:\it\backend
# v0 재측정
git checkout kafka-exp-v0
# (bootRun → §5-3 트랙A 100k 측정) → phase-0.md 표에 "<서버명>" 행 추가
# v1 재측정
git checkout kafka-exp-v1
# (bootRun → §5-3 동일) → phase-1.md 표에 "<서버명>" 행 추가
```
→ 이제 **서버 기준 v0/v1** 이 생김. Phase 2(`exp/kafka-2-batch`) 측정은 이 값과 비교(§4 상대 델타 규칙).

### 9-3. 운영 수칙(통제변수)
- 측정 중 서버에서 다른 무거운 작업 금지.
- producer 도 서버 localhost 에서 실행(노트북에서 쏘지 않기).
- 동일 N(100k)·동일 시드·워밍업 5k 폐기 유지.
