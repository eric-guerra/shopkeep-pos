package com.ericguerra.shopkeep.repo;

import com.ericguerra.shopkeep.domain.StockMovement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {
    List<StockMovement> findTop50ByProductIdOrderByIdDesc(Long productId);
}
