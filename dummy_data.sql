-- ================================================
-- WooriPort 더미 데이터
-- 실행 순서: users → assets → (transfer_plans, transfer_executions)
-- ================================================

-- 확장 활성화 (init.sql에 이미 있으면 생략)
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- ─────────────────────────────────────────────
-- 1. USERS
-- ─────────────────────────────────────────────
-- 테스트용 비밀번호: test1234! (BCrypt 인코딩 값)
INSERT INTO users (id, email, password, name, phone, status, finance_type, created_at)
VALUES (
    'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
    'test@wooriport.com',
    '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', -- test1234!
    '홍길동',
    '010-1234-5678',
    'ACTIVE',
    'ROCKET',
    NOW()
);

-- ─────────────────────────────────────────────
-- 2. ASSETS (계좌 5개)
-- ─────────────────────────────────────────────

-- 급여 통장 (우리은행) - 이체의 출발지
INSERT INTO assets (id, user_id, institution, asset_type, account_purpose, balance, synced_at, bank_type, created_at)
VALUES (
    'a1a1a1a1-aaaa-aaaa-aaaa-a1a1a1a1a1a1',
    'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
    '우리은행',
    'BANK',
    'SALARY',
    5000000,  -- 500만원
    NOW(),
    'WOORI',
    NOW()
);

-- 소비 통장 (카카오뱅크)
INSERT INTO assets (id, user_id, institution, asset_type, account_purpose, balance, synced_at, bank_type, created_at)
VALUES (
    'a2a2a2a2-aaaa-aaaa-aaaa-a2a2a2a2a2a2',
    'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
    '카카오뱅크',
    'BANK',
    'SPENDING',
    800000,
    NOW(),
    'OTHER',
    NOW()
);

-- 비상금 통장 (토스뱅크)
INSERT INTO assets (id, user_id, institution, asset_type, account_purpose, balance, synced_at, bank_type, created_at)
VALUES (
    'a3a3a3a3-aaaa-aaaa-aaaa-a3a3a3a3a3a3',
    'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
    '토스뱅크',
    'BANK',
    'EMERGENCY',
    1200000,
    NOW(),
    'OTHER',
    NOW()
);

-- 목적 통장 - 여행 (신한은행)
INSERT INTO assets (id, user_id, institution, asset_type, account_purpose, balance, synced_at, bank_type, created_at)
VALUES (
    'a4a4a4a4-aaaa-aaaa-aaaa-a4a4a4a4a4a4',
    'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
    '신한은행',
    'BANK',
    'TARGET',
    500000,
    NOW(),
    'OTHER',
    NOW()
);

-- 저축 통장 (우리은행)
INSERT INTO assets (id, user_id, institution, asset_type, account_purpose, balance, synced_at, bank_type, created_at)
VALUES (
    'a5a5a5a5-aaaa-aaaa-aaaa-a5a5a5a5a5a5',
    'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
    '우리은행',
    'BANK',
    'SAVING',
    2000000,
    NOW(),
    'WOORI',
    NOW()
);

-- ─────────────────────────────────────────────
-- 3. 데이터 확인 쿼리
-- ─────────────────────────────────────────────
SELECT '=== USERS ===' AS info;
SELECT id, email, name, status, finance_type FROM users;

SELECT '=== ASSETS ===' AS info;
SELECT id, institution, asset_type, account_purpose, balance, bank_type FROM assets;
