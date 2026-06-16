-- ============================================================
-- transactions.sql  —  거래내역 더미 (4개월치)
-- 파라미터: {USER_ID}, {SALARY_ASSET_ID}
-- 재실행 시 기존 transactions 삭제 후 재삽입
-- 4개월치인 이유: 4월 리포트의 전달 비교(3월) 데이터까지 커버
-- ============================================================

DELETE FROM transactions WHERE user_id = '{USER_ID}';

-- 급여 (4개월치, 매월 25일경)
INSERT INTO transactions (id, user_id, asset_id, amount, category, sender_name, transaction_at) VALUES
    (gen_random_uuid(), '{USER_ID}', '{SALARY_ASSET_ID}', 4800000, '급여', '우리포트(주)', date_trunc('month', NOW()) - INTERVAL '3 month' + INTERVAL '24 day'),
    (gen_random_uuid(), '{USER_ID}', '{SALARY_ASSET_ID}', 4800000, '급여', '우리포트(주)', date_trunc('month', NOW()) - INTERVAL '2 month' + INTERVAL '24 day'),
    (gen_random_uuid(), '{USER_ID}', '{SALARY_ASSET_ID}', 4800000, '급여', '우리포트(주)', date_trunc('month', NOW()) - INTERVAL '1 month' + INTERVAL '24 day');
-- 변동 지출 — 식비/카페/문화/쇼핑/교통 (4개월치)
INSERT INTO transactions (id, user_id, asset_id, amount, category, sender_name, transaction_at)
SELECT gen_random_uuid(), '{USER_ID}', '{SALARY_ASSET_ID}', amount, category, sender, txn_at
FROM (VALUES
    -- 6월 (현재) — 5월 대비 약 20만원 절약
    (-250000, '식비',       '배달의민족',   date_trunc('month', NOW()) + INTERVAL '1 day'),
    (-120000, '식비',       '쿠팡이츠',     date_trunc('month', NOW()) + INTERVAL '4 day'),
    (-100000, '카페',       '스타벅스',     NOW()),   -- 오늘 거래 — 이번 주 요일별 지출 한 칸 채우기 (월 총액 동일)
    (-160000, '문화/여가',  'CGV',          date_trunc('month', NOW()) + INTERVAL '6 day'),
    (-255000, '온라인쇼핑', '쿠팡',         date_trunc('month', NOW()) + INTERVAL '2 day'),
    (-120000, '교통',       '카카오T',      date_trunc('month', NOW()) + INTERVAL '3 day'),
    ( -30000, '교통',       '티머니',       date_trunc('month', NOW()) + INTERVAL '13 day'),
    -- 5월
    (-420000, '식비',       '배달의민족',   date_trunc('month', NOW()) - INTERVAL '1 month' + INTERVAL '2 day'),
    (-130000, '카페',       '투썸플레이스', date_trunc('month', NOW()) - INTERVAL '1 month' + INTERVAL '5 day'),
    (-200000, '문화/여가',  '인터파크',     date_trunc('month', NOW()) - INTERVAL '1 month' + INTERVAL '9 day'),
    (-310000, '온라인쇼핑', '쿠팡',         date_trunc('month', NOW()) - INTERVAL '1 month' + INTERVAL '12 day'),
    (-180000, '교통',       '카카오T',      date_trunc('month', NOW()) - INTERVAL '1 month' + INTERVAL '14 day'),
    -- 4월
    (-380000, '식비',       '쿠팡이츠',     date_trunc('month', NOW()) - INTERVAL '2 month' + INTERVAL '3 day'),
    (-110000, '카페',       '스타벅스',     date_trunc('month', NOW()) - INTERVAL '2 month' + INTERVAL '6 day'),
    (-220000, '문화/여가',  'YES24',        date_trunc('month', NOW()) - INTERVAL '2 month' + INTERVAL '10 day'),
    (-280000, '온라인쇼핑', '11번가',       date_trunc('month', NOW()) - INTERVAL '2 month' + INTERVAL '13 day'),
    (-160000, '교통',       '티머니',       date_trunc('month', NOW()) - INTERVAL '2 month' + INTERVAL '15 day'),
    -- 3월 (4월 리포트의 전달 비교용)
    (-350000, '식비',       '배달의민족',   date_trunc('month', NOW()) - INTERVAL '3 month' + INTERVAL '2 day'),
    ( -95000, '카페',       '스타벅스',     date_trunc('month', NOW()) - INTERVAL '3 month' + INTERVAL '7 day'),
    (-190000, '문화/여가',  'CGV',          date_trunc('month', NOW()) - INTERVAL '3 month' + INTERVAL '11 day'),
    (-240000, '온라인쇼핑', '쿠팡',         date_trunc('month', NOW()) - INTERVAL '3 month' + INTERVAL '14 day'),
    (-140000, '교통',       '티머니',       date_trunc('month', NOW()) - INTERVAL '3 month' + INTERVAL '16 day')
) AS t(amount, category, sender, txn_at);

-- 고정 지출 — 통신/공과금/보험료 (4개월치)
INSERT INTO transactions (id, user_id, asset_id, amount, category, sender_name, transaction_at)
SELECT gen_random_uuid(), '{USER_ID}', '{SALARY_ASSET_ID}', amount, category, sender, txn_at
FROM (VALUES
    -- 6월
    ( -65000, '통신',   'SKT',      date_trunc('month', NOW()) + INTERVAL '5 day'),
    (-120000, '공과금', '한국전력', date_trunc('month', NOW()) + INTERVAL '10 day'),
    ( -95000, '보험료', '삼성생명', date_trunc('month', NOW()) + INTERVAL '17 day'),
    -- 5월
    ( -65000, '통신',   'SKT',      date_trunc('month', NOW()) - INTERVAL '1 month' + INTERVAL '5 day'),
    (-115000, '공과금', '한국전력', date_trunc('month', NOW()) - INTERVAL '1 month' + INTERVAL '10 day'),
    ( -95000, '보험료', '삼성생명', date_trunc('month', NOW()) - INTERVAL '1 month' + INTERVAL '17 day'),
    -- 4월
    ( -65000, '통신',   'SKT',      date_trunc('month', NOW()) - INTERVAL '2 month' + INTERVAL '5 day'),
    (-118000, '공과금', '한국전력', date_trunc('month', NOW()) - INTERVAL '2 month' + INTERVAL '10 day'),
    ( -95000, '보험료', '삼성생명', date_trunc('month', NOW()) - INTERVAL '2 month' + INTERVAL '17 day'),
    -- 3월
    ( -65000, '통신',   'SKT',      date_trunc('month', NOW()) - INTERVAL '3 month' + INTERVAL '5 day'),
    (-112000, '공과금', '한국전력', date_trunc('month', NOW()) - INTERVAL '3 month' + INTERVAL '10 day'),
    ( -95000, '보험료', '삼성생명', date_trunc('month', NOW()) - INTERVAL '3 month' + INTERVAL '17 day')
) AS t(amount, category, sender, txn_at);
