package com.wooriport.core_api.base.dto.challenge;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Getter;
import lombok.Setter;

// 입력(Flask /mini_challenge 응답)은 snake_case → @JsonAlias 로 수용,
// 출력(프론트 응답)은 필드명 camelCase 로 직렬화 (다른 DTO와 동일 규약)
@Getter
public class ChallengeProposalResponseDto {

    @JsonAlias("created_at")
    private String createdAt;

    private String title;

    private String description;

    private String category;

    @JsonAlias("challenge_sub_type")
    private String challengeSubType;

    @JsonAlias("challenge_type")
    private String challengeType;

    private Long target;

    @JsonAlias("estimated_saving")
    private Long estimatedSaving;

    private String ticker;

    @Setter
    private String tickerName;
}
