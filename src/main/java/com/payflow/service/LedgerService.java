package com.payflow.service;

import com.payflow.dto.LedgerEntryDto;
import com.payflow.entity.LedgerEntry;
import com.payflow.entity.PaymentTransaction;
import com.payflow.enums.LedgerAccountType;
import com.payflow.enums.LedgerEntryType;
import com.payflow.exception.PayflowException;
import com.payflow.repository.LedgerEntryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class LedgerService {

    private static final Logger log = LoggerFactory.getLogger(LedgerService.class);

    private final LedgerEntryRepository ledgerEntryRepository;

    public LedgerService(LedgerEntryRepository ledgerEntryRepository) {
        this.ledgerEntryRepository = ledgerEntryRepository;
    }

    /**
     * Atomically records a double-entry pair (DEBIT and CREDIT) for a given transaction.
     * Enforces the fundamental accounting equation: Debit Amount == Credit Amount.
     */
    @Transactional
    public void recordDoubleEntry(PaymentTransaction transaction,
                                  LedgerAccountType debitAccountType,
                                  String debitAccountId,
                                  BigDecimal debitBalanceAfter,
                                  LedgerAccountType creditAccountType,
                                  String creditAccountId,
                                  BigDecimal creditBalanceAfter,
                                  BigDecimal amount,
                                  String description) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new PayflowException("Ledger amount must be strictly positive: " + amount);
        }

        // 1. Debit Entry
        LedgerEntry debitEntry = new LedgerEntry(
                transaction,
                debitAccountType,
                debitAccountId,
                LedgerEntryType.DEBIT,
                amount,
                transaction.getCurrency(),
                debitBalanceAfter,
                description + " [DEBIT]"
        );

        // 2. Credit Entry
        LedgerEntry creditEntry = new LedgerEntry(
                transaction,
                creditAccountType,
                creditAccountId,
                LedgerEntryType.CREDIT,
                amount,
                transaction.getCurrency(),
                creditBalanceAfter,
                description + " [CREDIT]"
        );

        ledgerEntryRepository.save(debitEntry);
        ledgerEntryRepository.save(creditEntry);

        log.info("Recorded double-entry ledger: txnRef={}, amount={}, DEBIT={}:{}, CREDIT={}:{}",
                transaction.getTransactionRef(), amount, debitAccountType, debitAccountId, creditAccountType, creditAccountId);
    }

    @Transactional(readOnly = true)
    public List<LedgerEntryDto> getEntriesByTransaction(String transactionId) {
        return ledgerEntryRepository.findByTransactionIdOrderByCreatedAtAsc(transactionId)
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<LedgerEntryDto> getEntriesByAccount(String accountId, LedgerAccountType accountType) {
        return ledgerEntryRepository.findByAccountIdAndAccountTypeOrderByCreatedAtDesc(accountId, accountType)
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    public LedgerEntryDto toDto(LedgerEntry entry) {
        return new LedgerEntryDto(
                entry.getId(),
                entry.getTransaction().getId(),
                entry.getAccountType(),
                entry.getAccountId(),
                entry.getEntryType(),
                entry.getAmount(),
                entry.getCurrency(),
                entry.getBalanceAfter(),
                entry.getDescription(),
                entry.getCreatedAt()
        );
    }
}
