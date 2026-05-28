-- =========================================================
-- portfolio_flows: priority(INT) → term(VARCHAR(50)) 변경
--                  summary(VARCHAR(200)) 추가
-- assets.asset_type enum 에 ISA 추가
--
-- ddl-auto=update 는 컬럼 추가는 자동이지만
-- rename / 타입 변경은 자동으로 처리하지 않으므로 수동 실행 필요.
-- =========================================================

ALTER TABLE portfolio_flows DROP COLUMN IF EXISTS priority;
ALTER TABLE portfolio_flows ADD COLUMN IF NOT EXISTS term VARCHAR(50);
ALTER TABLE portfolio_flows ADD COLUMN IF NOT EXISTS summary VARCHAR(200);

-- asset_type 은 @Enumerated(STRING) 이라 컬럼은 VARCHAR.
-- 새 값(ISA) 자체는 enum 추가만으로 동작하지만 CHECK 제약이 걸려 있다면 갱신 필요.
-- (현재는 별도 CHECK 제약 없음 — 참고용)
