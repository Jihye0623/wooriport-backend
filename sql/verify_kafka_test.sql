-- ─────────────────────────────────────────────
-- Kafka 실험 정합성 검증 쿼리
-- 측정/시나리오 실행 후 실행하여 적재 결과를 확인한다.
-- ─────────────────────────────────────────────

-- 1. 전체 적재 행 수 (발행 건수와 비교)
SELECT COUNT(*) AS total_transactions FROM transactions;

-- 2. 카프카테스트 유저별 적재 건수
SELECT u.name, COUNT(t.id) AS tx_count
FROM users u
LEFT JOIN transactions t ON t.user_id = u.id
WHERE u.name LIKE '카프카테스트%'
GROUP BY u.name
ORDER BY u.name;

-- 3. [Phase 1+] event_id 기준 중복 적재 확인 (Phase 1에서 event_id 컬럼 추가 후 0 이어야 함)
--    Phase 0 에서는 event_id 컬럼이 없으므로 주석 처리.
-- SELECT event_id, COUNT(*) AS dup
-- FROM transactions
-- GROUP BY event_id
-- HAVING COUNT(*) > 1
-- ORDER BY dup DESC
-- LIMIT 20;
