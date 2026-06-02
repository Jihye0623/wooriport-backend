-- =========================================================
-- Dashboard API 테스트용 시드 데이터 (신규 스키마)
--
-- 변경점:
--   * portfolio_items 대신 portfolio_flows / portfolio_flow_items 사용
--   * event 테이블 컬럼이 엔티티(Event.java)에 맞춰 슬림해짐
--   * product_category_rates (수익률 더미 테이블) 추가
--
-- 실행 흐름:
-- 1) Spring Boot 앱 실행 (ddl-auto=create)
-- 2) Swagger 에서 회원가입:
--      POST /api/v1/auth/signup
--      { "email": "dashboard@wooriport.com",
--        "password": "test1234!",
--        "name": "서태형",
--        "phone": "010-9999-0000" }
-- 3) 이 SQL 실행
-- 4) Swagger 에서 로그인 후 GET /api/v1/dashboard
-- =========================================================

-- ─── 0. 사용자 무관 시드: 수익률 더미 (upsert) ────────────
INSERT INTO product_category_rates (id, category_label, rate) VALUES
    (gen_random_uuid(), 'ETF',    '+4%'),
    (gen_random_uuid(), '현금성', '+2%'),
    (gen_random_uuid(), '적금',   '-'),
    (gen_random_uuid(), 'IRP',    '-')
ON CONFLICT (category_label) DO UPDATE
    SET rate = EXCLUDED.rate;

-- ─── 0-1. 사용자 무관 시드: PRODUCTS (asset-portfolio 화면용) ────
-- portfolio_flow_items.product_id 가 이 상품들을 가리킴.
-- products 는 글로벌이므로 사용자 시드(DO 블록) 재실행 시 지우지 않음.
INSERT INTO products (id, product_type, institution, name, interest_rate, description, updated_at, created_at) VALUES
    ('11111111-bbbb-1111-1111-111111111111', 'DEPOSIT', '우리은행',   'WON 정기예금',      3.50,  '12개월 만기 정기예금',          NOW(), NOW()),
    ('22222222-bbbb-2222-2222-222222222222', 'SAVING',  '카카오뱅크', '26주 적금',         7.00,  '매주 늘려 모으는 단기 적금',     NOW(), NOW()),
    ('33333333-bbbb-3333-3333-333333333333', 'STOCK',   '미래에셋',   'TIGER 미국S&P500',  10.50, '미국 S&P500 추종 ETF',         NOW(), NOW()),
    ('44444444-bbbb-4444-4444-444444444444', 'IRP',     '미래에셋',   '미래에셋 TDF2045',  6.20,  '은퇴시점 자동배분 IRP 상품',    NOW(), NOW()),
    ('55555555-bbbb-5555-5555-555555555555', 'STOCK',   '미래에셋',   'KODEX 나스닥100',   12.80, '미국 나스닥 100 추종 ETF',     NOW(), NOW()),
    ('66666666-bbbb-6666-6666-666666666666', 'SAVING',  '토스뱅크',   '토스 자유적금',     4.50,  '자유 입금 가능한 적금',         NOW(), NOW())
ON CONFLICT (id) DO UPDATE
    SET product_type   = EXCLUDED.product_type,
        institution    = EXCLUDED.institution,
        name           = EXCLUDED.name,
        interest_rate  = EXCLUDED.interest_rate,
        description    = EXCLUDED.description,
        updated_at     = EXCLUDED.updated_at;

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
    DELETE FROM transactions          WHERE user_id = v_user;
    DELETE FROM portfolio_flow_items
        WHERE flow_id IN (SELECT id FROM portfolio_flows WHERE user_id = v_user);
    DELETE FROM portfolio_flows       WHERE user_id = v_user;
    DELETE FROM portfolios            WHERE user_id = v_user;
    DELETE FROM event                 WHERE user_id = v_user;
    DELETE FROM assets                WHERE user_id = v_user;

    -- =========================================================
    -- 2. ASSETS (7개)
    --   a1: 급여통장   (CHECKING, WOORI, 생활비, isSalary)
    --   a2: 파킹       (PARKING,  OTHER, 현금성)
    --   a3: 적금       (SAVINGS,  OTHER, 적금)
    --   a4: 증권 ETF   (STOCK,    WOORI, 투자) — TIGER 미국S&P500
    --   a5: 퇴직연금   (IRP,      OTHER, 투자)
    --   a6: 여행 모음통장 (PARKING, OTHER, event gathering)
    --   a7: 증권 ETF#2 (STOCK,    OTHER, 투자) — KODEX 나스닥100
    -- =========================================================
    INSERT INTO assets (id, user_id, institution, asset_number, asset_type, account_name, account_purpose, is_salary, balance, synced_at, bank_type, created_at) VALUES
    ('a1111111-1111-1111-1111-111111111111', v_user, '우리은행',   '1002-111-111111', 'CHECKING', '우리 WON 통장',     '생활비', TRUE,   6800000, NOW(), 'WOORI', NOW()),
    ('a2222222-2222-2222-2222-222222222222', v_user, '토스뱅크',   '4444-22-222222',  'PARKING',  '토스 파킹통장',     '현금성', FALSE,  2000000, NOW(), 'OTHER', NOW()),
    ('a3333333-3333-3333-3333-333333333333', v_user, '카카오뱅크', '3333-33-333333',  'SAVINGS',  '카뱅 26주 적금',    '적금',  FALSE,  1500000, NOW(), 'OTHER', NOW()),
    ('a4444444-4444-4444-4444-444444444444', v_user, '우리은행',   '5555-44-444444',  'STOCK',    'TIGER 미국S&P500',  '투자',  FALSE,  4000000, NOW(), 'WOORI', NOW()),
    ('a5555555-5555-5555-5555-555555555555', v_user, '미래에셋',   '6666-55-555555',  'IRP',      '미래에셋 IRP',      '투자',  FALSE,  1000000, NOW(), 'OTHER', NOW()),
    ('a6666666-6666-6666-6666-666666666666', v_user, '토스뱅크',   '4444-66-666666',  'PARKING',  '여행 모음통장',     '여행', FALSE,   335000, NOW(), 'OTHER', NOW()),
    ('a7777777-7777-7777-7777-777777777777', v_user, 'KB증권',     '7777-77-777777',  'STOCK',    'KODEX 나스닥100',   '투자',  FALSE,  1500000, NOW(), 'OTHER', NOW());

    -- =========================================================
    -- 3. PORTFOLIOS (월급 배분, monthlyIncome = 3,200,000)
    -- =========================================================
    INSERT INTO portfolios (id, user_id, asset_type, asset_amount, asset_id, created_at) VALUES
    ('b1111111-1111-1111-1111-111111111111', v_user, 'FIXED',     1504000, 'a1111111-1111-1111-1111-111111111111', NOW()),
    ('b2222222-2222-2222-2222-222222222222', v_user, 'CASH',       608000, 'a2222222-2222-2222-2222-222222222222', NOW()),
    ('b3333333-3333-3333-3333-333333333333', v_user, 'EMERGENCY',  320000, 'a6666666-6666-6666-6666-666666666666', NOW()),
    ('b4444444-4444-4444-4444-444444444444', v_user, 'STOCK',      768000, 'a4444444-4444-4444-4444-444444444444', NOW());

    -- =========================================================
    -- 4. EVENT (목표) — current_amount, priority, is_active_dashboard 등은 엔티티에서 빠짐
    --    target=500,000, deadline=2026-07-10
    -- =========================================================
    INSERT INTO event (id, user_id, title, target_amount, deadline, status, summary_message, event_description, deleted_at, created_at) VALUES
    ('e1111111-1111-1111-1111-111111111111',
     v_user,
     '제주 여행',
     500000,
     DATE '2026-07-10',
     'ACTIVE',
     '제주 여행을 위해 매달 모아요',
     '제주 여행 가고 싶어',
     NULL,
     NOW());

    -- =========================================================
    -- 5. PORTFOLIO_FLOWS
    --   f1 (기본 흐름): event_id=NULL,  gathering=a1, PUT 항목 4개
    --   f2 (이벤트 흐름): event_id=e1, gathering=a6 (목표 진행도 = a6.balance / event.target_amount)
    -- =========================================================
    INSERT INTO portfolio_flows (id, user_id, event_id, title, summary, term, gathering_asset_id, is_active, started_at, created_at) VALUES
    ('f1111111-1111-1111-1111-111111111111', v_user, NULL,
     '기본 흐름', '비상금·생활비 베이스를 단단히 다져요', '단',
     'a1111111-1111-1111-1111-111111111111', TRUE, NOW(), NOW()),
    ('f2222222-2222-2222-2222-222222222222', v_user, 'e1111111-1111-1111-1111-111111111111',
     '제주 여행 흐름', '제주 여행 자금을 모으는 흐름이에요', '중',
     'a6666666-6666-6666-6666-666666666666', TRUE, NOW(), NOW());

    -- =========================================================
    -- 6. PORTFOLIO_FLOW_ITEMS
    --    PULL — asset-portfolio "1. 끌어오기" (sources)
    --           amount = 끌어올 금액 (원 단위)
    --    PUT  — asset-portfolio "3. 넣기"     (products)
    --           product_id = products 테이블 FK
    --           label 매핑: STOCK/BOND→ETF, DEPOSIT→현금성, SAVING→적금, IRP→IRP
    -- =========================================================
    INSERT INTO portfolio_flow_items (id, flow_id, asset_id, product_id, product_ratio, created_at, ai_comment) VALUES
    -- f1 (기본 흐름) PULL — 급여통장/파킹에서 끌어옴 (product_ratio NULL → PULL)
    ('aaaa1111-1111-1111-1111-111111111111', 'f1111111-1111-1111-1111-111111111111', 'a1111111-1111-1111-1111-111111111111', NULL, NULL, NOW(), NULL),
    ('aaaa2222-2222-2222-2222-222222222222', 'f1111111-1111-1111-1111-111111111111', 'a2222222-2222-2222-2222-222222222222', NULL, NULL, NOW(), NULL),

    -- f1 (기본 흐름) PUT — 상품에 넣기 (product_id + product_ratio 채움)
    ('11111111-aaaa-1111-1111-111111111111', 'f1111111-1111-1111-1111-111111111111', 'a2222222-2222-2222-2222-222222222222', '11111111-bbbb-1111-1111-111111111111', 20, NOW(), NULL),
    ('22222222-aaaa-2222-2222-222222222222', 'f1111111-1111-1111-1111-111111111111', 'a3333333-3333-3333-3333-333333333333', '22222222-bbbb-2222-2222-222222222222', 20, NOW(), NULL),
    ('33333333-aaaa-3333-3333-333333333333', 'f1111111-1111-1111-1111-111111111111', 'a4444444-4444-4444-4444-444444444444', '33333333-bbbb-3333-3333-333333333333', 30, NOW(), NULL),
    ('44444444-aaaa-4444-4444-444444444444', 'f1111111-1111-1111-1111-111111111111', 'a5555555-5555-5555-5555-555555555555', '44444444-bbbb-4444-4444-444444444444', 15, NOW(), NULL),
    ('55555555-aaaa-5555-5555-555555555555', 'f1111111-1111-1111-1111-111111111111', 'a7777777-7777-7777-7777-777777777777', '55555555-bbbb-5555-5555-555555555555', 15, NOW(), NULL),

    -- f2 (이벤트 흐름 — 제주 여행) PULL/PUT
    ('aaaa3333-3333-3333-3333-333333333333', 'f2222222-2222-2222-2222-222222222222', 'a1111111-1111-1111-1111-111111111111', NULL, NULL, NOW(), NULL),
    ('66666666-aaaa-6666-6666-666666666666', 'f2222222-2222-2222-2222-222222222222', 'a6666666-6666-6666-6666-666666666666', '66666666-bbbb-6666-6666-666666666666', 100, NOW(), NULL);

    -- =========================================================
    -- 7. TRANSACTIONS (이번 달, 총 2,449,500)
    --    카테고리별 가맹점 분산해서 sub 필드 ("배달의민족 외 N건") 가 의미있게 나오도록
    -- =========================================================
    INSERT INTO transactions (id, user_id, asset_id, amount, category, sender_name, transaction_at)
    SELECT gen_random_uuid(), v_user, 'a1111111-1111-1111-1111-111111111111', amount, category, sender, txn_at
    FROM (VALUES
        -- 식비 (1,029,000 / 5건 → "배달의민족 외 4건")
        (-450000, '식비',       '배달의민족',  date_trunc('month', NOW()) + INTERVAL '1 day'),
        (-180000, '식비',       '배달의민족',  date_trunc('month', NOW()) + INTERVAL '4 day'),
        (-150000, '식비',       '스타벅스',    date_trunc('month', NOW()) + INTERVAL '8 day'),
        (-149000, '식비',       '김밥천국',    date_trunc('month', NOW()) + INTERVAL '12 day'),
        (-100000, '식비',       '쿠팡이츠',    date_trunc('month', NOW()) + INTERVAL '15 day'),
        -- 문화/여가 (441,000 / 3건)
        (-241000, '문화/여가',  'CGV',         date_trunc('month', NOW()) + INTERVAL '6 day'),
        (-120000, '문화/여가',  '인터파크',    date_trunc('month', NOW()) + INTERVAL '11 day'),
        (-80000,  '문화/여가',  'YES24',       date_trunc('month', NOW()) + INTERVAL '18 day'),
        -- 온라인쇼핑 (514,500 / 4건)
        (-260000, '온라인쇼핑', '쿠팡',        date_trunc('month', NOW()) + INTERVAL '2 day'),
        (-150000, '온라인쇼핑', '쿠팡',        date_trunc('month', NOW()) + INTERVAL '9 day'),
        (-65000,  '온라인쇼핑', '11번가',      date_trunc('month', NOW()) + INTERVAL '14 day'),
        (-39500,  '온라인쇼핑', 'G마켓',       date_trunc('month', NOW()) + INTERVAL '20 day'),
        -- 교통 (196,000 / 2건)
        (-150000, '교통',       '카카오T',     date_trunc('month', NOW()) + INTERVAL '3 day'),
        (-46000,  '교통',       '티머니',      date_trunc('month', NOW()) + INTERVAL '13 day'),
        -- 기타 (269,500 / 3건)
        (-150000, '기타',       'GS25',        date_trunc('month', NOW()) + INTERVAL '7 day'),
        (-70000,  '기타',       'CU',          date_trunc('month', NOW()) + INTERVAL '16 day'),
        (-49500,  '기타',       '올리브영',    date_trunc('month', NOW()) + INTERVAL '21 day')
    ) AS t(amount, category, sender, txn_at);
END $$;

-- =========================================================
-- 8. 확인
-- =========================================================
SELECT 'users'                AS table_name, COUNT(*) AS cnt FROM users                WHERE email   = 'dashboard@wooriport.com'
UNION ALL SELECT 'assets',                    COUNT(*) FROM assets                    WHERE user_id = (SELECT id FROM users WHERE email = 'dashboard@wooriport.com')
UNION ALL SELECT 'portfolios',                COUNT(*) FROM portfolios                WHERE user_id = (SELECT id FROM users WHERE email = 'dashboard@wooriport.com')
UNION ALL SELECT 'portfolio_flows',           COUNT(*) FROM portfolio_flows           WHERE user_id = (SELECT id FROM users WHERE email = 'dashboard@wooriport.com')
UNION ALL SELECT 'portfolio_flow_items',      COUNT(*) FROM portfolio_flow_items
    WHERE flow_id IN (SELECT id FROM portfolio_flows WHERE user_id = (SELECT id FROM users WHERE email = 'dashboard@wooriport.com'))
UNION ALL SELECT 'event',                     COUNT(*) FROM event                     WHERE user_id = (SELECT id FROM users WHERE email = 'dashboard@wooriport.com')
UNION ALL SELECT 'transactions',              COUNT(*) FROM transactions              WHERE user_id = (SELECT id FROM users WHERE email = 'dashboard@wooriport.com')
UNION ALL SELECT 'product_category_rates',    COUNT(*) FROM product_category_rates
UNION ALL SELECT 'products',                  COUNT(*) FROM products;
