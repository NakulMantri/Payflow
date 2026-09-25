package com.payflow.repository;

import com.payflow.entity.IdempotencyRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;

@Repository
public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, String> {
    void deleteByExpiresAtBefore(LocalDateTime time);
}
