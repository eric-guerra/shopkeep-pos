package com.ericguerra.shopkeep.repo;

import com.ericguerra.shopkeep.domain.Product;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {
    Optional<Product> findBySku(String sku);

    Optional<Product> findByBarcode(String barcode);

    @Query("""
            select p from Product p
            where p.active = true and (:q is null
               or lower(p.name) like lower(concat('%', :q, '%'))
               or lower(p.sku) like lower(concat('%', :q, '%'))
               or p.barcode = :q)
            order by p.name""")
    Page<Product> search(@Param("q") String q, Pageable pageable);

    @Query("select p from Product p where p.active = true and p.stockOnHand <= p.reorderLevel order by p.stockOnHand")
    List<Product> findLowStock();

    /** Row locks for checkout so two registers can't oversell the last unit. Ordered by id to avoid deadlocks. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.id in :ids order by p.id")
    List<Product> lockAll(@Param("ids") Collection<Long> ids);
}
