-- =========================================================
-- 프론트 → 백엔드 → mock FastAPI 전체 흐름 테스트용 시드
--
-- ⚠️ 이 SQL은 두 번 실행해야 합니다.
--    1차 실행: 회원가입 직후 (dummy_mydata + products 만 들어감)
--    2차 실행: Linking + SalarySelect 끝난 뒤 (transactions 추가)
--    → 같은 파일을 두 번 돌려도 안전하게 짜놨음.
--
-- 실행 흐름:
-- ───────────────────────────────────────────────────────
-- 1) Spring Boot 앱 실행 (ddl-auto=create / update)
--    FastAPI mock 실행:
--      cd mock-server
--      uvicorn mock_asset_portfolio:app --host 0.0.0.0 --port 8000 --reload
--    프론트 실행: npm run dev
--
-- 2) 프론트 / → /signup
--      이름: 테스터, 이메일: flowtest@wooriport.com,
--      비밀번호: Test1234! (대/소/숫/특, 8자)
--
-- 3) 이 SQL 1차 실행 (psql)  → dummy_mydata 8건 + products 6건
--
-- 4) 프론트 /linking → 동의 → 우리/카카오/토스 선택 → 계좌 8개 모두 선택
--    /salary-select → 우리 WON 통장(또는 카뱅 입출금) 을 급여통장으로 지정
--
-- 5) 이 SQL 2차 실행 (psql)  → transactions 추가
--
-- 6) /porti-survey 부터 끝까지 진행
--      /porti-survey      → POST /api/v1/agent/profile
--      /asset-prescription→ POST /api/v1/agent/rebalance
--      /prescription-loading + /complete → POST /api/v1/agent/prescriptions
--      /asset-portfolio → /dashboard
-- =========================================================

-- =========================================================
-- PART 1. 글로벌 시드 (사용자 무관, 재실행 안전)
-- =========================================================

-- products — /agent/prescriptions 에서 추천할 상품 카탈로그
INSERT INTO products (id, product_type, institution, name, interest_rate, description, updated_at, created_at) VALUES
    ('aaaa1111-bbbb-1111-cccc-111111111111', 'DEPOSIT', '우리은행',   'WON 정기예금',     3.50,  '12개월 만기 정기예금',          NOW(), NOW()),
    ('aaaa2222-bbbb-2222-cccc-222222222222', 'SAVING',  '카카오뱅크', '26주 적금',        7.00,  '매주 늘려 모으는 단기 적금',     NOW(), NOW()),
    ('aaaa3333-bbbb-3333-cccc-333333333333', 'STOCK',   '미래에셋',   'TIGER 미국S&P500', 10.50, '미국 S&P500 추종 ETF',         NOW(), NOW()),
    ('aaaa4444-bbbb-4444-cccc-444444444444', 'IRP',     '미래에셋',   '미래에셋 TDF2045', 6.20,  '은퇴시점 자동배분 IRP 상품',    NOW(), NOW()),
    ('aaaa5555-bbbb-5555-cccc-555555555555', 'STOCK',   '미래에셋',   'KODEX 나스닥100',  12.80, '미국 나스닥100 추종 ETF',       NOW(), NOW()),
    ('aaaa6666-bbbb-6666-cccc-666666666666', 'SAVING',  '토스뱅크',   '토스 자유적금',    4.50,  '자유 입금 가능한 적금',         NOW(), NOW())
ON CONFLICT (id) DO UPDATE
    SET product_type   = EXCLUDED.product_type,
        institution    = EXCLUDED.institution,
        name           = EXCLUDED.name,
        interest_rate  = EXCLUDED.interest_rate,
        description    = EXCLUDED.description,
        updated_at     = EXCLUDED.updated_at;

-- =========================================================
-- PART 2. dummy_mydata — Linking 화면에서 미리보기/연결 대상
--   이메일 'flowtest@wooriport.com' 로 회원가입한 사용자만 해당
--   재실행 시 기존 같은 이메일 행 지우고 다시 INSERT
-- =========================================================
DELETE FROM dummy_mydata WHERE email = 'flowtest@wooriport.com';

INSERT INTO dummy_mydata (email, institution, asset_type, account_name, account_purpose, asset_number, balance, bank_type, is_salary) VALUES
    ('flowtest@wooriport.com', '우리은행',   'CHECKING', '우리 WON 통장',    '생활비', '1002-111-111111', 6800000, 'WOORI', false),
    ('flowtest@wooriport.com', '토스뱅크',   'PARKING',  '토스 파킹통장',    '현금성', '4444-22-222222',  2000000, 'OTHER', false),
    ('flowtest@wooriport.com', '카카오뱅크', 'SAVINGS',  '카뱅 26주 적금',   '적금',  '3333-33-333333',  1500000, 'OTHER', false),
    ('flowtest@wooriport.com', '우리은행',   'STOCK',    'TIGER 미국S&P500', '투자',  '5555-44-444444',  4000000, 'WOORI', false),
    ('flowtest@wooriport.com', '미래에셋',   'IRP',      '미래에셋 IRP',     '투자',  '6666-55-555555',  1000000, 'OTHER', false),
    ('flowtest@wooriport.com', '토스뱅크',   'PARKING',  '여행 모음통장',    '여행',  '4444-66-666666',   335000, 'OTHER', false),
    -- ── 세제혜택 화면(/tax-benefits) 시연용 계좌 ──
    --   ISA / 연금저축펀드 계좌가 있어야 수익률·세액공제를 보여줄 수 있어 추가함.
    --   · ISA            : balance = 현재 평가액(원금+수익). 납입원금은 따로 알 수 없어 PART 4 에서 시드 → 수익률 계산.
    --   · PENSION_SAVINGS: 연금저축펀드. IRP(100만)와 합산해 세액공제 한도(900만) 시연용. balance 를 납입액으로 간주.
    ('flowtest@wooriport.com', '미래에셋',   'ISA',             'ISA 종합계좌', '투자', '7777-77-777777', 12000000, 'OTHER', false),
    ('flowtest@wooriport.com', '한국투자',   'PENSION_SAVINGS', '연금저축펀드', '노후', '8888-88-888888',  5000000, 'OTHER', false),
    -- ── 신용카드 2개 ──
    --   mock_payment.py 가 assets.asset_type='CREDIT_CARD' 의 asset_number 로 거래를 생성하므로
    --   /linking 에서 이 두 카드를 연동하면 카드 결제 더미가 흘러들어옴.
    ('flowtest@wooriport.com', '우리카드',   'CREDIT_CARD',     '우리 카드의정석', '카드', '5570-1111-2222-3333', 0, 'WOORI', false),
    ('flowtest@wooriport.com', '신한카드',   'CREDIT_CARD',     '신한 Deep Dream', '카드', '4000-4444-5555-6666', 0, 'OTHER', false);

-- =========================================================
-- PART 3. transactions — Linking + SalarySelect 가 끝난 뒤에만 실행됨
--   - 자산이 아직 연동 안 됐으면 NOTICE 만 띄우고 종료
--   - 급여통장(is_salary=TRUE) 가 있으면 그쪽으로 급여/지출 트랜잭션 생성
-- =========================================================
DO $$
DECLARE
    v_user         UUID;
    v_salary_asset UUID;
    v_asset_count  INTEGER;
BEGIN
    -- 사용자 확인
    SELECT id INTO v_user FROM users WHERE email = 'flowtest@wooriport.com';
    IF v_user IS NULL THEN
        RAISE EXCEPTION '⚠ flowtest@wooriport.com 사용자가 없습니다. 프론트에서 먼저 signup 하세요.';
    END IF;

    -- 자산 연동 여부 확인
    SELECT COUNT(*) INTO v_asset_count
      FROM assets WHERE user_id = v_user AND deleted_at IS NULL;

    IF v_asset_count = 0 THEN
        RAISE NOTICE '────────────────────────────────────────────────';
        RAISE NOTICE '1차 실행 완료: dummy_mydata + products 시드 완료';
        RAISE NOTICE '👉 이제 프론트에서 /linking → /salary-select 진행 후';
        RAISE NOTICE '   이 SQL 을 다시 실행하면 transactions 가 들어갑니다.';
        RAISE NOTICE '────────────────────────────────────────────────';
        RETURN;
    END IF;

    -- 급여통장 확인
    SELECT id INTO v_salary_asset
      FROM assets
     WHERE user_id = v_user
       AND is_salary = TRUE
       AND deleted_at IS NULL
     LIMIT 1;

    IF v_salary_asset IS NULL THEN
        -- 급여통장 미설정 → CHECKING 계좌라도 사용
        SELECT id INTO v_salary_asset
          FROM assets
         WHERE user_id = v_user
           AND asset_type = 'CHECKING'
           AND deleted_at IS NULL
         LIMIT 1;
    END IF;

    IF v_salary_asset IS NULL THEN
        RAISE NOTICE '⚠ 급여통장(또는 CHECKING 계좌) 이 없습니다.';
        RAISE NOTICE '   /salary-select 에서 급여통장을 먼저 지정해주세요.';
        RETURN;
    END IF;

    -- 기존 트랜잭션 정리 (재실행 가능)
    DELETE FROM transactions WHERE user_id = v_user;

    -- ─────────────────────────────────────────────
    -- 3-1. 급여 (3개월치, 매월 25일경 +3,200,000)
    --      AgentService.findLatestSalaryTransaction 가 '급여' 키워드로 찾음
    -- ─────────────────────────────────────────────
    INSERT INTO transactions (id, user_id, asset_id, amount, category, sender_name, transaction_at) VALUES
    (gen_random_uuid(), v_user, v_salary_asset, 4800000, '급여', '우리포트(주)', date_trunc('month', NOW()) - INTERVAL '2 month' + INTERVAL '24 day'),
    (gen_random_uuid(), v_user, v_salary_asset, 4800000, '급여', '우리포트(주)', date_trunc('month', NOW()) - INTERVAL '1 month' + INTERVAL '24 day'),
    -- 이번 달 급여: 25일이 아직 안 지났으면 NOW() 로 상한 → 미래 날짜 방지
    -- (미래 날짜면 findLatestSalaryTransaction 의 ORDER BY transaction_at DESC 가 이 행을 계속 '최신'으로 잡아 mock 급여가 반영 안 됨)
    (gen_random_uuid(), v_user, v_salary_asset, 4800000, '급여', '우리포트(주)', LEAST(date_trunc('month', NOW()) + INTERVAL '24 day', NOW()));

    -- ─────────────────────────────────────────────
    -- 3-2. 변동 지출 — 3개월치 (식비/카페/문화/온라인쇼핑/교통)
    -- ─────────────────────────────────────────────
    INSERT INTO transactions (id, user_id, asset_id, amount, category, sender_name, transaction_at)
    SELECT gen_random_uuid(), v_user, v_salary_asset, amount, category, sender, txn_at
    FROM (VALUES
        -- 이번 달
        (-450000, '식비',       '배달의민족',   date_trunc('month', NOW()) + INTERVAL '1 day'),
        (-180000, '식비',       '쿠팡이츠',     date_trunc('month', NOW()) + INTERVAL '4 day'),
        (-150000, '카페',       '스타벅스',     date_trunc('month', NOW()) + INTERVAL '8 day'),
        (-241000, '문화/여가',  'CGV',          date_trunc('month', NOW()) + INTERVAL '6 day'),
        (-260000, '온라인쇼핑', '쿠팡',         date_trunc('month', NOW()) + INTERVAL '2 day'),
        (-150000, '교통',       '카카오T',      date_trunc('month', NOW()) + INTERVAL '3 day'),
        (-46000,  '교통',       '티머니',       date_trunc('month', NOW()) + INTERVAL '13 day'),
        -- 1개월 전
        (-420000, '식비',       '배달의민족',   date_trunc('month', NOW()) - INTERVAL '1 month' + INTERVAL '2 day'),
        (-130000, '카페',       '투썸플레이스', date_trunc('month', NOW()) - INTERVAL '1 month' + INTERVAL '5 day'),
        (-200000, '문화/여가',  '인터파크',     date_trunc('month', NOW()) - INTERVAL '1 month' + INTERVAL '9 day'),
        (-310000, '온라인쇼핑', '쿠팡',         date_trunc('month', NOW()) - INTERVAL '1 month' + INTERVAL '12 day'),
        (-180000, '교통',       '카카오T',      date_trunc('month', NOW()) - INTERVAL '1 month' + INTERVAL '14 day'),
        -- 2개월 전
        (-380000, '식비',       '쿠팡이츠',     date_trunc('month', NOW()) - INTERVAL '2 month' + INTERVAL '3 day'),
        (-110000, '카페',       '스타벅스',     date_trunc('month', NOW()) - INTERVAL '2 month' + INTERVAL '6 day'),
        (-220000, '문화/여가',  'YES24',        date_trunc('month', NOW()) - INTERVAL '2 month' + INTERVAL '10 day'),
        (-280000, '온라인쇼핑', '11번가',       date_trunc('month', NOW()) - INTERVAL '2 month' + INTERVAL '13 day'),
        (-160000, '교통',       '티머니',       date_trunc('month', NOW()) - INTERVAL '2 month' + INTERVAL '15 day')
    ) AS t(amount, category, sender, txn_at);

    -- ─────────────────────────────────────────────
    -- 3-3. 고정 지출 — 통신/공과금/보험료 (AgentService.FIXED_CATEGORIES 와 일치)
    -- ─────────────────────────────────────────────
    INSERT INTO transactions (id, user_id, asset_id, amount, category, sender_name, transaction_at)
    SELECT gen_random_uuid(), v_user, v_salary_asset, amount, category, sender, txn_at
    FROM (VALUES
        (-65000,  '통신',   'SKT',      date_trunc('month', NOW()) + INTERVAL '5 day'),
        (-120000, '공과금', '한국전력', date_trunc('month', NOW()) + INTERVAL '10 day'),
        (-95000,  '보험료', '삼성생명', date_trunc('month', NOW()) + INTERVAL '17 day'),
        (-65000,  '통신',   'SKT',      date_trunc('month', NOW()) - INTERVAL '1 month' + INTERVAL '5 day'),
        (-115000, '공과금', '한국전력', date_trunc('month', NOW()) - INTERVAL '1 month' + INTERVAL '10 day'),
        (-95000,  '보험료', '삼성생명', date_trunc('month', NOW()) - INTERVAL '1 month' + INTERVAL '17 day'),
        (-65000,  '통신',   'SKT',      date_trunc('month', NOW()) - INTERVAL '2 month' + INTERVAL '5 day'),
        (-118000, '공과금', '한국전력', date_trunc('month', NOW()) - INTERVAL '2 month' + INTERVAL '10 day'),
        (-95000,  '보험료', '삼성생명', date_trunc('month', NOW()) - INTERVAL '2 month' + INTERVAL '17 day')
    ) AS t(amount, category, sender, txn_at);

    RAISE NOTICE '────────────────────────────────────────────────';
    RAISE NOTICE '✅ 2차 실행 완료: transactions 시드 완료';
    RAISE NOTICE '   - 사용자: %', v_user;
    RAISE NOTICE '   - 급여통장 asset_id: %', v_salary_asset;
    RAISE NOTICE '👉 이제 프론트 /porti-survey 부터 진행하면 됩니다.';
    RAISE NOTICE '────────────────────────────────────────────────';
END $$;

-- =========================================================
-- PART 4. tax_benefit_accounts — 세제혜택 화면 보조 데이터
--   ⚠ 2차 실행(계좌 연동 후)에만 적재됨. 1차에는 assets 가 없어 자동 스킵.
--
--   [왜 이 테이블이 필요한가]
--   ISA 혜택 현황은 "수익률"인데  수익률 = (현재평가액 - 납입원금) / 납입원금  이다.
--   그런데 우리가 가진 테이블만으로는 '납입원금' 을 알 수 없다:
--     · assets.balance 는 현재 평가액(원금+수익) 이라 원금/수익을 분리 못 함
--     · transactions 에는 ISA 입금 내역이 없음
--       (TransactionConsumer 가 모든 거래를 음수=출금으로 적재 + 더미 거래는 급여통장에만 쌓음)
--   그래서 ISA 납입원금만 이 테이블에 따로 들고 간다.
--   (IRP/연금저축 세액공제는 balance × 공제율 로 계산되므로 별도 저장 불필요)
--
--   [다음 페르소나 만들 때 활용법]
--   assets 는 /linking 연동 시 app 이 asset_number 를 매칭해 UUID 를 새로 발급한다.
--   따라서 asset_id 를 하드코딩하지 말고, 아래처럼 asset_number 로 조회해서 넣을 것.
-- =========================================================
DO $$
DECLARE
    v_user      UUID;
    v_isa_asset UUID;
BEGIN
    SELECT id INTO v_user FROM users WHERE email = 'flowtest@wooriport.com';
    IF v_user IS NULL THEN
        RETURN;  -- signup 전 → 스킵
    END IF;

    -- ISA 계좌(asset_number 7777-77-777777) 가 연동됐을 때만 원금 시드
    SELECT id INTO v_isa_asset
      FROM assets
     WHERE user_id = v_user
       AND asset_number = '7777-77-777777'
       AND deleted_at IS NULL
     LIMIT 1;

    IF v_isa_asset IS NULL THEN
        RAISE NOTICE 'ℹ ISA 계좌 미연동 → tax_benefit_accounts 시드 스킵 (/linking 후 재실행)';
        RETURN;
    END IF;

    -- 납입원금 11,000,000  (현재 평가액 12,000,000 → 수익 1,000,000 / 수익률 ≈ 9.09%)
    -- asset_id 가 unique 라 ON CONFLICT 로 재실행 안전하게 갱신
    INSERT INTO tax_benefit_accounts (id, asset_id, principal, created_at)
    VALUES (gen_random_uuid(), v_isa_asset, 11000000, NOW())
    ON CONFLICT (asset_id) DO UPDATE SET principal = EXCLUDED.principal;

    RAISE NOTICE '✅ tax_benefit_accounts 시드 완료 — ISA asset_id: %, principal: 11,000,000', v_isa_asset;
END $$;

-- =========================================================
-- 확인 쿼리
-- =========================================================
SELECT '──── 시드 현황 ────' AS section;

SELECT 'users'        AS table_name, COUNT(*) AS cnt FROM users        WHERE email   = 'flowtest@wooriport.com'
UNION ALL
SELECT 'dummy_mydata',                COUNT(*) FROM dummy_mydata WHERE email   = 'flowtest@wooriport.com'
UNION ALL
SELECT 'assets',                      COUNT(*) FROM assets       WHERE user_id = (SELECT id FROM users WHERE email = 'flowtest@wooriport.com') AND deleted_at IS NULL
UNION ALL
SELECT 'cards (CREDIT_CARD)',         COUNT(*) FROM assets       WHERE user_id = (SELECT id FROM users WHERE email = 'flowtest@wooriport.com') AND asset_type = 'CREDIT_CARD' AND deleted_at IS NULL
UNION ALL
SELECT 'transactions',                COUNT(*) FROM transactions WHERE user_id = (SELECT id FROM users WHERE email = 'flowtest@wooriport.com')
UNION ALL
SELECT 'tax_benefit_accounts',        COUNT(*) FROM tax_benefit_accounts WHERE asset_id IN (SELECT id FROM assets WHERE user_id = (SELECT id FROM users WHERE email = 'flowtest@wooriport.com'))
UNION ALL
SELECT 'products (active)',           COUNT(*) FROM products     WHERE deleted_at IS NULL;
