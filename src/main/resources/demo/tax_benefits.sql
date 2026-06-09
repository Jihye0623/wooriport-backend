-- ============================================================
-- tax_benefits.sql  —  ISA 납입원금 시드 (세제혜택 화면용)
-- 파라미터: {ISA_ASSET_ID}
-- 현재 평가액 12,000,000 / 납입원금 11,000,000 → 수익률 ≈ 9.09%
-- ============================================================

INSERT INTO tax_benefit_accounts (id, asset_id, principal, created_at)
VALUES (gen_random_uuid(), '{ISA_ASSET_ID}', 11000000, NOW())
ON CONFLICT (asset_id) DO UPDATE
    SET principal = EXCLUDED.principal;
