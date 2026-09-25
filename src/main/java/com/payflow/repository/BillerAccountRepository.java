package com.payflow.repository;

import com.payflow.entity.BillerAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface BillerAccountRepository extends JpaRepository<BillerAccount, Long> {
    List<BillerAccount> findByUserId(Long userId);
    Optional<BillerAccount> findByUserIdAndBillerIdAndConsumerNumber(Long userId, Long billerId, String consumerNumber);
}
