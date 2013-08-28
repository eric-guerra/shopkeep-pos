package com.ericguerra.shopkeep.repo;

import com.ericguerra.shopkeep.domain.Sale;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface SaleRepository extends JpaRepository<Sale, Long> {
    Optional<Sale> findByReceiptNo(String receiptNo);

    Page<Sale> findAllByOrderByCreatedAtDesc(Pageable pageable);

    @Query("select s from Sale s where s.createdAt >= :from and s.createdAt < :to and s.status <> com.ericguerra.shopkeep.domain.Sale.Status.VOIDED")
    List<Sale> findCountedBetween(@Param("from") Instant from, @Param("to") Instant to);

    @Query("select count(s) from Sale s where s.createdAt >= :from")
    long countSince(@Param("from") Instant from);
}
