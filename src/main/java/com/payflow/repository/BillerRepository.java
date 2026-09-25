package com.payflow.repository;

import com.payflow.entity.Biller;
import com.payflow.enums.BillerCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface BillerRepository extends JpaRepository<Biller, Long> {
    Optional<Biller> findByCode(String code);
    List<Biller> findByCategory(BillerCategory category);
    List<Biller> findByStatus(String status);
}
