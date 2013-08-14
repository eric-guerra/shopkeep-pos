package com.ericguerra.shopkeep.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "sale_lines")
public class SaleLine {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "sale_id")
    private Sale sale;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    /** Snapshot of the product at sale time so receipts never change retroactively. */
    @Column(nullable = false, length = 32) private String sku;
    @Column(nullable = false, length = 120) private String name;
    @Column(name = "unit_price_cents", nullable = false) private long unitPriceCents;
    @Column(name = "unit_cost_cents", nullable = false) private long unitCostCents;
    @Column(nullable = false) private int quantity;
    @Column(name = "discount_cents", nullable = false) private long discountCents;
    @Column(name = "tax_cents", nullable = false) private long taxCents;
    @Column(name = "returned_quantity", nullable = false) private int returnedQuantity;

    protected SaleLine() {}

    public SaleLine(Long productId, String sku, String name, long unitPriceCents, long unitCostCents,
                    int quantity, long discountCents, long taxCents) {
        this.productId = productId;
        this.sku = sku;
        this.name = name;
        this.unitPriceCents = unitPriceCents;
        this.unitCostCents = unitCostCents;
        this.quantity = quantity;
        this.discountCents = discountCents;
        this.taxCents = taxCents;
    }

    public long netCents() {
        return unitPriceCents * quantity - discountCents;
    }

    public int returnable() {
        return quantity - returnedQuantity;
    }

    public void markReturned(int qty) {
        if (qty <= 0 || qty > returnable()) throw new IllegalArgumentException("invalid return quantity " + qty);
        returnedQuantity += qty;
    }

    void setSale(Sale sale) { this.sale = sale; }
    public Long getId() { return id; }
    public Long getProductId() { return productId; }
    public String getSku() { return sku; }
    public String getName() { return name; }
    public long getUnitPriceCents() { return unitPriceCents; }
    public long getUnitCostCents() { return unitCostCents; }
    public int getQuantity() { return quantity; }
    public long getDiscountCents() { return discountCents; }
    public long getTaxCents() { return taxCents; }
    public int getReturnedQuantity() { return returnedQuantity; }
}
