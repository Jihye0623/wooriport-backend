package com.wooriport.core_api.base.dto.challenge;

import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;

import java.util.List;

@Getter
public class ChallengeAdjustRequestDto {

    @NotEmpty
    private List<PreviousProposalItem> previousProposals;

    @Getter
    public static class PreviousProposalItem {
        private String title;
        private String description;
        private String challengeSubType;
        private String challengeType;
        private String category;
        private Long estimatedSaving;
        private String ticker;
        private String feedback;
    }
}
