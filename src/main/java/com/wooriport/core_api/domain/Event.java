package com.wooriport.core_api.domain;

import com.wooriport.core_api.domain.common.SoftDeleteEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "event")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Event extends SoftDeleteEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private Users user;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "target_amount", nullable = false)
    private Long targetAmount;

    @Column(name = "deadline", nullable = false)
    private LocalDate deadline;

    // ACTIVE / COMPLETED / CANCELLED / EXPIRED
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private EventStatus status = EventStatus.ACTIVE;

    // AI가 생성한 목표 요약 메시지
    @Column(name = "summary_message", columnDefinition = "TEXT")
    private String summaryMessage;

    // 자연어 원문 ("일본 여행 가고 싶어")
    @Column(name = "event_description", columnDefinition = "TEXT")
    private String eventDescription;

    public void cancel() {
        this.status = EventStatus.CANCELLED;
        this.delete();
    }

    public void expire() {
        this.status = EventStatus.EXPIRED;
    }

    public enum EventStatus {
        ACTIVE, COMPLETED, CANCELLED, EXPIRED
    }

}