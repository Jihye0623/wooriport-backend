# ─────────────────────────────────────────────
# Kafka 실험 DB 리셋 + 시드 (매 측정 런 전에 실행)
#   1) transactions 비우기 (reset_kafka_test.sql)
#   2) 카프카테스트 유저/카드 시드 보장 (dummy_kafka_test.sql, ON CONFLICT DO NOTHING)
# 전제: docker compose 로 wooriport-db 컨테이너가 떠 있어야 함.
# 사용: powershell -File scripts\kafka-exp\reset-db.ps1
# ─────────────────────────────────────────────
$ErrorActionPreference = "Stop"
$sqlDir = Join-Path $PSScriptRoot "..\..\sql"
$container = "wooriport-db"
$psql = "psql -U wooriport -d wooriport -v ON_ERROR_STOP=1"

Write-Host "▶ transactions 리셋..."
Get-Content (Join-Path $sqlDir "reset_kafka_test.sql") -Raw | docker exec -i $container sh -c $psql

Write-Host "▶ 시드 보장 (users/assets)..."
Get-Content (Join-Path $sqlDir "dummy_kafka_test.sql") -Raw | docker exec -i $container sh -c $psql

Write-Host "✅ 리셋 + 시드 완료"
