-- ============================================================
-- pension_transactions.sql  —  연금저축 월 납입 거래내역 더미
-- 파라미터: {USER_ID}, {PENSION_ASSET_ID}
-- sumTaxBenefitContributionByMonth 쿼리에서 월 납입액으로 집계됨
-- ============================================================

DELETE FROM transactions
  WHERE user_id = '{USER_ID}'
    AND asset_id = '{PENSION_ASSET_ID}';

INSERT INTO transactions (id, user_id, asset_id, amount, category, sender_name, transaction_at)
VALUES
-- 4월 납입
(gen_random_uuid(), '{USER_ID}', '{PENSION_ASSET_ID}',
 400000, '연금저축', '한국투자증권',
 date_trunc('month', NOW()) - INTERVAL '2 month' + INTERVAL '4 day'),

-- 5월 납입
(gen_random_uuid(), '{USER_ID}', '{PENSION_ASSET_ID}',
 400000, '연금저축', '한국투자증권',
 date_trunc('month', NOW()) - INTERVAL '1 month' + INTERVAL '4 day'),

-- 6월 납입
(gen_random_uuid(), '{USER_ID}', '{PENSION_ASSET_ID}',
 400000, '연금저축', '한국투자증권',
 date_trunc('month', NOW()) + INTERVAL '4 day');
