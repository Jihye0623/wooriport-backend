-- ============================================================
-- dummy_mydata.sql  —  Linking 화면용 마이데이터 더미
-- 파라미터: {EMAIL}
-- 재실행 시 기존 행 삭제 후 재삽입
-- ============================================================

DELETE FROM dummy_mydata WHERE email = '{EMAIL}';

INSERT INTO dummy_mydata (email, institution, asset_type, account_name, account_purpose, asset_number, balance, bank_type, is_salary) VALUES
    ('{EMAIL}', '우리은행',   'CHECKING',        '우리 WON 통장',     null, '1002-111-111111',     6800000, 'WOORI', false),
    ('{EMAIL}', '토스뱅크',   'PARKING',         '토스 파킹통장',     null, '4444-22-222222',      2000000, 'OTHER', false),
    ('{EMAIL}', '카카오뱅크', 'SAVINGS',         '카뱅 26주 적금',    null,   '3333-33-333333',      1500000, 'OTHER', false),
    ('{EMAIL}', '우리투자증권', 'STOCK',           '종합매매계좌',      null,  '5555-44-444444',      4000000, 'WOORI', false),
    ('{EMAIL}', '토스뱅크',   'PARKING',         '여행 모음통장',     null,   '4444-66-666666',       335000, 'OTHER', false),
    ('{EMAIL}', '한국투자',   'PENSION_SAVINGS',  '연금저축펀드',     null,   '8888-88-888888',      5000000, 'OTHER', false),
    ('{EMAIL}', '우리카드',   'CREDIT_CARD',     '우리 카드의정석',   null,   '5570-1111-2222-3333',       0, 'WOORI', false),
    ('{EMAIL}', '신한카드',   'CREDIT_CARD',     '신한 Deep Dream',   null,   '4000-4444-5555-6666',       0, 'OTHER', false);
