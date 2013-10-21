package com.ericguerra.shopkeep.web;

import com.ericguerra.shopkeep.domain.Sale;
import com.ericguerra.shopkeep.repo.SaleRepository;
import com.ericguerra.shopkeep.service.CheckoutService;
import com.ericguerra.shopkeep.service.PosException;
import com.ericguerra.shopkeep.service.PriceCalculator;
import com.ericguerra.shopkeep.service.ReportService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api")
public class SaleController {
    public record QuoteRequest(List<CheckoutService.CartLine> lines, long orderDiscountCents) {}

    public record ReturnRequest(List<CheckoutService.ReturnLine> lines) {}

    private final CheckoutService checkout;
    private final SaleRepository sales;
    private final ReportService reports;

    public SaleController(CheckoutService checkout, SaleRepository sales, ReportService reports) {
        this.checkout = checkout;
        this.sales = sales;
        this.reports = reports;
    }

    @PostMapping("/sales/quote")
    public PriceCalculator.Totals quote(@RequestBody QuoteRequest req) {
        return checkout.quote(req.lines() == null ? List.of() : req.lines(), req.orderDiscountCents());
    }

    @PostMapping("/sales")
    @ResponseStatus(HttpStatus.CREATED)
    public Sale checkout(@RequestBody CheckoutService.CheckoutRequest req, Authentication auth) {
        if (req.orderDiscountCents() > 0 && auth.getAuthorities().stream().noneMatch(a -> a.getAuthority().equals("ROLE_MANAGER"))) {
            throw new PosException(HttpStatus.FORBIDDEN, "MANAGER_REQUIRED", "order discounts need a manager", java.util.Map.of());
        }
        return checkout.checkout(req, auth.getName());
    }

    @GetMapping("/sales")
    public Page<Sale> recent(@RequestParam(defaultValue = "0") int page) {
        return sales.findAllByOrderByCreatedAtDesc(PageRequest.of(Math.max(0, page), 25));
    }

    @GetMapping("/sales/{receiptNo}")
    public Sale get(@PathVariable String receiptNo) {
        return sales.findByReceiptNo(receiptNo).orElseThrow(() -> PosException.notFound("Sale", receiptNo));
    }

    @PostMapping("/sales/{receiptNo}/returns")
    public Sale processReturn(@PathVariable String receiptNo, @RequestBody ReturnRequest req, Authentication auth) {
        return checkout.processReturn(receiptNo, req.lines(), auth.getName());
    }

    @PostMapping("/sales/{receiptNo}/void")
    @PreAuthorize("hasRole('MANAGER')")
    public Sale voidSale(@PathVariable String receiptNo, Authentication auth) {
        return checkout.voidSale(receiptNo, auth.getName());
    }

    @GetMapping("/reports/daily")
    public ReportService.DailyReport daily(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return reports.daily(date != null ? date : LocalDate.now());
    }

    @GetMapping("/me")
    public java.util.Map<String, Object> me(Authentication auth) {
        return java.util.Map.of("username", auth.getName(),
                "manager", auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_MANAGER")));
    }
}
