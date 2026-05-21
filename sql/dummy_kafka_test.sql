-- ─────────────────────────────────────────────
-- 카프카 테스트용 더미 데이터
-- 유저 3명 (카프카테스트1~3) + 각자 CREDIT_CARD 자산 1개
--
-- 용도: mock-server/mock_payment.py 가 assets 테이블에서
--      asset_type='CREDIT_CARD' 인 asset_number 를 읽어
--      Kafka 'transaction-events' 토픽으로 발행할 때 사용
-- ─────────────────────────────────────────────

-- 1. users
INSERT INTO users (
    id, email, password, name, phone,
    status, finance_type, salary_date,
    auto_transfer_to_asset_id, created_at
) VALUES
(
    '11111111-1111-1111-1111-111111111111',
    'kafka.test1@example.com',
    '$2a$12$ij.wsUKNJMAMKlCkTzuoIu8osBmhIsCcn3R70cU7v6LwdN8wgPzKO',
    '카프카테스트1',
    '010-1111-1111',
    'ACTIVE', 'SPARK', 25, NULL, NOW()
),
(
    '22222222-2222-2222-2222-222222222222',
    'kafka.test2@example.com',
    '$2a$12$ij.wsUKNJMAMKlCkTzuoIu8osBmhIsCcn3R70cU7v6LwdN8wgPzKO',
    '카프카테스트2',
    '010-2222-2222',
    'ACTIVE', 'ROCKET', 15, NULL, NOW()
),
(
    '33333333-3333-3333-3333-333333333333',
    'kafka.test3@example.com',
    '$2a$12$ij.wsUKNJMAMKlCkTzuoIu8osBmhIsCcn3R70cU7v6LwdN8wgPzKO',
    '카프카테스트3',
    '010-3333-3333',
    'ACTIVE', 'TURTLE', 5, NULL, NOW()
)
ON CONFLICT (id) DO NOTHING;

-- 2. assets (사람당 CREDIT_CARD 1개)
INSERT INTO assets (
    id, user_id, institution, asset_number, asset_type,
    account_name, account_purpose, is_salary,
    balance, synced_at, bank_type, created_at
) VALUES
(
    'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1',
    '11111111-1111-1111-1111-111111111111',
    '삼성카드', '5429-4494-5284-1111', 'CREDIT_CARD',
    '삼성 iD VISA', '주거래 신용카드', false,
    0, NOW(), 'OTHER', NOW()
),
(
    'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa2',
    '22222222-2222-2222-2222-222222222222',
    '현대카드', '4321-8765-1234-2222', 'CREDIT_CARD',
    '현대카드 M', '주거래 신용카드', false,
    0, NOW(), 'OTHER', NOW()
),
(
    'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa3',
    '33333333-3333-3333-3333-333333333333',
    '우리카드', '9876-5432-1098-3333', 'CREDIT_CARD',
    '우리 카드의정석', '주거래 신용카드', false,
    0, NOW(), 'WOORI', NOW()
)
ON CONFLICT (id) DO NOTHING;

-- 3. 확인 쿼리
SELECT '=== kafka test users & credit cards ===' AS info;
SELECT u.name, a.institution, a.asset_type, a.asset_number, a.account_name
FROM users u
         JOIN assets a ON a.user_id = u.id
WHERE u.name LIKE '카프카테스트%'
ORDER BY u.name;
