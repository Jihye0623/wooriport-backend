-- ============================================================
-- mini_challenges.sql  —  3개월치 완료 챌린지 더미
-- 파라미터: {USER_ID}
-- ============================================================

DELETE FROM mini_challenges WHERE user_id = '{USER_ID}';

INSERT INTO mini_challenges
    (id, user_id, title, description, category, challenge_type, challenge_sub_type,
     target, status, estimated_saving, current_value, notified_threshold,
     started_at, completed_at, created_at)
VALUES
-- 4월: 카페 줄이기 성공
(gen_random_uuid(), '{USER_ID}',
 '이번 달 카페 지출 10만원 이하로 줄이기',
 '카페 방문 횟수를 줄이고 집에서 커피를 마셔요.',
 '카페', 'AMOUNT', 'COFFEE',
 100000, 'COMPLETED', 50000, 87000, 2,
 '2026-04-01 09:00:00', '2026-04-28 00:00:00', '2026-04-01 09:00:00'),

-- 5월: 배달 줄이기 성공
(gen_random_uuid(), '{USER_ID}',
 '이번 달 배달 지출 15만원 이하로 줄이기',
 '배달 앱 대신 직접 요리하거나 포장 주문을 활용해요.',
 '식비', 'AMOUNT', 'DELIVERY',
 150000, 'COMPLETED', 80000, 132000, 2,
 '2026-05-01 09:00:00', '2026-05-28 00:00:00', '2026-05-01 09:00:00'),

-- 6월: 택시 줄이기 성공
(gen_random_uuid(), '{USER_ID}',
 '이번 달 택시비 5만원 이하로 줄이기',
 '대중교통을 적극 활용해 교통비를 절약해요.',
 '교통', 'AMOUNT', 'TAXI',
 50000, 'COMPLETED', 30000, 41000, 1,
 '2026-06-01 09:00:00', '2026-06-08 00:00:00', '2026-06-01 09:00:00');
