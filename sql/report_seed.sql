-- ============================================================
-- report_seed.sql  ─  Monthly Report 화면 테스트용 더미 데이터
-- 대상: report-test@wooriport.com
-- 목표 리포트: 2026년 5월  (GET /api/v1/reports/2026/5)
--
-- 실행 순서:
-- 1. Spring Boot 앱 실행
-- 2. Swagger: POST /api/v1/auth/signup
--      { "email": "report-test@wooriport.com",
--        "password": "Test1234!",
--        "name": "리포트테스터" }
-- 3. 이 SQL 실행
-- 4. Swagger: POST /api/v1/auth/login  → JWT 발급
-- 5. GET /api/v1/reports/2026/5 로 확인
--
-- 재실행 가능: DELETE 후 INSERT 패턴으로 멱등성 보장
-- ============================================================

-- ──────────────────────────────────────────────────────────
-- PART 0. 글로벌 시드: ETF/BOND 상품 (ticker 필수)
--   portfolioBreakdown → YahooFinanceService.getMonthlyChangeRate(ticker)
-- ──────────────────────────────────────────────────────────
INSERT INTO products (id, product_type, institution, name, ticker, interest_rate, description, updated_at, created_at) VALUES
    ('cc111111-1111-1111-1111-111111111111', 'ETF',  'KODEX',    'KODEX 200', '069500.KS', 2.9,  'KOSPI200 추종 ETF', NOW(), NOW()),
    ('cc222222-2222-2222-2222-222222222222', 'BOND', '삼성자산', '국채 3년',  '148070.KS', -1.1, '국채 3년물 ETF',    NOW(), NOW())
ON CONFLICT (id) DO UPDATE
    SET ticker     = EXCLUDED.ticker,
        updated_at = EXCLUDED.updated_at;

-- ──────────────────────────────────────────────────────────
-- PART 1. 사용자별 시드 (DO 블록)
-- ──────────────────────────────────────────────────────────
DO $$
DECLARE
    v_user    UUID;
    v_a_check UUID;   -- 급여·체크 통장 (CHECKING)
    v_a_stock UUID;   -- 증권 계좌     (STOCK)
    v_a_irp   UUID;   -- IRP          (세제혜택)
    v_a_pen   UUID;   -- 연금저축      (세제혜택)
    v_flow    UUID;   -- portfolio_flow
    v_report  UUID;
BEGIN
    -- ── 1. 사용자 확인 ──────────────────────────────────────
    SELECT id INTO v_user FROM users WHERE email = 'report-test@wooriport.com';
    IF v_user IS NULL THEN
        RAISE EXCEPTION '⚠ report-test@wooriport.com 사용자가 없습니다.'
            ' Swagger에서 먼저 POST /api/v1/auth/signup 하세요.';
    END IF;

    -- 급여 정보 업데이트
    --   salary × 12 = 67,800,000 > 55,000,000 → 세액공제율 13.2% (LOW_RATE)
    UPDATE users
    SET salary = 5650000, salary_date = 25, monthly_invest_amount = 850000
    WHERE id = v_user;

    RAISE NOTICE '[1] 사용자 확인 완료 — id: %', v_user;

    -- ── 2. 기존 시드 정리 (재실행 안전) ─────────────────────
    DELETE FROM report_category_expenses
      WHERE report_id IN (SELECT id FROM reports WHERE user_id = v_user);
    DELETE FROM reports              WHERE user_id = v_user;
    DELETE FROM mini_challenges      WHERE user_id = v_user;
    DELETE FROM asset_snapshots      WHERE user_id = v_user;
    DELETE FROM transactions         WHERE user_id = v_user;
    DELETE FROM portfolio_flow_items
      WHERE flow_id IN (SELECT id FROM portfolio_flows WHERE user_id = v_user);
    DELETE FROM portfolio_flows      WHERE user_id = v_user;
    DELETE FROM assets               WHERE user_id = v_user;

    RAISE NOTICE '[2] 기존 데이터 정리 완료';

    -- ── 3. 자산 4개 ─────────────────────────────────────────
    --  a_check : CHECKING   — 급여 수취·지출 발생 계좌 (is_salary=TRUE)
    --  a_stock : STOCK      — 투자 수익 수취·포트폴리오 매핑 계좌
    --  a_irp   : IRP        — 잔액 5,500,000 → irpDeductible = min(5.5M, 9M-4M) = 5,000,000
    --  a_pen   : PENSION    — 잔액 4,000,000 → penDeductible = min(4M,   6M)     = 4,000,000
    INSERT INTO assets
        (id, user_id, institution, asset_type, account_name, account_purpose,
         is_salary, balance, synced_at, bank_type, created_at)
    VALUES
        (gen_random_uuid(), v_user, '우리은행',  'CHECKING',       '우리 WON 통장', '생활비', TRUE,  6800000, NOW(), 'WOORI', NOW()),
        (gen_random_uuid(), v_user, '미래에셋',  'STOCK',          'MTS 투자계좌',  '투자',  FALSE, 8500000, NOW(), 'OTHER', NOW()),
        (gen_random_uuid(), v_user, '미래에셋',  'IRP',            '미래에셋 IRP',  '노후',  FALSE, 5500000, NOW(), 'OTHER', NOW()),
        (gen_random_uuid(), v_user, '한국투자',  'PENSION_SAVINGS', '연금저축펀드', '노후',  FALSE, 4000000, NOW(), 'OTHER', NOW());

    SELECT id INTO v_a_check FROM assets WHERE user_id = v_user AND asset_type = 'CHECKING'       AND deleted_at IS NULL LIMIT 1;
    SELECT id INTO v_a_stock FROM assets WHERE user_id = v_user AND asset_type = 'STOCK'          AND deleted_at IS NULL LIMIT 1;
    SELECT id INTO v_a_irp   FROM assets WHERE user_id = v_user AND asset_type = 'IRP'            AND deleted_at IS NULL LIMIT 1;
    SELECT id INTO v_a_pen   FROM assets WHERE user_id = v_user AND asset_type = 'PENSION_SAVINGS' AND deleted_at IS NULL LIMIT 1;

    RAISE NOTICE '[3] 자산 4개 생성 완료';

    -- ── 4. asset_snapshots ─────────────────────────────────
    -- getReport() 에서 assetChangeRate 계산:
    --   prevSnapshot = findLastBefore(userId, '2026-05-01') → 4/28 총자산 17,500,000
    --   currLast     = findByUserIdAndMonth(...5월...) 마지막 → 5/26 총자산 18,500,000
    --   rate = round((18500000-17500000)*1000.0/17500000)/10.0
    --        = round(57.14)/10.0 = 57/10.0 = 5.7  ✓
    INSERT INTO asset_snapshots
        (id, user_id, snapshot_at, total_amount, savings_amount, invest_amount, created_at)
    VALUES
        (gen_random_uuid(), v_user, '2026-04-28 00:00:00', 17500000,  9500000, 8000000, NOW()),
        (gen_random_uuid(), v_user, '2026-05-05 00:00:00', 18000000,  9700000, 8300000, NOW()),
        (gen_random_uuid(), v_user, '2026-05-12 00:00:00', 18200000,  9800000, 8400000, NOW()),
        (gen_random_uuid(), v_user, '2026-05-19 00:00:00', 18350000,  9900000, 8450000, NOW()),
        (gen_random_uuid(), v_user, '2026-05-26 00:00:00', 18500000, 10000000, 8500000, NOW());

    RAISE NOTICE '[4] asset_snapshots 5건 생성 완료';

    -- ── 5. transactions — 5월 (리포트 대상월) ───────────────
    -- 수입: 급여 5,500,000 + 투자수익 150,000 → totalIncome = 5,650,000
    -- 지출: 교통 8,000(1주) + 쇼핑 95,000(2주) + 카페 19,500(2주) + 식비 27,000(2주)
    --       → totalExpense = 149,500
    -- IRP/연금 납입은 양수(입금) → sumTaxBenefitContributionByMonth 집계 대상
    INSERT INTO transactions
        (id, user_id, asset_id, amount, category, sender_name, transaction_at)
    VALUES
        -- 수입
        (gen_random_uuid(), v_user, v_a_check, 5500000, '급여',        '우리포트(주)',  '2026-05-25 09:00:00'),
        (gen_random_uuid(), v_user, v_a_stock,  150000, '투자수익',    '미래에셋증권', '2026-05-15 10:00:00'),
        -- 지출 (week 1 = day 1-7)
        (gen_random_uuid(), v_user, v_a_check,   -8000, '교통',        '티머니',       '2026-05-03 08:30:00'),
        -- 지출 (week 2 = day 8-14)
        (gen_random_uuid(), v_user, v_a_check,  -65000, '쇼핑',        '쿠팡',         '2026-05-09 14:00:00'),
        (gen_random_uuid(), v_user, v_a_check,  -30000, '쇼핑',        '올리브영',     '2026-05-10 16:00:00'),
        (gen_random_uuid(), v_user, v_a_check,  -19500, '카페',        '스타벅스',     '2026-05-12 09:00:00'),
        (gen_random_uuid(), v_user, v_a_check,  -27000, '식비',        '배달의민족',   '2026-05-14 19:00:00'),
        -- IRP/연금 납입 (양수 = 해당 계좌 입금 → 세제혜택 contribution 집계)
        (gen_random_uuid(), v_user, v_a_irp,    300000, 'IRP납입',     '이체',         '2026-05-20 10:00:00'),
        (gen_random_uuid(), v_user, v_a_pen,    150000, '연금저축납입', '이체',         '2026-05-20 10:05:00');

    RAISE NOTICE '[5] 5월 transactions 9건 생성 완료';

    -- ── 6. transactions — 4월 (전달 누적 지출 비교용) ────────
    -- weeklyExpenses JSON의 prevCumulative 원본 데이터
    -- week1: 카페 5,000    → prev cumul 5,000
    -- week2: 식비+쇼핑 50,000 → prev cumul 55,000
    -- week3: 교통 7,000    → prev cumul 62,000
    -- week4,5: 없음        → prev cumul 62,000
    INSERT INTO transactions
        (id, user_id, asset_id, amount, category, sender_name, transaction_at)
    VALUES
        (gen_random_uuid(), v_user, v_a_check, 5500000, '급여',  '우리포트(주)', '2026-04-25 09:00:00'),
        (gen_random_uuid(), v_user, v_a_check,   -5000, '카페',  '스타벅스',    '2026-04-03 09:00:00'),
        (gen_random_uuid(), v_user, v_a_check,  -20000, '식비',  '배달의민족',  '2026-04-10 19:00:00'),
        (gen_random_uuid(), v_user, v_a_check,  -30000, '쇼핑',  '쿠팡',        '2026-04-12 14:00:00'),
        (gen_random_uuid(), v_user, v_a_check,   -7000, '교통',  '티머니',      '2026-04-16 08:30:00');

    RAISE NOTICE '[6] 4월 transactions 5건 생성 완료';

    -- ── 7. mini_challenges (5월에 완료된 챌린지) ─────────────
    -- getReport() → findByUserIdAndCompletedAtMonth(userId, from='2026-05-01', to='2026-06-01')
    INSERT INTO mini_challenges (
        id, user_id, title, description, category,
        challenge_type, challenge_sub_type, target, status,
        current_value, notified_threshold,
        started_at, completed_at, created_at
    ) VALUES (
        gen_random_uuid(), v_user,
        '이번 달 카페 지출 50,000원 이내로 줄이기',
        '카페 지출을 월 50,000원 이하로 관리하세요',
        '카페', 'AMOUNT', 'COFFEE', 50000, 'COMPLETED',
        19500, 0,
        '2026-05-01 00:00:00', '2026-05-27 00:00:00', NOW()
    );

    RAISE NOTICE '[7] mini_challenges 1건 생성 완료';

    -- ── 8. portfolio_flows + portfolio_flow_items ─────────────
    -- buildPortfolioBreakdown() → findAllPutByUserIdWithAsset
    --   WHERE product_ratio IS NOT NULL
    --   → product.ticker 로 YahooFinance 조회
    INSERT INTO portfolio_flows
        (id, user_id, title, summary, term, amount, is_active, started_at, created_at)
    VALUES
        (gen_random_uuid(), v_user, '기본 포트폴리오', '안정 성장형 포트폴리오', '장', 850000, TRUE, NOW(), NOW())
    RETURNING id INTO v_flow;

    INSERT INTO portfolio_flow_items
        (id, flow_id, asset_id, product_id, product_ratio, created_at)
    VALUES
        (gen_random_uuid(), v_flow, v_a_stock, 'cc111111-1111-1111-1111-111111111111', 60, NOW()),
        (gen_random_uuid(), v_flow, v_a_stock, 'cc222222-2222-2222-2222-222222222222', 40, NOW());

    RAISE NOTICE '[8] portfolio_flows / portfolio_flow_items 생성 완료';

    -- ── 9. reports (2026년 5월) ───────────────────────────────
    -- 수입/지출/잉여 — 배치 없이 직접 계산값 삽입
    --   totalIncome  = 5,500,000(급여) + 150,000(투자수익) = 5,650,000
    --   totalExpense = 8,000 + 95,000 + 19,500 + 27,000   = 149,500
    --   surplus      = 5,650,000 - 149,500                 = 5,500,500
    --
    -- asset_snapshots_json  → getReport() 에서 JSON 파싱 후 AssetSnapshot DTO 반환
    -- weekly_expenses_json  → getReport() 에서 JSON 파싱 후 WeeklyExpenseSnapshot DTO 반환
    --
    -- weekly_expenses 계산 (배치 로직 시뮬레이션):
    --   5월 지출: 교통 -8,000 (day3=week1), 쇼핑+카페+식비 -141,500 (day9~14=week2)
    --   4월 지출: 카페 -5,000 (day3=week1), 식비+쇼핑 -50,000 (day10~12=week2), 교통 -7,000 (day16=week3)
    --
    --   week | curr cumul | prev cumul
    --     1  |      8,000 |     5,000
    --     2  |    149,500 |    55,000
    --     3  |    149,500 |    62,000
    --     4  |    149,500 |    62,000
    --     5  |    149,500 |    62,000
    INSERT INTO reports (
        id, user_id, year, month,
        total_income, total_expense, surplus,
        prev_total_amount, curr_total_amount,
        prev_savings_amount, curr_savings_amount,
        prev_invest_amount,  curr_invest_amount,
        portfolio_comment, event_comment,
        market_summary, next_month_guideline,
        asset_snapshots_json, weekly_expenses_json,
        created_at
    ) VALUES (
        gen_random_uuid(), v_user, 2026, 5,
        5650000, 149500, 5500500,
        17500000, 18500000,
         9500000, 10000000,
         8000000,  8500000,
        '전월 대비 총 자산이 500,000원 증가했으며, 투자 자산이 500,000원 상승한 반면 저축은 변동이 없었습니다.',
        '카페 지출을 50,000원 이하로 줄이는 미니 챌린지를 성공적으로 달성했습니다.',
        '이번 달 수입이 주로 급여와 투자 수익으로 구성되어 자산이 꾸준히 증가했습니다.',
        '다음 달에는 투자 수익을 활용해 저축 비중을 10% 이상으로 늘리는 것을 목표로 하세요.',
        '[{"snapshotDate":"2026-05-05","totalAmount":18000000},{"snapshotDate":"2026-05-12","totalAmount":18200000},{"snapshotDate":"2026-05-19","totalAmount":18350000},{"snapshotDate":"2026-05-26","totalAmount":18500000}]',
        '[{"week":1,"currCumulative":8000,"prevCumulative":5000},{"week":2,"currCumulative":149500,"prevCumulative":55000},{"week":3,"currCumulative":149500,"prevCumulative":62000},{"week":4,"currCumulative":149500,"prevCumulative":62000},{"week":5,"currCumulative":149500,"prevCumulative":62000}]',
        NOW()
    )
    RETURNING id INTO v_report;

    RAISE NOTICE '[9] reports 생성 완료 — report_id: %', v_report;

    -- ── 10. report_category_expenses ────────────────────────
    -- 비율: 교통 8,000/149,500=5% / 쇼핑 95,000/149,500=63% / 카페 19,500/149,500=13% / 식비 27,000/149,500=18%
    INSERT INTO report_category_expenses
        (id, report_id, category, amount, prev_amount, ratio, hover_comment, created_at)
    VALUES
        (gen_random_uuid(), v_report, '교통',  8000,  7000,  5,  '교통 지출이 8,000원으로 적절히 관리되었습니다.',           NOW()),
        (gen_random_uuid(), v_report, '쇼핑',  95000, 30000, 63, '쇼핑 지출이 95,000원으로 소폭 증가했습니다.',             NOW()),
        (gen_random_uuid(), v_report, '카페',  19500, 5000,  13, '카페 지출이 19,500원으로 미니 챌린지 목표를 달성했습니다.', NOW()),
        (gen_random_uuid(), v_report, '식비',  27000, 20000, 18, '식비 지출이 27,000원으로 안정적이었습니다.',               NOW());

    RAISE NOTICE '[10] report_category_expenses 4건 생성 완료';

    RAISE NOTICE '════════════════════════════════════════════════';
    RAISE NOTICE '✅ report_seed 완료';
    RAISE NOTICE '   user_id   : %', v_user;
    RAISE NOTICE '   report_id : %', v_report;
    RAISE NOTICE '';
    RAISE NOTICE '세제혜택 예상값 (rate=13.2%%):';
    RAISE NOTICE '   irpContribution        = 300,000';
    RAISE NOTICE '   irpCumulativeDeduction = 660,000  (5,000,000 × 0.132)';
    RAISE NOTICE '   pensionContribution    = 150,000';
    RAISE NOTICE '   penCumulativeDeduction = 528,000  (4,000,000 × 0.132)';
    RAISE NOTICE '   totalTaxSavings        = 1,188,000';
    RAISE NOTICE '';
    RAISE NOTICE '👉 Swagger 로그인 후 GET /api/v1/reports/2026/5';
    RAISE NOTICE '════════════════════════════════════════════════';
END $$;

-- ──────────────────────────────────────────────────────────
-- 확인 쿼리
-- ──────────────────────────────────────────────────────────
SELECT '─── 시드 현황 ───' AS section;

SELECT 'reports'                 AS tbl, COUNT(*) AS cnt
  FROM reports
 WHERE user_id = (SELECT id FROM users WHERE email = 'report-test@wooriport.com' LIMIT 1)
UNION ALL
SELECT 'report_category_expenses',      COUNT(*)
  FROM report_category_expenses
 WHERE report_id IN (
       SELECT id FROM reports
        WHERE user_id = (SELECT id FROM users WHERE email = 'report-test@wooriport.com' LIMIT 1))
UNION ALL
SELECT 'asset_snapshots',               COUNT(*)
  FROM asset_snapshots
 WHERE user_id = (SELECT id FROM users WHERE email = 'report-test@wooriport.com' LIMIT 1)
UNION ALL
SELECT 'transactions (5월)',             COUNT(*)
  FROM transactions
 WHERE user_id = (SELECT id FROM users WHERE email = 'report-test@wooriport.com' LIMIT 1)
   AND transaction_at >= '2026-05-01'
   AND transaction_at <  '2026-06-01'
UNION ALL
SELECT 'transactions (4월)',             COUNT(*)
  FROM transactions
 WHERE user_id = (SELECT id FROM users WHERE email = 'report-test@wooriport.com' LIMIT 1)
   AND transaction_at >= '2026-04-01'
   AND transaction_at <  '2026-05-01'
UNION ALL
SELECT 'mini_challenges',               COUNT(*)
  FROM mini_challenges
 WHERE user_id = (SELECT id FROM users WHERE email = 'report-test@wooriport.com' LIMIT 1)
UNION ALL
SELECT 'portfolio_flow_items',           COUNT(*)
  FROM portfolio_flow_items
 WHERE flow_id IN (
       SELECT id FROM portfolio_flows
        WHERE user_id = (SELECT id FROM users WHERE email = 'report-test@wooriport.com' LIMIT 1))
UNION ALL
SELECT 'assets',                         COUNT(*)
  FROM assets
 WHERE user_id = (SELECT id FROM users WHERE email = 'report-test@wooriport.com' LIMIT 1)
   AND deleted_at IS NULL;
