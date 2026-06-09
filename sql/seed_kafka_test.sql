-- ─────────────────────────────────────────────
-- Kafka 실험용 시드 (2026 스키마 기준)
-- 유저 18명 + 각자 CREDIT_CARD 자산 1개 (총 카드 18장).
-- partitions=6 환경에서 파티션당 카드 3장 보장 → 균등 분산.
-- mock_payment.py 가 assets(asset_type='CREDIT_CARD')의 asset_number 를 읽어
-- transaction-events 토픽으로 발행하는 데 사용.
-- ─────────────────────────────────────────────

INSERT INTO users (
    id, email, password, name, status, salary_date, auto_transfer_to_asset_id, created_at
) VALUES
('11111111-1111-1111-1111-111111111111', 'kafka.test1@example.com',
 '$2a$12$ij.wsUKNJMAMKlCkTzuoIu8osBmhIsCcn3R70cU7v6LwdN8wgPzKO', '카프카테스트1',  'ACTIVE', 25, NULL, NOW()),
('22222222-2222-2222-2222-222222222222', 'kafka.test2@example.com',
 '$2a$12$ij.wsUKNJMAMKlCkTzuoIu8osBmhIsCcn3R70cU7v6LwdN8wgPzKO', '카프카테스트2',  'ACTIVE', 15, NULL, NOW()),
('33333333-3333-3333-3333-333333333333', 'kafka.test3@example.com',
 '$2a$12$ij.wsUKNJMAMKlCkTzuoIu8osBmhIsCcn3R70cU7v6LwdN8wgPzKO', '카프카테스트3',  'ACTIVE',  5, NULL, NOW()),
('44444444-4444-4444-4444-444444444444', 'kafka.test4@example.com',
 '$2a$12$ij.wsUKNJMAMKlCkTzuoIu8osBmhIsCcn3R70cU7v6LwdN8wgPzKO', '카프카테스트4',  'ACTIVE', 25, NULL, NOW()),
('55555555-5555-5555-5555-555555555555', 'kafka.test5@example.com',
 '$2a$12$ij.wsUKNJMAMKlCkTzuoIu8osBmhIsCcn3R70cU7v6LwdN8wgPzKO', '카프카테스트5',  'ACTIVE', 15, NULL, NOW()),
('66666666-6666-6666-6666-666666666666', 'kafka.test6@example.com',
 '$2a$12$ij.wsUKNJMAMKlCkTzuoIu8osBmhIsCcn3R70cU7v6LwdN8wgPzKO', '카프카테스트6',  'ACTIVE',  5, NULL, NOW()),
('77777777-7777-7777-7777-777777777777', 'kafka.test7@example.com',
 '$2a$12$ij.wsUKNJMAMKlCkTzuoIu8osBmhIsCcn3R70cU7v6LwdN8wgPzKO', '카프카테스트7',  'ACTIVE', 25, NULL, NOW()),
('88888888-8888-8888-8888-888888888888', 'kafka.test8@example.com',
 '$2a$12$ij.wsUKNJMAMKlCkTzuoIu8osBmhIsCcn3R70cU7v6LwdN8wgPzKO', '카프카테스트8',  'ACTIVE', 15, NULL, NOW()),
('99999999-9999-9999-9999-999999999999', 'kafka.test9@example.com',
 '$2a$12$ij.wsUKNJMAMKlCkTzuoIu8osBmhIsCcn3R70cU7v6LwdN8wgPzKO', '카프카테스트9',  'ACTIVE',  5, NULL, NOW()),
('00000000-0000-0000-0000-000000000010', 'kafka.test10@example.com',
 '$2a$12$ij.wsUKNJMAMKlCkTzuoIu8osBmhIsCcn3R70cU7v6LwdN8wgPzKO', '카프카테스트10', 'ACTIVE', 25, NULL, NOW()),
('00000000-0000-0000-0000-000000000011', 'kafka.test11@example.com',
 '$2a$12$ij.wsUKNJMAMKlCkTzuoIu8osBmhIsCcn3R70cU7v6LwdN8wgPzKO', '카프카테스트11', 'ACTIVE', 15, NULL, NOW()),
('00000000-0000-0000-0000-000000000012', 'kafka.test12@example.com',
 '$2a$12$ij.wsUKNJMAMKlCkTzuoIu8osBmhIsCcn3R70cU7v6LwdN8wgPzKO', '카프카테스트12', 'ACTIVE',  5, NULL, NOW()),
('00000000-0000-0000-0000-000000000013', 'kafka.test13@example.com',
 '$2a$12$ij.wsUKNJMAMKlCkTzuoIu8osBmhIsCcn3R70cU7v6LwdN8wgPzKO', '카프카테스트13', 'ACTIVE', 25, NULL, NOW()),
('00000000-0000-0000-0000-000000000014', 'kafka.test14@example.com',
 '$2a$12$ij.wsUKNJMAMKlCkTzuoIu8osBmhIsCcn3R70cU7v6LwdN8wgPzKO', '카프카테스트14', 'ACTIVE', 15, NULL, NOW()),
('00000000-0000-0000-0000-000000000015', 'kafka.test15@example.com',
 '$2a$12$ij.wsUKNJMAMKlCkTzuoIu8osBmhIsCcn3R70cU7v6LwdN8wgPzKO', '카프카테스트15', 'ACTIVE',  5, NULL, NOW()),
('00000000-0000-0000-0000-000000000016', 'kafka.test16@example.com',
 '$2a$12$ij.wsUKNJMAMKlCkTzuoIu8osBmhIsCcn3R70cU7v6LwdN8wgPzKO', '카프카테스트16', 'ACTIVE', 25, NULL, NOW()),
('00000000-0000-0000-0000-000000000017', 'kafka.test17@example.com',
 '$2a$12$ij.wsUKNJMAMKlCkTzuoIu8osBmhIsCcn3R70cU7v6LwdN8wgPzKO', '카프카테스트17', 'ACTIVE', 15, NULL, NOW()),
('00000000-0000-0000-0000-000000000018', 'kafka.test18@example.com',
 '$2a$12$ij.wsUKNJMAMKlCkTzuoIu8osBmhIsCcn3R70cU7v6LwdN8wgPzKO', '카프카테스트18', 'ACTIVE',  5, NULL, NOW())
ON CONFLICT (id) DO NOTHING;

INSERT INTO assets (
    id, user_id, institution, asset_number, asset_type,
    account_name, account_purpose, is_salary, balance, synced_at, bank_type, created_at
) VALUES
('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1', '11111111-1111-1111-1111-111111111111',
 '삼성카드',   '5429-4494-5284-1111', 'CREDIT_CARD', '삼성 iD VISA',      '주거래 신용카드', false, 0, NOW(), 'OTHER', NOW()),
('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa2', '22222222-2222-2222-2222-222222222222',
 '현대카드',   '4321-8765-1234-2222', 'CREDIT_CARD', '현대카드 M',         '주거래 신용카드', false, 0, NOW(), 'OTHER', NOW()),
('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa3', '33333333-3333-3333-3333-333333333333',
 '우리카드',   '9876-5432-1098-3333', 'CREDIT_CARD', '우리 카드의정석',    '주거래 신용카드', false, 0, NOW(), 'WOORI', NOW()),
('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa4', '44444444-4444-4444-4444-444444444444',
 'KB국민카드', '1234-5678-9012-4444', 'CREDIT_CARD', 'KB국민 탄탄대로',    '주거래 신용카드', false, 0, NOW(), 'OTHER', NOW()),
('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa5', '55555555-5555-5555-5555-555555555555',
 '신한카드',   '5678-9012-3456-5555', 'CREDIT_CARD', '신한 Deep Dream',   '주거래 신용카드', false, 0, NOW(), 'OTHER', NOW()),
('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa6', '66666666-6666-6666-6666-666666666666',
 'NH농협카드', '9012-3456-7890-6666', 'CREDIT_CARD', 'NH채움 올바른',      '주거래 신용카드', false, 0, NOW(), 'OTHER', NOW()),
('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa7', '77777777-7777-7777-7777-777777777777',
 '하나카드',   '1111-2222-3333-7777', 'CREDIT_CARD', '하나 1Q카드',        '주거래 신용카드', false, 0, NOW(), 'OTHER', NOW()),
('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa8', '88888888-8888-8888-8888-888888888888',
 '롯데카드',   '2222-3333-4444-8888', 'CREDIT_CARD', '롯데 LOCA 365',     '주거래 신용카드', false, 0, NOW(), 'OTHER', NOW()),
('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa9', '99999999-9999-9999-9999-999999999999',
 'BC카드',     '3333-4444-5555-9999', 'CREDIT_CARD', 'BC 바로카드',        '주거래 신용카드', false, 0, NOW(), 'OTHER', NOW()),
('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaa10', '00000000-0000-0000-0000-000000000010',
 '카카오뱅크', '4444-5555-6666-0010', 'CREDIT_CARD', '카카오뱅크 체크카드', '주거래 신용카드', false, 0, NOW(), 'OTHER', NOW()),
('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaa11', '00000000-0000-0000-0000-000000000011',
 '토스',       '5555-6666-7777-0011', 'CREDIT_CARD', '토스 신용카드',      '주거래 신용카드', false, 0, NOW(), 'OTHER', NOW()),
('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaa12', '00000000-0000-0000-0000-000000000012',
 'IBK기업은행', '6666-7777-8888-0012', 'CREDIT_CARD', 'IBK 청춘 더하기',   '주거래 신용카드', false, 0, NOW(), 'OTHER', NOW()),
('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaa13', '00000000-0000-0000-0000-000000000013',
 'DGB대구은행', '7777-8888-9999-0013', 'CREDIT_CARD', 'DGB 행복 더함',     '주거래 신용카드', false, 0, NOW(), 'OTHER', NOW()),
('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaa14', '00000000-0000-0000-0000-000000000014',
 'BNK부산은행', '8888-9999-0000-0014', 'CREDIT_CARD', 'BNK 파워 체크',     '주거래 신용카드', false, 0, NOW(), 'OTHER', NOW()),
('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaa15', '00000000-0000-0000-0000-000000000015',
 '광주은행',   '9999-0000-1111-0015', 'CREDIT_CARD', '광주 행복나눔',      '주거래 신용카드', false, 0, NOW(), 'OTHER', NOW()),
('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaa16', '00000000-0000-0000-0000-000000000016',
 '전북은행',   '0000-1111-2222-0016', 'CREDIT_CARD', '전북 JB',           '주거래 신용카드', false, 0, NOW(), 'OTHER', NOW()),
('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaa17', '00000000-0000-0000-0000-000000000017',
 '제주은행',   '1234-0000-5678-0017', 'CREDIT_CARD', '제주 JDC',          '주거래 신용카드', false, 0, NOW(), 'OTHER', NOW()),
('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaa18', '00000000-0000-0000-0000-000000000018',
 '케이뱅크',   '2345-1111-6789-0018', 'CREDIT_CARD', '케이뱅크 플러스',   '주거래 신용카드', false, 0, NOW(), 'OTHER', NOW())
ON CONFLICT (id) DO NOTHING;
