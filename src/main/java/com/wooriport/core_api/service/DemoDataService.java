package com.wooriport.core_api.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class DemoDataService implements ApplicationRunner {

    private final JdbcTemplate jdbc;

    @Value("${demo.email:}")
    private String demoEmail;

    // ──────────────────────────────────────
    // 서버 시작 시 자동 실행
    // ──────────────────────────────────────
    @Override
    public void run(ApplicationArguments args) {
        if (demoEmail.isBlank()) return;
        try {
            seedGlobal();
            log.info("[Demo] global 시드 완료");
        } catch (Exception e) {
            log.error("[Demo] global 시드 실패", e);
        }
        try {
            seedProducts();
            log.info("[Demo] products 시드 완료");
        } catch (Exception e) {
            log.error("[Demo] products 시드 실패", e);
        }
        try {
            seedMydata(demoEmail);
            log.info("[Demo] mydata 시드 완료 — email: {}", demoEmail);
        } catch (Exception e) {
            log.error("[Demo] mydata 시드 실패", e);
        }
    }

    // ──────────────────────────────────────
    // AssetService.setSalaryAccount() 에서 호출
    // ──────────────────────────────────────
    public void onSalaryAccountSet(UUID userId, String email, UUID salaryAssetId) {
        if (demoEmail.isBlank() || !demoEmail.equalsIgnoreCase(email)) return;
        try {
            runScript("demo/transactions.sql", Map.of(
                    "USER_ID", userId.toString(),
                    "SALARY_ASSET_ID", salaryAssetId.toString()
            ));

            runScript("demo/asset_snapshots.sql", Map.of("USER_ID", userId.toString()));
            log.info("[Demo] asset_snapshots 시드 완료: userId={}", userId);

            UUID isaAssetId = findAssetByType(userId, "ISA");
            if (isaAssetId != null) {
                runScript("demo/tax_benefits.sql", Map.of("ISA_ASSET_ID", isaAssetId.toString()));
                log.info("[Demo] tax_benefit_accounts 시드 완료: isaAssetId={}", isaAssetId);
            }
            log.info("[Demo] 거래내역 자동 시드 완료: userId={}", userId);
        } catch (Exception e) {
            log.warn("[Demo] 거래내역 자동 시드 실패 (무시): {}", e.getMessage());
        }
    }

    // ──────────────────────────────────────
    // 내부 시드 메서드
    // ──────────────────────────────────────
    private void seedGlobal() {
        runScript("demo/global.sql", Map.of());
    }

    private void seedProducts() throws IOException {
        byte[] raw = new ClassPathResource("demo/products.sql").getInputStream().readAllBytes();
        String sql = new String(raw, StandardCharsets.UTF_8);
        if (sql.startsWith("\uFEFF")) sql = sql.substring(1);
        String noComments = sql.replaceAll("--[^\\n]*", "");
        for (String stmt : noComments.split(";")) {
            String trimmed = stmt.strip();
            if (trimmed.isEmpty()) continue;
            if (trimmed.toUpperCase().startsWith("INSERT")) {
                trimmed = trimmed + " ON CONFLICT (id) DO NOTHING";
            }
            jdbc.update(trimmed);
        }
    }

    private void seedMydata(String email) {
        runScript("demo/dummy_mydata.sql", Map.of("EMAIL", email));
    }

    private void runScript(String classpathLocation, Map<String, String> params) {
        try {
            byte[] raw = new ClassPathResource(classpathLocation).getInputStream().readAllBytes();
            String sql = new String(raw, StandardCharsets.UTF_8);
            for (var entry : params.entrySet()) {
                sql = sql.replace("{" + entry.getKey() + "}", entry.getValue());
            }
            // 주석 제거 후 ; 기준 분리 — 각 구문을 개별 실행
            String noComments = sql.replaceAll("--[^\\n]*", "");
            for (String stmt : noComments.split(";")) {
                String trimmed = stmt.strip();
                if (!trimmed.isEmpty()) {
                    jdbc.update(trimmed);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Demo SQL 파일 로드 실패: " + classpathLocation, e);
        }
    }

    private UUID findAssetByType(UUID userId, String assetType) {
        try {
            return jdbc.queryForObject(
                    "SELECT id FROM assets WHERE user_id = ? AND asset_type = ? AND deleted_at IS NULL LIMIT 1",
                    (rs, n) -> (UUID) rs.getObject("id"),
                    userId, assetType);
        } catch (org.springframework.dao.EmptyResultDataAccessException e) {
            return null;
        }
    }
}
