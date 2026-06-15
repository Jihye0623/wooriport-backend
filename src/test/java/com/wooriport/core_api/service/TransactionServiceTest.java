package com.wooriport.core_api.service;

import com.wooriport.core_api.base.dto.transaction.PersistedTransaction;
import com.wooriport.core_api.base.dto.transaction.TransactionEventDto;
import com.wooriport.core_api.base.exception.DuplicateEventException;
import com.wooriport.core_api.domain.Assets;
import com.wooriport.core_api.domain.Transactions;
import com.wooriport.core_api.domain.Users;
import com.wooriport.core_api.repository.AssetRepository;
import com.wooriport.core_api.repository.TransactionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock TransactionRepository transactionRepository;
    @Mock AssetRepository assetRepository;
    @InjectMocks TransactionService transactionService;

    @Test
    @DisplayName("매칭되는 asset_number 가 없으면 null 반환하고 저장하지 않는다")
    void persist_assetNotFound_returnsNullAndDoesNotSave() {
        given(assetRepository.findByAssetNumber("UNKNOWN")).willReturn(Optional.empty());

        PersistedTransaction result = transactionService.persist(event("UNKNOWN", 12500L, "식비"));

        assertThat(result).isNull();
        verify(transactionRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("CREDIT_CARD 결제는 음수로 저장하고, 결과 값객체를 정확히 채운다")
    void persist_savesAsNegativeAndReturnsValueObject() {
        UUID userId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        UUID autoId = UUID.randomUUID();
        Users user = Users.builder().id(userId).name("홍길동").autoTransferToAssetId(autoId).build();
        Assets asset = Assets.builder().id(assetId).user(user).assetNumber("5429-4494-5284-1827").build();
        given(assetRepository.findByAssetNumber("5429-4494-5284-1827")).willReturn(Optional.of(asset));

        PersistedTransaction result = transactionService.persist(event("5429-4494-5284-1827", 12500L, "식비"));

        ArgumentCaptor<Transactions> captor = ArgumentCaptor.forClass(Transactions.class);
        verify(transactionRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getAmount()).isEqualTo(-12500L); // 음수 적재

        assertThat(result).isNotNull();
        assertThat(result.userId()).isEqualTo(userId);
        assertThat(result.assetId()).isEqualTo(assetId);
        assertThat(result.autoTransferToAssetId()).isEqualTo(autoId);
        assertThat(result.category()).isEqualTo("식비");
        assertThat(result.rawAmount()).isEqualTo(12500L);      // 절대값
        assertThat(result.isIncome()).isFalse();               // 식비는 출금(입금 카테고리 아님)
    }

    @Test
    @DisplayName("이미 처리한 event_id 면 DuplicateEventException 으로 중복을 알리고 저장하지 않는다 (멱등 적재)")
    void persist_duplicateEventId_throwsAndDoesNotSave() {
        TransactionEventDto e = event("5429-4494-5284-1827", 12500L, "식비");
        e.setEventId("evt-dup-1");
        given(transactionRepository.existsByEventId("evt-dup-1")).willReturn(true);

        assertThatThrownBy(() -> transactionService.persist(e))
                .isInstanceOf(DuplicateEventException.class);
        verify(transactionRepository, never()).saveAndFlush(any());
    }

    private TransactionEventDto event(String assetNumber, long amount, String category) {
        TransactionEventDto e = new TransactionEventDto();
        e.setAssetNumber(assetNumber);
        e.setAmount(amount);
        e.setCategory(category);
        e.setSenderName("스타벅스 코리아");
        e.setTransactionAt(LocalDateTime.now());
        return e;
    }
}
