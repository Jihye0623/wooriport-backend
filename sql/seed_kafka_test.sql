-- ─────────────────────────────────────────────
-- Kafka 실험용 시드 (2026 스키마 기준)
-- 유저 3명 + 각자 CREDIT_CARD 자산 1개.
-- mock_payment.py 가 assets(asset_type='CREDIT_CARD')의 asset_number 를 읽어
-- transaction-events 토픽으로 발행하는 데 사용.
-- (구버전 dummy_kafka_test.sql 은 phone/finance_type 등 사라진 컬럼 참조로 깨짐 → 이 파일로 대체)
-- ─────────────────────────────────────────────

INSERT INTO users (
    id, email, password, name, status, salary_date, auto_transfer_to_asset_id, created_at
) VALUES
('11111111-1111-1111-1111-111111111111', 'kafka.test1@example.com',
 '$2a$12$ij.wsUKNJMAMKlCkTzuoIu8osBmhIsCcn3R70cU7v6LwdN8wgPzKO', '카프카테스트1', 'ACTIVE', 25, NULL, NOW()),
('22222222-2222-2222-2222-222222222222', 'kafka.test2@example.com',
 '$2a$12$ij.wsUKNJMAMKlCkTzuoIu8osBmhIsCcn3R70cU7v6LwdN8wgPzKO', '카프카테스트2', 'ACTIVE', 15, NULL, NOW()),
('33333333-3333-3333-3333-333333333333', 'kafka.test3@example.com',
 '$2a$12$ij.wsUKNJMAMKlCkTzuoIu8osBmhIsCcn3R70cU7v6LwdN8wgPzKO', '카프카테스트3', 'ACTIVE', 5, NULL, NOW())
ON CONFLICT (id) DO NOTHING;

INSERT INTO assets (
    id, user_id, institution, asset_number, asset_type,
    account_name, account_purpose, is_salary, balance, synced_at, bank_type, created_at
) VALUES
('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1', '11111111-1111-1111-1111-111111111111',
 '삼성카드', '5429-4494-5284-1111', 'CREDIT_CARD', '삼성 iD VISA', '주거래 신용카드', false, 0, NOW(), 'OTHER', NOW()),
('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa2', '22222222-2222-2222-2222-222222222222',
 '현대카드', '4321-8765-1234-2222', 'CREDIT_CARD', '현대카드 M', '주거래 신용카드', false, 0, NOW(), 'OTHER', NOW()),
('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa3', '33333333-3333-3333-3333-333333333333',
 '우리카드', '9876-5432-1098-3333', 'CREDIT_CARD', '우리 카드의정석', '주거래 신용카드', false, 0, NOW(), 'WOORI', NOW())
ON CONFLICT (id) DO NOTHING;
