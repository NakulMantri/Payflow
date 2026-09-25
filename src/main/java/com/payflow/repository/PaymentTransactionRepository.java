package com.payflow.repository;

import com.payflow.entity.PaymentTransaction;
import com.payflow.enums.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, String> {

    Optional<PaymentTransaction> findByIdempotencyKey(String idempotencyKey);

    Optional<PaymentTransaction> findByTransactionRef(String transactionRef);

    List<PaymentTransaction> findByUserIdOrderByCreatedAtDesc(Long userId);

    @Query("SELECT t FROM PaymentTransaction t WHERE t.status = :status AND t.nextRetryAt <= :now ORDER BY t.nextRetryAt ASC")
    List<PaymentTransaction> findPendingRetries(@Param("status") PaymentStatus status, @Param("now") LocalDateTime now);

    @Query("SELECT t FROM PaymentTransaction t WHERE t.status IN :statuses AND t.createdAt <= :beforeTime")
    List<PaymentTransaction> findTransactionsForReconciliation(
        @Param("statuses") List<PaymentStatus> statuses,
        @Param("beforeTime") LocalDateTime beforeTime
    );
}
