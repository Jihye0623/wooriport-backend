-- 활성 챌린지 조회(GET /challenges/active) 테스트용 시드
-- 진행 중(IN_PROGRESS) 챌린지 1건을 flowtest 유저에게 부여
-- 재실행 안전: 기존 IN_PROGRESS 를 먼저 정리한 뒤 삽입
DO $$
DECLARE
    v_user uuid;
BEGIN
    SELECT id INTO v_user FROM users WHERE email = 'flowtest@wooriport.com';
    IF v_user IS NULL THEN
        RAISE EXCEPTION 'flowtest 유저가 없습니다. 먼저 사용자 시드를 실행하세요.';
    END IF;

    DELETE FROM mini_challenges WHERE user_id = v_user AND status = 'IN_PROGRESS';

    INSERT INTO mini_challenges (
        id, user_id, title, description, category,
        challenge_type, challenge_sub_type, target,
        status, reward_stock_ticker, estimated_saving,
        current_value, notified_threshold, started_at, created_at
    ) VALUES (
        gen_random_uuid(), v_user,
        '커피 3잔만 마시기',
        '이번주 카페 지출을 줄여 절약 습관을 만들어봐요!',
        '카페',
        'COUNT', 'COFFEE', 3,
        'IN_PROGRESS', '삼성전자', 24000,
        2, 0, NOW(), NOW()
    );

    RAISE NOTICE '✅ 활성 챌린지 시드 완료 (target=3, current=2 → 진행도 66%%)';
END $$;
