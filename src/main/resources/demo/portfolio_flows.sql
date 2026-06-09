-- ============================================================
-- portfolio_flows.sql  —  리포트 포트폴리오 breakdown용 흐름 더미
-- 파라미터: {USER_ID}
-- ETF/BOND 상품은 products 테이블에서 조회 (ticker 있는 첫 번째)
-- ============================================================

DELETE FROM portfolio_flow_items
  WHERE flow_id IN (SELECT id FROM portfolio_flows WHERE user_id = '{USER_ID}');
DELETE FROM portfolio_flows WHERE user_id = '{USER_ID}';

INSERT INTO portfolio_flows
    (id, user_id, title, summary, term, amount,
     expected_rr_pct, investment_months, expected_amount,
     rr_comment, is_active, started_at, created_at)
VALUES
('b0000001-0000-0000-0000-000000000001',
 '{USER_ID}',
 '글로벌 ETF 흐름',
 '글로벌 ETF와 채권으로 리스크를 분산해요',
 '장',
 2000000,
 7.5, 12, 2150000,
 '장기 분산 투자로 안정적인 복리 수익을 기대할 수 있어요.',
 true, NOW(), NOW());

-- ETF 항목 (products 테이블에서 ticker 있는 ETF 1개 사용)
INSERT INTO portfolio_flow_items (id, flow_id, product_id, product_ratio, ai_comment, created_at)
SELECT gen_random_uuid(),
       'b0000001-0000-0000-0000-000000000001',
       p.id,
       70,
       '글로벌 시장 성장에 연동된 ETF로 장기 수익을 노려요.',
       NOW()
FROM products p
WHERE p.product_type = 'ETF'
  AND p.ticker IS NOT NULL
  AND p.deleted_at IS NULL
ORDER BY p.created_at ASC
LIMIT 1;

-- BOND 항목 (products 테이블에서 ticker 있는 BOND 1개, 없으면 두 번째 ETF 사용)
INSERT INTO portfolio_flow_items (id, flow_id, product_id, product_ratio, ai_comment, created_at)
SELECT gen_random_uuid(),
       'b0000001-0000-0000-0000-000000000001',
       p.id,
       30,
       '채권 ETF로 변동성을 낮추고 안정성을 더해요.',
       NOW()
FROM products p
WHERE p.product_type IN ('BOND', 'ETF')
  AND p.ticker IS NOT NULL
  AND p.deleted_at IS NULL
  AND p.id NOT IN (
      SELECT pi.product_id FROM portfolio_flow_items pi
      WHERE pi.flow_id = 'b0000001-0000-0000-0000-000000000001'
        AND pi.product_id IS NOT NULL
  )
ORDER BY p.product_type ASC, p.created_at ASC
LIMIT 1;
