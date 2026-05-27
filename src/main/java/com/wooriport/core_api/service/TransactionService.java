package com.wooriport.core_api.service;


import com.wooriport.core_api.base.dto.transaction.SalaryTransactionListResponseDto;
import com.wooriport.core_api.domain.Transactions;
import com.wooriport.core_api.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionRepository transactionRepository;

    @Transactional(readOnly = true)
    public SalaryTransactionListResponseDto getSalaryTransactions(UUID userId) {
        List<Transactions> txs = transactionRepository
                .findSalaryTransactionsByUserId(userId);

        List<SalaryTransactionListResponseDto.SalaryItem> items = txs.stream()
                .map(t -> SalaryTransactionListResponseDto.SalaryItem.builder()
                        .id(t.getId())
                        .assetId(t.getAsset().getId())
                        .institution(t.getAsset().getInstitution())
                        .amount(t.getAmount())
                        .category(t.getCategory())
                        .senderName(t.getSenderName())
                        .transactionAt(t.getTransactionAt().toString())
                        .build())
                .toList();

        return SalaryTransactionListResponseDto.builder()
                .salaryTransactions(items)
                .totalCount(items.size())
                .build();
    }
}