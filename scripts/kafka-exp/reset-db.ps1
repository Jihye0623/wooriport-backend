# ─────────────────────────────────────────────
# Kafka 실험 DB 리셋 + 시드 (매 측정 런 전에 실행)
#   1) transactions 비우기 (reset_kafka_test.sql)
#   2) 카프카테스트 유저/카드 시드 보장 (seed_kafka_test.sql, ON CONFLICT DO NOTHING)
#   3) 데모(demo_chaos_seed) 잔존 데이터 제거 + Redis flush
#      → 성능 측정이 "활성 챌린지 0 / CREDIT_CARD 3장" 클린 조건이 되도록 (v0/v1 과 동일 조건).
#        (이게 없으면 데모용 활성 챌린지가 남아 처리량이 ~1,305→~1,118 로 떨어짐. phase-2.md 민감도 표 참고)
# 전제: docker compose 로 wooriport-db, wooriport-redis 컨테이너가 떠 있어야 함.
# 사용: powershell -File scripts\kafka-exp\reset-db.ps1
# 주의: 라이브 데모(capture-guide) 직전엔 이 스크립트 후 demo_chaos_seed.sql 을 다시 실행할 것.
# ─────────────────────────────────────────────
$ErrorActionPreference = "Stop"
$sqlDir = Join-Path $PSScriptRoot "..\..\sql"
$container = "wooriport-db"

# 한글(UTF-8) 보존을 위해 PowerShell 파이프 대신 docker cp 후 psql -f 로 실행.
function Invoke-Sql($file) {
    $src = Join-Path $sqlDir $file
    docker cp $src "${container}:/tmp/$file" | Out-Null
    docker exec $container psql -U wooriport -d wooriport -v ON_ERROR_STOP=1 -f "/tmp/$file"
}

Write-Host "[reset] truncate transactions..."
Invoke-Sql "reset_kafka_test.sql"

Write-Host "[reset] seed users/assets..."
Invoke-Sql "seed_kafka_test.sql"

Write-Host "[reset] remove demo (chaos) data so perf run is clean (no active challenge)..."
$chaosCleanup = @"
DELETE FROM mini_challenges WHERE user_id IN ('66666666-6666-6666-6666-666666666666','55555555-5555-5555-5555-555555555555');
UPDATE users SET auto_transfer_to_asset_id = NULL WHERE id='55555555-5555-5555-5555-555555555555';
DELETE FROM assets WHERE id IN ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbb1','bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbb2');
DELETE FROM users WHERE id IN ('66666666-6666-6666-6666-666666666666','55555555-5555-5555-5555-555555555555');
"@
docker exec $container psql -U wooriport -d wooriport -v ON_ERROR_STOP=1 -c $chaosCleanup

Write-Host "[reset] flush Redis (stale challenge keys)..."
docker exec wooriport-redis redis-cli FLUSHALL | Out-Null

Write-Host "[reset] done."
