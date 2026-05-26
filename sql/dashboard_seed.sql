-- =========================================================
-- Dashboard API 테스트용 시드 데이터
--
-- 실행 흐름:
-- 1) Spring Boot 앱 실행 (ddl-auto=create 면 테이블 자동 생성)
-- 2) Swagger 에서 회원가입:
--      POST /api/v1/auth/signup
--      { "email": "dashboard@wooriport.com",
--        "password": "test1234!",
--        "name": "서태형",
--        "phone": "010-9999-0000" }
-- 3) 이 SQL 실행 → users 는 만들지 않고, 위에서 만든 user 의 id 를 찾아 자식 데이터를 연결합니다.
-- 4) Swagger 에서 로그인 후 /api/v1/dashboard 호출
-- =========================================================

DO $$
DECLARE
    v_user UUID;
BEGIN
    -- 0. signup 으로 만들어진 user 찾기
    SELECT id INTO v_user FROM users WHERE email = 'dashboard@wooriport.com';
    IF v_user IS NULL THEN
        RAISE EXCEPTION 'dashboard@wooriport.com 사용자가 없습니다. Swagger 에서 먼저 signup 하세요.';
    END IF;

    -- 1. 기존 시드 정리 (재실행 가능)
    DELETE FROM transactions    WHERE user_id = v_user;
    DELETE FROM portfolio_items WHERE user_id = v_user;
    DELETE FROM portfolios      WHERE user_id = v_user;
    DELETE FROM event           WHERE user_id = v_user;
    DELETE FROM assets          WHERE user_id = v_user;

    -- =========================================================
    -- 2. ASSETS (5개)
    --   a1: 급여통장 (CHECKING, WOORI, 생활비)
    --   a2: 저축    (SAVINGS,  OTHER, 저축)
    --   a3: 예금    (DEPOSIT,  OTHER, 비상금)
    --   a4: 주식    (STOCK,    WOORI, 투자)
    --   a5: 채권    (STOCK,    OTHER, 투자)
    -- =========================================================
    INSERT INTO assets (id, user_id, institution, asset_number, asset_type, account_name, account_purpose, is_salary, balance, synced_at, bank_type, created_at) VALUES
    ('a1111111-1111-1111-1111-111111111111', v_user, '우리은행',   '1002-111-111111', 'CHECKING', '우리 WON 통장',  '생활비', TRUE,   6800000, NOW(), 'WOORI', NOW()),
    ('a2222222-2222-2222-2222-222222222222', v_user, '카카오뱅크', '3333-22-222222',  'SAVINGS',  '카뱅 저축통장',  '저축',  FALSE,  5850000, NOW(), 'OTHER', NOW()),
    ('a3333333-3333-3333-3333-333333333333', v_user, '토스뱅크',   '4444-33-333333',  'DEPOSIT',  '토스 예금',     '비상금', FALSE,        0, NOW(), 'OTHER', NOW()),
    ('a4444444-4444-4444-4444-444444444444', v_user, '우리은행',   '5555-44-444444',  'STOCK',    '우리 ETF',      '투자',  FALSE, 12980000, NOW(), 'WOORI', NOW()),
    ('a5555555-5555-5555-5555-555555555555', v_user, '신한은행',   '6666-55-555555',  'STOCK',    '신한 채권펀드', '투자',  FALSE,  6820000, NOW(), 'OTHER', NOW());

    -- =========================================================
    -- 3. PORTFOLIOS (월급 배분, monthlyIncome = 3,200,000)
    -- =========================================================
    INSERT INTO portfolios (id, user_id, asset_type, asset_amount, asset_id, created_at) VALUES
    ('b1111111-1111-1111-1111-111111111111', v_user, 'FIXED',     1504000, 'a1111111-1111-1111-1111-111111111111', NOW()),
    ('b2222222-2222-2222-2222-222222222222', v_user, 'CASH',       608000, 'a2222222-2222-2222-2222-222222222222', NOW()),
    ('b3333333-3333-3333-3333-333333333333', v_user, 'EMERGENCY',  320000, 'a3333333-3333-3333-3333-333333333333', NOW()),
    ('b4444444-4444-4444-4444-444444444444', v_user, 'STOCK',      768000, 'a4444444-4444-4444-4444-444444444444', NOW());

    -- =========================================================
    -- 4. PORTFOLIO_ITEMS
    --    cashBalance   = a2 + a3 = 5,850,000  (DEPOSIT 분류)
    --    investmentBal = a4 + a5 = 19,800,000 (STOCK/BOND 분류)
    -- =========================================================
    INSERT INTO portfolio_items (id, event_id, user_id, product_id, product_type, product_ratio, asset_id, created_at) VALUES
    ('c1111111-1111-1111-1111-111111111111', NULL, v_user, NULL, 'DEPOSIT', 100, 'a2222222-2222-2222-2222-222222222222', NOW()),
    ('c2222222-2222-2222-2222-222222222222', NULL, v_user, NULL, 'DEPOSIT', 100, 'a3333333-3333-3333-3333-333333333333', NOW()),
    ('c3333333-3333-3333-3333-333333333333', NULL, v_user, NULL, 'STOCK',   100, 'a4444444-4444-4444-4444-444444444444', NOW()),
    ('c4444444-4444-4444-4444-444444444444', NULL, v_user, NULL, 'BOND',    100, 'a5555555-5555-5555-5555-555555555555', NOW());

    -- =========================================================
    -- 5. EVENT (is_active_dashboard = true)
    -- =========================================================
    INSERT INTO event (id, user_id, source_asset_id, event_type, title, target_amount, initial_amount, duration_months, current_amount, deadline, status, priority, is_active_dashboard, created_at) VALUES
    ('e1111111-1111-1111-1111-111111111111',
     v_user,
     'a2222222-2222-2222-2222-222222222222',
     'TRAVEL', '제주 여행', 500000, 100000, 2, 335000, DATE '2026-07-10',
     'ACTIVE', 1, TRUE, NOW());

    -- =========================================================
    -- 6. TRANSACTIONS (이번 달, 총 2,450,000)
    --    식비 1,029,000 / 문화여가 441,000 / 온라인쇼핑 514,500 / 교통 196,000 / 기타 269,500
    -- =========================================================
    INSERT INTO transactions (id, user_id, asset_id, amount, category, sender_name, transaction_at)
    SELECT gen_random_uuid(), v_user, 'a1111111-1111-1111-1111-111111111111', amount, category, sender, txn_at
    FROM (VALUES
        (-1029000, '식비',       '배달의민족', date_trunc('month', NOW()) + INTERVAL '5 day'),
        ( -441000, '문화/여가',  'CGV',        date_trunc('month', NOW()) + INTERVAL '6 day'),
        ( -514500, '온라인쇼핑', '쿠팡',       date_trunc('month', NOW()) + INTERVAL '7 day'),
        ( -196000, '교통',       '카카오T',    date_trunc('month', NOW()) + INTERVAL '8 day'),
        ( -269500, '기타',       '편의점',     date_trunc('month', NOW()) + INTERVAL '9 day')
    ) AS t(amount, category, sender, txn_at);
END $$;

-- =========================================================
-- 7. 확인
-- =========================================================
SELECT 'users'           AS table_name, COUNT(*) AS cnt FROM users           WHERE email   = 'dashboard@wooriport.com'
UNION ALL SELECT 'assets',          COUNT(*) FROM assets          WHERE user_id = (SELECT id FROM users WHERE email = 'dashboard@wooriport.com')
UNION ALL SELECT 'portfolios',      COUNT(*) FROM portfolios      WHERE user_id = (SELECT id FROM users WHERE email = 'dashboard@wooriport.com')
UNION ALL SELECT 'portfolio_items', COUNT(*) FROM portfolio_items WHERE user_id = (SELECT id FROM users WHERE email = 'dashboard@wooriport.com')
UNION ALL SELECT 'event',           COUNT(*) FROM event           WHERE user_id = (SELECT id FROM users WHERE email = 'dashboard@wooriport.com')
UNION ALL SELECT 'transactions',    COUNT(*) FROM transactions    WHERE user_id = (SELECT id FROM users WHERE email = 'dashboard@wooriport.com');
