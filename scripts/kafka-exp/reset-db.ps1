# ─────────────────────────────────────────────
# Kafka 실험 DB 리셋 + 시드 (매 측정 런 전에 실행)
#   1) transactions 비우기 (reset_kafka_test.sql)
#   2) 카프카테스트 유저/카드 시드 보장 (seed_kafka_test.sql, ON CONFLICT DO NOTHING)
# 전제: docker compose 로 wooriport-db 컨테이너가 떠 있어야 함.
# 사용: powershell -File scripts\kafka-exp\reset-db.ps1
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

Write-Host "[reset] done."
