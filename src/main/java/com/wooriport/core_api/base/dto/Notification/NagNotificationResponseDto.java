package com.wooriport.core_api.base.dto.Notification;

import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class NagNotificationResponseDto {
    private UUID id;
    private UUID challengeId;
    private String type;
    private String challengeTitle;
    private String stockName;
    private Double affordableShares;
    private String content;
    private Boolean isRead;
    private String sentAt;
}
