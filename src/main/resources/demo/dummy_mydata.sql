-- ============================================================
-- dummy_mydata.sql  —  Linking 화면용 마이데이터 더미
-- 파라미터: {EMAIL}
-- 재실행 시 기존 행 삭제 후 재삽입
-- ============================================================

DELETE FROM dummy_mydata WHERE email = '{EMAIL}';

INSERT INTO dummy_mydata (email, institution, asset_type, account_name, account_purpose, asset_number, balance, bank_type, is_salary) VALUES
    ('{EMAIL}', '우리은행',     'CHECKING',    '우리 WON 통장',     null, '1002-111-111111',     6800000, 'WOORI', false),
    ('{EMAIL}', '토스뱅크',     'CHECKING',    '토스뱅크 통장',     null, '4444-22-222222',      1200000, 'OTHER', false),
    ('{EMAIL}', '카카오뱅크',   'CHECKING',    '카카오뱅크 통장',   null, '3333-33-333333',       850000, 'OTHER', false),
    ('{EMAIL}', '국민은행',     'CHECKING',    'KB 국민 통장',      null, '6010-44-444444',      2100000, 'OTHER', false),
    ('{EMAIL}', '신한은행',     'CHECKING',    '신한 SOL 통장',     null, '5550-55-555555',      1750000, 'OTHER', false),
    ('{EMAIL}', '하나은행',     'CHECKING',    '하나 통장',         null, '6660-66-666666',       980000, 'OTHER', false),
    ('{EMAIL}', '우리투자증권', 'ISA',         '우리 ISA 계좌',     null, '7700-11-111111',      3000000, 'WOORI', false),
    ('{EMAIL}', '우리은행',     'SAVINGS',     '우리 정기적금',     null, '1002-222-222222',     1500000, 'WOORI', false),
    ('{EMAIL}', '우리은행',     'DEPOSIT',     '우리 정기예금',     null, '1002-333-333333',     5000000, 'WOORI', false),
    ('{EMAIL}', '우리투자증권', 'STOCK',       '종합매매계좌',      null, '5555-44-444444',      4000000, 'WOORI', false),
    ('{EMAIL}', '우리카드',     'CREDIT_CARD', '우리 카드의정석',   null, '5570-1111-2222-3333',       0, 'WOORI', false),
    ('{EMAIL}', '신한카드',     'CREDIT_CARD', '신한 Deep Dream',   null, '4000-4444-5555-6666',       0, 'OTHER', false);
