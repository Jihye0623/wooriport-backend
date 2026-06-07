-- ─────────────────────────────────────────────
-- Phase 1 결함 복구 라이브 데모용 시드
--   · 급여 DLT 데모: auto_transfer 계좌를 가진 유저(급여 이벤트가 handleIfSalary 를 타도록)
--   · 챌린지 재계산 데모: 진행중(IN_PROGRESS) LUNCH 챌린지를 가진 유저+카드
-- (D1 중복 데모는 seed_kafka_test 의 카프카테스트1 카드 사용)
-- ─────────────────────────────────────────────

-- 급여 데모 유저 + 자동이체 출발 계좌
INSERT INTO users (id, email, password, name, status, salary, salary_date, created_at)
VALUES ('55555555-5555-5555-5555-555555555555', 'chaos.salary@test.com', 'pw', '카오스급여', 'ACTIVE', 3000000, 25, NOW())
ON CONFLICT (id) DO NOTHING;

INSERT INTO assets (id, user_id, institution, asset_number, asset_type,
                    account_name, account_purpose, is_salary, balance, synced_at, bank_type, created_at)
VALUES ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbb1', '55555555-5555-5555-5555-555555555555',
        '우리은행', '7777-0000-0000-5555', 'DEPOSIT', '급여통장', '자동이체', false, 0, NOW(), 'WOORI', NOW())
ON CONFLICT (id) DO NOTHING;

UPDATE users SET auto_transfer_to_asset_id = 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbb1'
WHERE id = '55555555-5555-5555-5555-555555555555';

-- 챌린지 데모 유저 + 카드 + 진행중 LUNCH 챌린지(한도 큼, startedAt 과거)
INSERT INTO users (id, email, password, name, status, created_at)
VALUES ('66666666-6666-6666-6666-666666666666', 'chaos.challenge@test.com', 'pw', '카오스챌린지', 'ACTIVE', NOW())
ON CONFLICT (id) DO NOTHING;

INSERT INTO assets (id, user_id, institution, asset_number, asset_type,
                    account_name, account_purpose, is_salary, balance, synced_at, bank_type, created_at)
VALUES ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbb2', '66666666-6666-6666-6666-666666666666',
        '삼성카드', '8888-0000-0000-6666', 'CREDIT_CARD', '챌린지카드', '소비', false, 0, NOW(), 'OTHER', NOW())
ON CONFLICT (id) DO NOTHING;

INSERT INTO mini_challenges (id, user_id, title, category, challenge_type, challenge_sub_type,
                            target, status, current_value, notified_threshold, started_at, created_at)
VALUES ('cccccccc-cccc-cccc-cccc-cccccccccc01', '66666666-6666-6666-6666-666666666666',
        '점심 절약', '식비', 'AMOUNT', 'LUNCH', 10000000, 'IN_PROGRESS', 0, 0, '2026-01-01 00:00:00', NOW())
ON CONFLICT (id) DO NOTHING;
