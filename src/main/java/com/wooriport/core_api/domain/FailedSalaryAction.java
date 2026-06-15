package com.wooriport.core_api.domain;

import com.wooriport.core_api.domain.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 급여 처리가 재시도 끝에 실패해 DLT 로 격리된 액션의 감사기록.
 * 관리자 대시보드에서 조회 → 원인 수정 후 redrive(재투입) 한다.
 */
@Entity
@Table(name = "failed_salary_actions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class FailedSalaryAction extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    // 재투입에 필요한 PersistedTransaction JSON
    @Column(name = "payload", columnDefinition = "text", nullable = false)
    private String payload;

    // DLT 로 갈 때까지의 재시도 횟수
    @Column(name = "attempts")
    private Integer attempts;

    @Column(name = "last_error", length = 500)
    private String lastError;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private Status status = Status.PENDING;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    public enum Status {
        PENDING,   // 격리됨, 관리자 처리 대기
        RESOLVED   // redrive(재투입) 완료
    }

    public void resolve() {
        this.status = Status.RESOLVED;
        this.resolvedAt = LocalDateTime.now();
    }
}
