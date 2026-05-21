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

    @JsonProperty("asset_number")
    private String assetNumber;

    private Long amount;

    private String category;

    @JsonProperty("sender_name")
    private String senderName;

    private LocalDateTime transactionAt;
}
