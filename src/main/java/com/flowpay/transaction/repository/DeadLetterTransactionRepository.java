package com.flowpay.transaction.repository;

import com.flowpay.transaction.entity.DeadLetterTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DeadLetterTransactionRepository extends JpaRepository<DeadLetterTransaction, UUID> {

    Optional<DeadLetterTransaction> findByTransactionId(UUID transactionId);

    List<DeadLetterTransaction> findByStatus(String status);

    Page<DeadLetterTransaction> findByStatus(String status, Pageable pageable);

    long countByStatus(String status);

    boolean existsByTransactionId(UUID transactionId);
}
