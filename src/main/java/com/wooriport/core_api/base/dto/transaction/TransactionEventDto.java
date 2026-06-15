package com.wooriport.core_api.base.dto.transaction;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

// mock-server/mock_payment.py 가 발행하는 transaction-events 페이로드
@Getter
@Setter
@NoArgsConstructor
public class TransactionEventDto {

    // PG 거래고유번호(멱등키). Phase 1부터 중복 적재 방지에 사용. (실제 카드결제 승인번호 역할)
    @JsonProperty("event_id")
    private String eventId;

    @JsonProperty("asset_number")
    private String assetNumber;

    private Long amount;

    private String category;

    @JsonProperty("sender_name")
    private String senderName;

    private LocalDateTime transactionAt;

    // 발행 시각(epoch ms). E2E 지연 측정용.
    @JsonProperty("producedAt")
    private Long producedAt;
}
