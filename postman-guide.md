# Postman 테스트 가이드

테스트 대상: `PATCH /portfolios` · `POST /transfer-plans/generate` · `GET /transfer-plans`

Base URL: `http://localhost:8080/api/v1`

---

## 순서 개요

```
1. 회원가입
2. 로그인 → 토큰 저장
3. dummy_mydata SQL 삽입
4. 자산 연동 (sync)
5. 급여통장 설정
6. 급여 트랜잭션 SQL 삽입
7. 포트폴리오 생성 (POST)
8. portfolio_flows SQL 삽입
9. ★ PATCH /portfolios 테스트
10. ★ POST /transfer-plans/generate 테스트
11. ★ GET /transfer-plans 테스트
```

---

## STEP 1 — 회원가입

**POST** `/auth/signup`

```json
{
  "email": "test@wooriport.com",
  "password": "Test1234!",
  "name": "테스트유저"
}
```

예상 응답: `201`

---

## STEP 2 — 로그인 → 토큰 저장

**POST** `/auth/login`

```json
{
  "email": "test@wooriport.com",
  "password": "Test1234!"
}
```

응답에서 `accessToken` 복사 → 이후 모든 요청 Header에 추가:
```
Authorization: Bearer {accessToken}
```

---

## STEP 3 — dummy_mydata SQL 삽입

`test-setup.sql` 상단 **[STEP 1]** 블록을 실행합니다.
(email을 `test@wooriport.com`으로 맞춰 삽입)

---

## STEP 4 — 자산 연동

**POST** `/assets/sync`

```json
{
  "assetNumbers": []
}
```

> `assetNumbers`를 빈 배열로 보내면 해당 이메일의 dummy_mydata 전체 연동

응답에서 각 계좌의 `id`(assetId) 확인:
- `CHECKING` 우리은행 → `SALARY_ASSET_ID`
- `PARKING` 카카오뱅크 → `PARKING_ASSET_ID`
- `SAVINGS` 우리은행 → `SAVINGS_ASSET_ID`
- `STOCK` 삼성증권 → `STOCK_ASSET_ID`

---

## STEP 5 — 급여통장 설정

**PATCH** `/assets/{SALARY_ASSET_ID}/salary`

응답 확인 후 완료

---

## STEP 6 — 급여 트랜잭션 + PortfolioFlow SQL 삽입

`test-setup.sql` 의 **[STEP 2]**, **[STEP 3]** 블록을 실행합니다.

DB에서 userId 확인:
```sql
SELECT id FROM users WHERE email = 'test@wooriport.com';
```

교체:
- `USER_ID_HERE` → userId
- `SALARY_ASSET_ID` → STEP 4에서 확인한 CHECKING 계좌 id
- `PARKING_ASSET_ID` → STEP 4에서 확인한 PARKING 계좌 id

---

## STEP 7 — 포트폴리오 생성

**POST** `/portfolios`

```json
{
  "salary": 3000000,
  "monthlyInvestAmount": 500000,
  "portfolios": [
    {
      "assetType": "CASH",
      "assetAmount": 200000,
      "assetId": "{SAVINGS_ASSET_ID}",
      "accountPurpose": "비상금"
    },
    {
      "assetType": "STOCK",
      "assetAmount": 300000,
      "assetId": "{STOCK_ASSET_ID}",
      "accountPurpose": "주식"
    }
  ]
}
```

---

## ★ STEP 8 — PATCH /portfolios 테스트

**PATCH** `/portfolios`

portfolios 목록과 monthlyInvestAmount를 전달하면 기존 포트폴리오를 전체 삭제 후 재생성하고,
portfolioFlow.amount를 비율에 맞게 재계산합니다.

```json
{
  "monthlyInvestAmount": 600000,
  "portfolios": [
    {
      "assetId": "{SAVINGS_ASSET_ID}",
      "assetAmount": 250000,
      "accountPurpose": "비상금"
    },
    {
      "assetId": "{STOCK_ASSET_ID}",
      "assetAmount": 350000,
      "accountPurpose": "주식"
    }
  ]
}
```

**검증 포인트:**
- 응답의 `portfolios` 금액이 요청값과 동일한지 확인
- DB에서 portfolio_flows.amount 확인:
  ```sql
  -- 기존 flow.amount = 500000, 이전 monthlyInvestAmount = 500000, 새 = 600000
  -- 예상: 500000 * 600000 / 500000 = 600000
  SELECT amount FROM portfolio_flows WHERE user_id = 'USER_ID_HERE';
  ```

---

## ★ STEP 9 — POST /transfer-plans/generate 테스트

**POST** `/transfer-plans/generate`

Body 없음

**검증 포인트:**
- 응답의 `plans` 리스트에 portfolio 항목과 flow 항목 모두 포함되는지 확인
- portfolio plans: SAVINGS, STOCK 계좌 각각 1건
- flow plan: PARKING 계좌 1건 (gatheringAsset)

```sql
-- 생성된 계획 확인
SELECT tp.planned_amount, a.institution, a.asset_type
FROM transfer_plans tp
JOIN assets a ON tp.asset_id = a.id
WHERE tp.user_id = 'USER_ID_HERE'
  AND tp.year = 2026 AND tp.month = 5;
```

---

## ★ STEP 10 — GET /transfer-plans 테스트

**GET** `/transfer-plans?year=2026&month=5`

**예상 응답 구조:**
```json
{
  "currentSalary": 3000000,
  "salaryDiff": 0,
  "portfolioTotal": 600000,
  "portfolioTotalDiff": ...,
  "portfolioItems": [
    { "institution": "우리은행", "plannedAmount": 250000, "baselineAmount": 250000, "diff": 0 },
    { "institution": "삼성증권", "plannedAmount": 350000, "baselineAmount": 350000, "diff": 0 }
  ],
  "flowTotal": 600000,
  "flowItems": [
    { "institution": "카카오뱅크", "plannedAmount": 600000, "baselineAmount": 600000, "diff": 0 }
  ],
  "remaining": 1800000,
  "remainingDiff": ...
}
```

**검증 포인트:**
- `portfolioItems`에 SAVINGS, STOCK 계좌가 잡히는지
- `flowItems`에 PARKING(gatheringAsset) 계좌가 잡히는지
- `diff`가 0인지 (generate 직후라 수정 없으므로)
