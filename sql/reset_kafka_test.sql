-- ─────────────────────────────────────────────
-- Kafka 실험용 DB 리셋
-- transactions 만 비우고 users/assets(시드)는 유지한다.
-- (assets 는 mock_payment.py 가 기동 시 읽으므로 보존해야 함)
-- 매 측정 런 전에 실행하여 동일 출발점 보장.
-- ─────────────────────────────────────────────
TRUNCATE TABLE transactions RESTART IDENTITY CASCADE;
