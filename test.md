# 전체 플로우 테스트 가이드

`signup → linking → salary-select → porti-survey → asset-prescription → prescription-loading/complete → asset-portfolio → dashboard`
까지 한 번에 돌려보기 위한 시드 + mock 서버 사용법입니다.

테스트 SQL: [sql/dummy_full_flow_test.sql](sql/dummy_full_flow_test.sql)
mock FastAPI: [../mock-server/mock_asset_portfolio.py](../mock-server/mock_asset_portfolio.py)

---

## 0. 사전 준비

1. 인프라 컨테이너 (PostgreSQL, Kafka 등) 실행 — `infra/` 폴더 참고
2. 백엔드(Spring Boot) 실행 → 테이블이 생성된 상태여야 함 (`ddl-auto=create` 또는 `update`)
3. 프론트 실행: `cd frontend && npm run dev`
4. mock 서버 실행 (아래 2번 참고)

---

## 1. 테스트 SQL — `sql/dummy_full_flow_test.sql`

### 한 줄 요약
**같은 SQL을 두 번 실행**합니다. 1차는 회원가입 직후, 2차는 자산 연동 + 급여통장 지정 직후.
같은 파일을 두 번 돌려도 안전하게 짜여 있습니다 (`ON CONFLICT` / `DELETE … WHERE email = …`).

### 1차 실행 — 회원가입 직후
- 들어가는 데이터
  - `products` 6건 (예금/적금/ETF/IRP 카탈로그) — `/agent/prescriptions` 가 추천에 사용
  - `dummy_mydata` 6건 — `/linking` 화면 미리보기/연결 대상 (이메일 `flowtest@wooriport.com` 기준)
- 이 시점에는 아직 `assets` 가 없으므로 `transactions` 는 들어가지 않고 `RAISE NOTICE` 만 뜸

### 2차 실행 — `/linking` + `/salary-select` 완료 후
- 들어가는 데이터
  - `transactions` (해당 사용자 행 전체 삭제 후 재삽입)
    - **급여 3개월치** (매월 25일경 +3,200,000, category `급여`) — `AgentService.findLatestSalaryTransaction` 이 `급여` 키워드로 찾음
    - **변동 지출 3개월치** (식비/카페/문화·여가/온라인쇼핑/교통)
    - **고정 지출 3개월치** (통신/공과금/보험료) — `AgentService.FIXED_CATEGORIES` 와 일치
- 끝나면 `NOTICE` 로 `user_id`, `salary asset_id` 가 출력됨

### 실행 예시 (psql)

```powershell
# 1차 (회원가입 후)
psql -h localhost -U wooriport -d wooriport -f backend/sql/dummy_full_flow_test.sql

# (프론트에서 /linking, /salary-select 진행)

# 2차 (linking + salary-select 후)
psql -h localhost -U wooriport -d wooriport -f backend/sql/dummy_full_flow_test.sql
```

비밀번호는 `wooriport1234` (기본값).

### 테스트 계정 정보

| 항목 | 값 |
|---|---|
| 이메일 | `flowtest@wooriport.com` |
| 비밀번호 | `Test1234!` (대문자/소문자/숫자/특수문자 포함 8자) |
| 이름 | `테스터` |

### 마지막 확인 쿼리
SQL 끝에 시드 현황 출력이 붙어 있습니다.

```
users / dummy_mydata / assets / transactions / products (active) 카운트
```

---

## 2. mock FastAPI 서버 — `mock_asset_portfolio.py`

Spring `AgentService` 가 호출하는 3개 엔드포인트를 흉내냅니다.

| 엔드포인트 | 호출 시점 | 백엔드 호출자 |
|---|---|---|
| `POST /portfolio/profile`   | `/porti-survey` 제출   | `AgentService.generateProfile` |
| `POST /portfolio/rebalance` | `/asset-prescription`  | `AgentService.recommend` |
| `POST /asset-portfolio`     | `/prescription-loading` → `/complete` | `AgentService.generatePrescriptions` |

### 실행 방법

```powershell
cd mock-server
.\venv\Scripts\Activate.ps1       # venv 없으면 먼저 만들기 (mock-server/README.md 참고)
pip install fastapi uvicorn       # 한 번만
uvicorn mock_asset_portfolio:app --host 0.0.0.0 --port 8000 --reload
```

- 기본 포트: **8000**
- Kafka 거래 이벤트 mock(`mock_payment.py`) 과는 **별도 프로세스**입니다. 둘 다 필요하면 터미널 2개에서 각각 실행하세요.
- 헬스체크: `GET http://localhost:8000/` → `{"status": "ok"}`

### 응답 형태 (요약)

- `POST /portfolio/profile`
  → `{ expense_comment, invest_comment, savings_comment }` 3종 코멘트
- `POST /portfolio/rebalance`
  → `{ invest_amount, salary_rebalance: [{asset_number, category, ratio}] }`
  - `ratio` 는 salary 대비 % (백엔드가 `amount = salary * ratio / 100` 계산)
  - 생활비 25% / 비상금 10% / 적금 10% / ETF 15% / IRP 10% 비율로 분배
  - `invest_amount` 는 salary 의 약 31%
- `POST /asset-portfolio`
  → `{ created_at, investment_flows: [...] }`
  - 단기(비상금) / 중기(ISA 채권+주식) / 장기(IRP 주식+IRP) 3개 flow 생성

---

## 3. 프론트 진행 순서 요약

1. `/signup` — 위 테스트 계정 정보로 가입
2. **SQL 1차 실행**
3. `/linking` — 동의 → 우리/카카오/토스 선택 → 계좌 6개 모두 선택
4. `/salary-select` — 우리 WON 통장(또는 카뱅 입출금) 을 급여통장으로 지정
5. **SQL 2차 실행**
6. `/porti-survey` → `POST /api/v1/agent/profile` (mock `/portfolio/profile`)
7. `/asset-prescription` → `POST /api/v1/agent/rebalance` (mock `/portfolio/rebalance`)
8. `/prescription-loading` + `/complete` → `POST /api/v1/agent/prescriptions` (mock `/asset-portfolio`)
9. `/asset-portfolio` → `/dashboard`

---

## 4. 자주 막히는 곳

- **2차 SQL이 `NOTICE` 만 띄우고 끝남**
  → `assets` 가 비어있다는 뜻. 프론트 `/linking` 까지 정상적으로 끝났는지 확인.
- **급여통장 미설정 경고**
  → `/salary-select` 에서 급여통장을 안 골랐거나, 고른 계좌가 `CHECKING` 이 아닌 경우.
- **`/porti-survey` 에서 502/Connection refused**
  → mock FastAPI(8000) 가 안 떠 있음. `uvicorn mock_asset_portfolio:app …` 다시 확인.
- **추천 상품이 비어서 나옴**
  → `products` 테이블에 카탈로그가 비었음. SQL 1차를 안 돌렸거나 `deleted_at` 처리됨.
