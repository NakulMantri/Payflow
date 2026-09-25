package com.payflow.repository;

import com.payflow.entity.ReconciliationRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ReconciliationRecordRepository extends JpaRepository<ReconciliationRecord, Long> {
    List<ReconciliationRecord> findByBatchId(String batchId);
    List<ReconciliationRecord> findByResolvedOrderByCreatedAtDesc(boolean resolved);
}
