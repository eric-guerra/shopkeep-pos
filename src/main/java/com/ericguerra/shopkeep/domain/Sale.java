package com.ericguerra.shopkeep.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "sales")
public class Sale {
    public enum Status { COMPLETED, VOIDED, PARTIALLY_RETURNED, RETURNED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "receipt_no", nullable = false, unique = true, length = 20)
    private String receiptNo;

    @Column(nullable = false, length = 60)
    private String cashier;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.COMPLETED;

    @Column(name = "subtotal_cents", nullable = false) private long subtotalCents;
    @Column(name = "discount_cents", nullable = false) private long discountCents;
    @Column(name = "tax_cents", nullable = false) private long taxCents;
    @Column(name = "total_cents", nullable = false) private long totalCents;
    @Column(name = "tendered_cents", nullable = false) private long tenderedCents;
    @Column(name = "change_cents", nullable = false) private long changeCents;
    @Column(name = "refunded_cents", nullable = false) private long refundedCents;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 10)
    private PaymentMethod paymentMethod;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @OneToMany(mappedBy = "sale", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("id")
    private List<SaleLine> lines = new ArrayList<>();

    protected Sale() {}

    public Sale(String receiptNo, String cashier, PaymentMethod method) {
        this.receiptNo = receiptNo;
        this.cashier = cashier;
        this.paymentMethod = method;
    }

    public void addLine(SaleLine line) {
        line.setSale(this);
        lines.add(line);
    }

    public void setTotals(long subtotal, long discount, long tax, long total, long tendered, long change) {
        this.subtotalCents = subtotal;
        this.discountCents = discount;
        this.taxCents = tax;
        this.totalCents = total;
        this.tenderedCents = tendered;
        this.changeCents = change;
    }

    public void recordRefund(long cents) {
        this.refundedCents += cents;
        boolean allReturned = lines.stream().allMatch(l -> l.getReturnedQuantity() == l.getQuantity());
        this.status = allReturned ? Status.RETURNED : Status.PARTIALLY_RETURNED;
    }

    public void voidSale() {
        this.status = Status.VOIDED;
    }

    public Long getId() { return id; }
    public String getReceiptNo() { return receiptNo; }
    public String getCashier() { return cashier; }
    public Status getStatus() { return status; }
    public long getSubtotalCents() { return subtotalCents; }
    public long getDiscountCents() { return discountCents; }
    public long getTaxCents() { return taxCents; }
    public long getTotalCents() { return totalCents; }
    public long getTenderedCents() { return tenderedCents; }
    public long getChangeCents() { return changeCents; }
    public long getRefundedCents() { return refundedCents; }
    public PaymentMethod getPaymentMethod() { return paymentMethod; }
    public Instant getCreatedAt() { return createdAt; }
    public List<SaleLine> getLines() { return lines; }
}
