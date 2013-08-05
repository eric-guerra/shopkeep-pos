package com.ericguerra.shopkeep.domain;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "products")
public class Product {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 32)
    private String sku;

    @Column(unique = true, length = 32)
    private String barcode;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 60)
    private String category;

    /** Price and cost in cents. */
    @Column(name = "price_cents", nullable = false)
    private long priceCents;

    @Column(name = "cost_cents", nullable = false)
    private long costCents;

    @Column(nullable = false)
    private boolean taxable = true;

    @Column(name = "stock_on_hand", nullable = false)
    private int stockOnHand;

    @Column(name = "reorder_level", nullable = false)
    private int reorderLevel;

    @Column(nullable = false)
    private boolean active = true;

    @Version
    private long version;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected Product() {}

    public Product(String sku, String name, long priceCents, long costCents) {
        this.sku = sku;
        this.name = name;
        this.priceCents = priceCents;
        this.costCents = costCents;
    }

    public void adjustStock(int delta) {
        this.stockOnHand += delta;
        this.updatedAt = Instant.now();
    }

    public boolean isLowStock() {
        return active && stockOnHand <= reorderLevel;
    }

    public Long getId() { return id; }
    public String getSku() { return sku; }
    public void setSku(String sku) { this.sku = sku; }
    public String getBarcode() { return barcode; }
    public void setBarcode(String barcode) { this.barcode = barcode; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public long getPriceCents() { return priceCents; }
    public void setPriceCents(long priceCents) { this.priceCents = priceCents; }
    public long getCostCents() { return costCents; }
    public void setCostCents(long costCents) { this.costCents = costCents; }
    public boolean isTaxable() { return taxable; }
    public void setTaxable(boolean taxable) { this.taxable = taxable; }
    public int getStockOnHand() { return stockOnHand; }
    public int getReorderLevel() { return reorderLevel; }
    public void setReorderLevel(int reorderLevel) { this.reorderLevel = reorderLevel; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public long getVersion() { return version; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void touch() { this.updatedAt = Instant.now(); }
}
