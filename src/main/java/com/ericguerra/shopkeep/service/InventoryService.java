package com.ericguerra.shopkeep.service;

import com.ericguerra.shopkeep.domain.Product;
import com.ericguerra.shopkeep.domain.StockMovement;
import com.ericguerra.shopkeep.repo.ProductRepository;
import com.ericguerra.shopkeep.repo.StockMovementRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
public class InventoryService {
    private final ProductRepository products;
    private final StockMovementRepository movements;

    public InventoryService(ProductRepository products, StockMovementRepository movements) {
        this.products = products;
        this.movements = movements;
    }

    /** Must run inside the caller's transaction with the product already locked. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void apply(Product product, int delta, StockMovement.Reason reason, String reference, String user) {
        product.adjustStock(delta);
        movements.save(new StockMovement(product.getId(), delta, product.getStockOnHand(), reason, reference, user));
    }

    @Transactional
    public Product adjust(Long productId, int delta, StockMovement.Reason reason, String note, String user) {
        if (delta == 0) throw PosException.invalid("delta must not be zero");
        if (reason == StockMovement.Reason.SALE || reason == StockMovement.Reason.RETURN || reason == StockMovement.Reason.VOID) {
            throw PosException.invalid("use checkout/returns for " + reason);
        }
        Product p = products.lockAll(List.of(productId)).stream().findFirst().orElseThrow(() -> PosException.notFound("Product", productId));
        if (p.getStockOnHand() + delta < 0) {
            throw PosException.conflict("NEGATIVE_STOCK", "adjustment would make stock negative",
                    Map.of("onHand", p.getStockOnHand(), "delta", delta));
        }
        apply(p, delta, reason, note, user);
        return p;
    }

    /** Physical count: sets stock to the counted number, recording the variance. */
    @Transactional
    public Product count(Long productId, int counted, String user) {
        if (counted < 0) throw PosException.invalid("count must not be negative");
        Product p = products.lockAll(List.of(productId)).stream().findFirst().orElseThrow(() -> PosException.notFound("Product", productId));
        int variance = counted - p.getStockOnHand();
        if (variance != 0) apply(p, variance, StockMovement.Reason.COUNT, "cycle count", user);
        return p;
    }

    public List<StockMovement> history(Long productId) {
        return movements.findTop50ByProductIdOrderByIdDesc(productId);
    }
}
