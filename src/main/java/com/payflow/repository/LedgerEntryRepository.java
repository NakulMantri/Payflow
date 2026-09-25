package com.payflow.repository;

import com.payflow.entity.LedgerEntry;
import com.payflow.enums.LedgerAccountType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {
    List<LedgerEntry> findByTransactionIdOrderByCreatedAtAsc(String transactionId);
    List<LedgerEntry> findByAccountIdAndAccountTypeOrderByCreatedAtDesc(String accountId, LedgerAccountType accountType);
}
