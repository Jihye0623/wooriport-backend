-- ============================================================
-- dashboard_seed.sql  ─  Dashboard 화면 테스트용 더미 데이터
-- 대상: report-test@wooriport.com
--
-- 실행 순서:
-- 1. report_seed.sql 먼저 실행 (users/assets/transactions 생성)
-- 2. 이 SQL 실행
-- 3. Swagger: POST /api/v1/auth/login { email: "report-test@wooriport.com", password: "Test1234!" }
-- 4. GET /api/v1/dashboard
--
-- 재실행 가능: 멱등성 보장
-- ============================================================

-- ──────────────────────────────────────────────────────────
-- GLOBAL. product_category_rates (포트폴리오 수익률 라벨)
-- ──────────────────────────────────────────────────────────
INSERT INTO product_category_rates (id, category_label, rate) VALUES
    (gen_random_uuid(), 'ETF',  '+4.2%'),
    (gen_random_uuid(), '현금성', '+2.1%'),
    (gen_random_uuid(), '적금',   '+3.5%'),
    (gen_random_uuid(), 'IRP',  '+1.8%')
ON CONFLICT (category_label) DO UPDATE
    SET rate = EXCLUDED.rate;

-- ──────────────────────────────────────────────────────────
-- GLOBAL. products (portfolioBreakdown ticker 조회용)
-- ──────────────────────────────────────────────────────────
INSERT INTO products (id, product_type, institution, name, ticker, interest_rate, description, updated_at, created_at) VALUES
    ('cc111111-1111-1111-1111-111111111111', 'ETF',  'KODEX', 'KODEX 200', '069500.KS', 2.9,  'KOSPI200 추종 ETF', NOW(), NOW()),
    ('cc222222-2222-2222-2222-222222222222', 'BOND', '삼성자산', '국채 3년', '148070.KS', -1.1, '국채 3년물 ETF',    NOW(), NOW())
ON CONFLICT (id) DO UPDATE
    SET ticker = EXCLUDED.ticker, updated_at = EXCLUDED.updated_at;

-- ──────────────────────────────────────────────────────────
-- 사용자별 시드
-- ──────────────────────────────────────────────────────────
DO $$
DECLARE
    v_user    UUID;
    v_a_check UUID;
    v_a_irp   UUID;
    v_a_pen   UUID;
BEGIN
    SELECT id INTO v_user FROM users WHERE email = 'report-test@wooriport.com';
    IF v_user IS NULL THEN
        RAISE EXCEPTION '⚠ report-test@wooriport.com 없음. report_seed.sql 먼저 실행하세요.';
    END IF;

    SELECT id INTO v_a_check FROM assets WHERE user_id = v_user AND asset_type = 'CHECKING'        AND deleted_at IS NULL LIMIT 1;
    SELECT id INTO v_a_irp   FROM assets WHERE user_id = v_user AND asset_type = 'IRP'             AND deleted_at IS NULL LIMIT 1;
    SELECT id INTO v_a_pen   FROM assets WHERE user_id = v_user AND asset_type = 'PENSION_SAVINGS' AND deleted_at IS NULL LIMIT 1;

    -- 정리
    DELETE FROM portfolios WHERE user_id = v_user;

    -- portfolios (월급 분배 계획)
    --   salary=5,650,000 / investmentAmount=850,000
    --   allocations: 생활비 2,500,000 / IRP 300,000 / 연금 150,000
    --   surplus = 5,650,000 - 2,950,000 - 850,000 = 1,850,000
    INSERT INTO portfolios (id, user_id, asset_amount, asset_id, created_at) VALUES
        (gen_random_uuid(), v_user, 2500000, v_a_check, NOW()),
        (gen_random_uuid(), v_user,  300000, v_a_irp,   NOW()),
        (gen_random_uuid(), v_user,  150000, v_a_pen,   NOW());

    RAISE NOTICE '✅ dashboard_seed 완료 (report-test@wooriport.com)';
    RAISE NOTICE '  assetsSummary:';
    RAISE NOTICE '    cash   = CHECKING 6,800,000';
    RAISE NOTICE '    invest = STOCK 8,500,000 + IRP 5,500,000 = 14,000,000';
    RAISE NOTICE '    total  = 20,800,000';
    RAISE NOTICE '  salaryPlan:';
    RAISE NOTICE '    income=5,650,000 / invest=850,000 / surplus=1,850,000';
    RAISE NOTICE '    allocations: 생활비 2,500,000 / IRP 300,000 / 연금 150,000';
    RAISE NOTICE '  consumption: 이번달 지출 = report_seed 5월 transactions 기준';
    RAISE NOTICE '  portfolio: ETF(+4.2%%) / IRP(+1.8%%)';
END $$;

-- 확인
SELECT tbl, cnt FROM (
    SELECT 'portfolios'           AS tbl, COUNT(*) AS cnt FROM portfolios           WHERE user_id = (SELECT id FROM users WHERE email = 'report-test@wooriport.com' LIMIT 1)
    UNION ALL
    SELECT 'assets',                       COUNT(*) FROM assets                     WHERE user_id = (SELECT id FROM users WHERE email = 'report-test@wooriport.com' LIMIT 1) AND deleted_at IS NULL
    UNION ALL
    SELECT 'transactions (5월)',            COUNT(*) FROM transactions               WHERE user_id = (SELECT id FROM users WHERE email = 'report-test@wooriport.com' LIMIT 1)
      AND transaction_at >= '2026-05-01' AND transaction_at < '2026-06-01'
    UNION ALL
    SELECT 'product_category_rates',       COUNT(*) FROM product_category_rates
) t;
