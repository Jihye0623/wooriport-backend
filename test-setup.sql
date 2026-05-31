-- ============================================================
-- 테스트 더미 데이터 세팅 스크립트
-- 실행 순서: 회원가입/로그인/자산연동 API 호출 후 이 SQL 실행
-- ============================================================

-- [STEP 1] 테스트 계정 이메일에 맞춰 dummy_mydata 삽입
-- 아래 이메일을 회원가입 시 사용한 이메일로 교체하세요
-- assetNumber는 계좌번호, balance는 잔액(원)

INSERT INTO dummy_mydata (id, email, institution, asset_type, account_name, account_purpose, asset_number, balance, bank_type, is_salary)
VALUES
  -- 입출금 통장 (급여 수령용, isSalary=true)
  (gen_random_uuid(), 'test@wooriport.com', '우리은행', 'CHECKING', '우리 입출금', '생활비', '1002-123-456789', 3000000, 'WOORI', true),
  -- 파킹 통장
  (gen_random_uuid(), 'test@wooriport.com', '카카오뱅크', 'PARKING', '카카오 파킹', '비상금', '3333-01-1234567', 1500000, 'OTHER', false),
  -- 적금
  (gen_random_uuid(), 'test@wooriport.com', '우리은행', 'SAVINGS', '우리 적금', '고정비', '1002-456-789012', 500000, 'WOORI', false),
  -- 증권 계좌
  (gen_random_uuid(), 'test@wooriport.com', '삼성증권', 'STOCK', '삼성증권 CMA', '주식', '5104-1234-5678', 2000000, 'OTHER', false);


-- ============================================================
-- [STEP 2] 자산 연동(POST /assets/sync) 후 아래 SQL 실행
-- users, assets 테이블에 데이터가 생긴 뒤에 실행해야 함
-- ============================================================

-- 급여 트랜잭션 삽입 (generateFromSalary가 이 row를 찾아 사용)
-- asset_id: 급여통장으로 설정한 자산의 id로 교체 (PATCH /assets/{assetId}/salary 한 자산)
-- user_id: 본인 userId로 교체
INSERT INTO transactions (id, user_id, asset_id, amount, category, transaction_at, description)
VALUES (
  gen_random_uuid(),
  'USER_ID_HERE',       -- 로그인 후 확인한 userId
  'SALARY_ASSET_ID',    -- PATCH /assets/{assetId}/salary 로 설정한 assetId
  3000000,              -- 월급 금액 (원)
  '급여',
  NOW(),
  '5월 급여'
);


-- ============================================================
-- [STEP 3] POST /portfolios로 포트폴리오 생성 후
--          portfolio_flows 수동 삽입 (AI 없이 테스트)
-- ============================================================

-- 흐름(PortfolioFlow) 생성
-- gathering_asset_id: 모으기 통장으로 쓸 assetId (예: 파킹 통장 id)
-- amount: 월 흐름 투자 금액
INSERT INTO portfolio_flows (id, user_id, event_id, title, summary, term, amount, gathering_asset_id, is_active, started_at, created_at, updated_at)
VALUES (
  gen_random_uuid(),
  'USER_ID_HERE',           -- userId
  NULL,                     -- 기본 흐름이므로 event_id 없음
  '흐름 A',
  '비상금·생활비 베이스를 단단히 다져요',
  '단',
  500000,                   -- flow.amount (월 50만원)
  'PARKING_ASSET_ID',       -- gatheringAsset: 파킹 통장 assetId
  true,
  NOW(),
  NOW(),
  NOW()
);
