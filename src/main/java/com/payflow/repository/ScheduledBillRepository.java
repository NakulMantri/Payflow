package com.payflow.repository;

import com.payflow.entity.ScheduledBill;
import com.payflow.enums.ScheduledBillStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ScheduledBillRepository extends JpaRepository<ScheduledBill, Long> {
    List<ScheduledBill> findByUserIdOrderByCreatedAtDesc(Long userId);

    @Query("SELECT s FROM ScheduledBill s WHERE s.status = :status AND s.nextExecutionDate <= :now")
    List<ScheduledBill> findDueBills(@Param("status") ScheduledBillStatus status, @Param("now") LocalDateTime now);
}
