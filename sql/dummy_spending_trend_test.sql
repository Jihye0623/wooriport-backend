-- ================================================
-- SPENDING_TREND 알림 상세 조회 테스트용 더미 데이터
-- 대상 유저: aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa (홍길동)
-- 테스트 날짜: 2026-05-29
-- ================================================

-- ai_comment 컬럼 추가 (Spring ddl-auto가 자동 추가 안 된 경우)
ALTER TABLE notifications ADD COLUMN IF NOT EXISTS ai_comment TEXT;

-- ─────────────────────────────────────────────
-- 1. SPENDING_TREND 알림 (sent_at = 2026-05-29)
-- ─────────────────────────────────────────────
INSERT INTO notifications (id, user_id, type, title, content, ai_comment, is_read, sent_at, created_at)
VALUES (
    'bb000001-0000-0000-0000-000000000001',
    'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
    'SPENDING_TREND',
    '밸런싱 붕괴 조짐이 보여요',
    '이번 달 소비 속도가 빠르게 올라가고 있어요!',
    '식비와 쇼핑 카테고리에서 지난달 대비 각각 40%, 80% 증가했습니다. 특히 4주차 쇼핑 지출이 급증하고 있어 월말까지 지속되면 예산을 초과할 수 있습니다. 식비는 외식 빈도를 줄이고 쇼핑은 불필요한 충동구매를 자제하는 것을 권장합니다.',
    false,
    '2026-05-29 10:00:00',
    NOW()
);

-- ─────────────────────────────────────────────
-- 2. 이번달 지출 거래 (2026-05-01 ~ 2026-05-29)
--    asset: 카카오뱅크 소비통장 a2a2a2a2-aaaa-aaaa-aaaa-a2a2a2a2a2a2
-- ─────────────────────────────────────────────

-- 1주차 (5/1 ~ 5/7)
INSERT INTO transactions (id, user_id, asset_id, amount, category, sender_name, transaction_at)
VALUES ('cc000001-0000-0000-0000-000000000001', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'a2a2a2a2-aaaa-aaaa-aaaa-a2a2a2a2a2a2', -45000,  '식비',    '스타벅스',       '2026-05-02 12:30:00');
INSERT INTO transactions (id, user_id, asset_id, amount, category, sender_name, transaction_at)
VALUES ('cc000002-0000-0000-0000-000000000001', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'a2a2a2a2-aaaa-aaaa-aaaa-a2a2a2a2a2a2', -18000,  '교통비',  'T머니',          '2026-05-04 08:10:00');
INSERT INTO transactions (id, user_id, asset_id, amount, category, sender_name, transaction_at)
VALUES ('cc000003-0000-0000-0000-000000000001', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'a2a2a2a2-aaaa-aaaa-aaaa-a2a2a2a2a2a2', -32000,  '식비',    '편의점',         '2026-05-06 19:45:00');

-- 2주차 (5/8 ~ 5/14)
INSERT INTO transactions (id, user_id, asset_id, amount, category, sender_name, transaction_at)
VALUES ('cc000004-0000-0000-0000-000000000001', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'a2a2a2a2-aaaa-aaaa-aaaa-a2a2a2a2a2a2', -68000,  '식비',    '식당',           '2026-05-09 13:00:00');
INSERT INTO transactions (id, user_id, asset_id, amount, category, sender_name, transaction_at)
VALUES ('cc000005-0000-0000-0000-000000000001', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'a2a2a2a2-aaaa-aaaa-aaaa-a2a2a2a2a2a2', -120000, '쇼핑',    '무신사',         '2026-05-11 15:20:00');
INSERT INTO transactions (id, user_id, asset_id, amount, category, sender_name, transaction_at)
VALUES ('cc000006-0000-0000-0000-000000000001', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'a2a2a2a2-aaaa-aaaa-aaaa-a2a2a2a2a2a2', -22000,  '교통비',  'T머니',          '2026-05-13 08:00:00');

-- 3주차 (5/15 ~ 5/21)
INSERT INTO transactions (id, user_id, asset_id, amount, category, sender_name, transaction_at)
VALUES ('cc000007-0000-0000-0000-000000000001', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'a2a2a2a2-aaaa-aaaa-aaaa-a2a2a2a2a2a2', -75000,  '식비',    '배달의민족',     '2026-05-16 19:00:00');
INSERT INTO transactions (id, user_id, asset_id, amount, category, sender_name, transaction_at)
VALUES ('cc000008-0000-0000-0000-000000000001', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'a2a2a2a2-aaaa-aaaa-aaaa-a2a2a2a2a2a2', -85000,  '문화생활', 'CGV',           '2026-05-18 17:30:00');
INSERT INTO transactions (id, user_id, asset_id, amount, category, sender_name, transaction_at)
VALUES ('cc000009-0000-0000-0000-000000000001', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'a2a2a2a2-aaaa-aaaa-aaaa-a2a2a2a2a2a2', -55000,  '쇼핑',    '올리브영',       '2026-05-20 14:00:00');

-- 4주차 (5/22 ~ 5/28)
INSERT INTO transactions (id, user_id, asset_id, amount, category, sender_name, transaction_at)
VALUES ('cc000010-0000-0000-0000-000000000001', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'a2a2a2a2-aaaa-aaaa-aaaa-a2a2a2a2a2a2', -210000, '쇼핑',    '쿠팡',           '2026-05-23 10:00:00');
INSERT INTO transactions (id, user_id, asset_id, amount, category, sender_name, transaction_at)
VALUES ('cc000011-0000-0000-0000-000000000001', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'a2a2a2a2-aaaa-aaaa-aaaa-a2a2a2a2a2a2', -88000,  '식비',    '식당',           '2026-05-25 12:30:00');
INSERT INTO transactions (id, user_id, asset_id, amount, category, sender_name, transaction_at)
VALUES ('cc000012-0000-0000-0000-000000000001', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'a2a2a2a2-aaaa-aaaa-aaaa-a2a2a2a2a2a2', -35000,  '의료',    '약국',           '2026-05-27 16:00:00');

-- 5주차 (5/29)
INSERT INTO transactions (id, user_id, asset_id, amount, category, sender_name, transaction_at)
VALUES ('cc000013-0000-0000-0000-000000000001', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'a2a2a2a2-aaaa-aaaa-aaaa-a2a2a2a2a2a2', -28000,  '식비',    '스타벅스',       '2026-05-29 09:00:00');

-- ─────────────────────────────────────────────
-- 3. 지난달 지출 거래 (2026-04-01 ~ 2026-04-30)
-- ─────────────────────────────────────────────

-- 1주차 (4/1 ~ 4/7)
INSERT INTO transactions (id, user_id, asset_id, amount, category, sender_name, transaction_at)
VALUES ('dd000001-0000-0000-0000-000000000001', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'a2a2a2a2-aaaa-aaaa-aaaa-a2a2a2a2a2a2', -38000,  '식비',    '편의점',         '2026-04-02 11:00:00');
INSERT INTO transactions (id, user_id, asset_id, amount, category, sender_name, transaction_at)
VALUES ('dd000002-0000-0000-0000-000000000001', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'a2a2a2a2-aaaa-aaaa-aaaa-a2a2a2a2a2a2', -15000,  '교통비',  'T머니',          '2026-04-05 08:00:00');

-- 2주차 (4/8 ~ 4/14)
INSERT INTO transactions (id, user_id, asset_id, amount, category, sender_name, transaction_at)
VALUES ('dd000003-0000-0000-0000-000000000001', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'a2a2a2a2-aaaa-aaaa-aaaa-a2a2a2a2a2a2', -52000,  '식비',    '식당',           '2026-04-10 12:30:00');
INSERT INTO transactions (id, user_id, asset_id, amount, category, sender_name, transaction_at)
VALUES ('dd000004-0000-0000-0000-000000000001', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'a2a2a2a2-aaaa-aaaa-aaaa-a2a2a2a2a2a2', -65000,  '쇼핑',    '올리브영',       '2026-04-12 15:00:00');

-- 3주차 (4/15 ~ 4/21)
INSERT INTO transactions (id, user_id, asset_id, amount, category, sender_name, transaction_at)
VALUES ('dd000005-0000-0000-0000-000000000001', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'a2a2a2a2-aaaa-aaaa-aaaa-a2a2a2a2a2a2', -48000,  '식비',    '배달의민족',     '2026-04-17 19:00:00');
INSERT INTO transactions (id, user_id, asset_id, amount, category, sender_name, transaction_at)
VALUES ('dd000006-0000-0000-0000-000000000001', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'a2a2a2a2-aaaa-aaaa-aaaa-a2a2a2a2a2a2', -55000,  '문화생활', 'CGV',           '2026-04-19 14:00:00');

-- 4주차 (4/22 ~ 4/28)
INSERT INTO transactions (id, user_id, asset_id, amount, category, sender_name, transaction_at)
VALUES ('dd000007-0000-0000-0000-000000000001', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'a2a2a2a2-aaaa-aaaa-aaaa-a2a2a2a2a2a2', -110000, '쇼핑',    '쿠팡',           '2026-04-24 10:00:00');
INSERT INTO transactions (id, user_id, asset_id, amount, category, sender_name, transaction_at)
VALUES ('dd000008-0000-0000-0000-000000000001', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'a2a2a2a2-aaaa-aaaa-aaaa-a2a2a2a2a2a2', -42000,  '식비',    '식당',           '2026-04-26 13:00:00');
INSERT INTO transactions (id, user_id, asset_id, amount, category, sender_name, transaction_at)
VALUES ('dd000009-0000-0000-0000-000000000001', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'a2a2a2a2-aaaa-aaaa-aaaa-a2a2a2a2a2a2', -18000,  '교통비',  'T머니',          '2026-04-28 08:00:00');

-- ─────────────────────────────────────────────
-- 확인 쿼리
-- ─────────────────────────────────────────────
SELECT '=== NOTIFICATION ===' AS info;
SELECT id, type, title, LEFT(ai_comment, 30) AS ai_comment_preview, sent_at FROM notifications WHERE type = 'SPENDING_TREND';

SELECT '=== 이번달 지출 (5월) ===' AS info;
SELECT category, SUM(ABS(amount)) AS total, COUNT(*) AS cnt
FROM transactions
WHERE user_id = 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa'
  AND transaction_at >= '2026-05-01'
  AND transaction_at <= '2026-05-29 23:59:59'
  AND amount < 0
GROUP BY category ORDER BY total DESC;

SELECT '=== 지난달 지출 (4월) ===' AS info;
SELECT category, SUM(ABS(amount)) AS total, COUNT(*) AS cnt
FROM transactions
WHERE user_id = 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa'
  AND transaction_at >= '2026-04-01'
  AND transaction_at <= '2026-04-30 23:59:59'
  AND amount < 0
GROUP BY category ORDER BY total DESC;
