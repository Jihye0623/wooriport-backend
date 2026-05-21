CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- ─────────────────────────────────────────────
-- 1. 페르소나 1 — 김지수 (결혼 준비, SPARK)
-- email: jisoo.kim@example.com
-- ─────────────────────────────────────────────
INSERT INTO dummy_mydata (email, institution, asset_type, account_name, account_purpose, asset_number, balance, bank_type, is_salary)
VALUES
('jisoo.kim@example.com', '카카오뱅크',  'CHECKING',    '카카오뱅크 입출금',    '급여통장',         '3333-01-2847391',     3200000, 'OTHER', true),
('jisoo.kim@example.com', '우리은행',   'SAVINGS',     '우리 주택청약종합저축', '주택청약종합저축',  '1002-447-829301',     4800000, 'WOORI', false),
('jisoo.kim@example.com', '토스뱅크',   'PARKING',     '토스 파킹통장',        '비상금',           '1000-3829-4715',      2100000, 'OTHER', false),
('jisoo.kim@example.com', '신한은행',   'SAVINGS',     '신한 쏠편한 적금',     '결혼자금 적금',    '110-528-374192',      8400000, 'OTHER', false),
('jisoo.kim@example.com', '삼성카드',   'CREDIT_CARD', '삼성 iD VISA',        '주거래 신용카드',  '5429-4494-5284-1827', 0,       'OTHER', false);

-- ─────────────────────────────────────────────
-- 2. users INSERT — 김지수
-- ─────────────────────────────────────────────
INSERT INTO users (
    id, email, password, name, phone,
    status, finance_type, salary_date,
    auto_transfer_to_asset_id, created_at
)
VALUES (
    '45b8a9ba-76c8-46b1-9b65-390e82728d32',
    'jisoo.kim@example.com',
    '$2a$12$ij.wsUKNJMAMKlCkTzuoIu8osBmhIsCcn3R70cU7v6LwdN8wgPzKO',
    '김지수',
    '010-2847-3915',
    'ACTIVE',
    'SPARK',
    25,
    NULL,
    '2024-01-15 10:23:41'
);

-- ─────────────────────────────────────────────
-- 3. 확인 쿼리
-- ─────────────────────────────────────────────
SELECT '=== dummy_mydata ===' AS info;
SELECT email, institution, asset_type, account_purpose, balance, is_salary
FROM dummy_mydata
ORDER BY email, is_salary DESC;

SELECT '=== users ===' AS info;
SELECT id, email, name, finance_type, salary_date FROM users;
