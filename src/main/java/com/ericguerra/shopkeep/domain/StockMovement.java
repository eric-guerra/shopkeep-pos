package com.ericguerra.shopkeep.domain;

import jakarta.persistence.*;

import java.time.Instant;

/** Append-only inventory ledger: every change to stock on hand has a row explaining why. */
@Entity
@Table(name = "stock_movements")
public class StockMovement {
    public enum Reason { SALE, RETURN, VOID, RECEIVE, ADJUST, DAMAGE, COUNT }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(nullable = false)
    private int delta;

    @Column(name = "balance_after", nullable = false)
    private int balanceAfter;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Reason reason;

    @Column(length = 40)
    private String reference;

    @Column(name = "performed_by", nullable = false, length = 60)
    private String performedBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected StockMovement() {}

    public StockMovement(Long productId, int delta, int balanceAfter, Reason reason, String reference, String performedBy) {
        this.productId = productId;
        this.delta = delta;
        this.balanceAfter = balanceAfter;
        this.reason = reason;
        this.reference = reference;
        this.performedBy = performedBy;
    }

    public Long getId() { return id; }
    public Long getProductId() { return productId; }
    public int getDelta() { return delta; }
    public int getBalanceAfter() { return balanceAfter; }
    public Reason getReason() { return reason; }
    public String getReference() { return reference; }
    public String getPerformedBy() { return performedBy; }
    public Instant getCreatedAt() { return createdAt; }
}
