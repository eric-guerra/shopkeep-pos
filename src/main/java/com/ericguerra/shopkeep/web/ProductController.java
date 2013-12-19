package com.ericguerra.shopkeep.web;

import com.ericguerra.shopkeep.domain.Product;
import com.ericguerra.shopkeep.domain.StockMovement;
import com.ericguerra.shopkeep.repo.ProductRepository;
import com.ericguerra.shopkeep.service.InventoryService;
import com.ericguerra.shopkeep.service.PosException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class ProductController {
    public record ProductForm(
            @NotBlank @Size(max = 32) @Pattern(regexp = "[A-Z0-9-]+", message = "SKU must be uppercase letters, digits or dashes") String sku,
            @Size(max = 32) String barcode,
            @NotBlank @Size(max = 120) String name,
            @Size(max = 60) String category,
            @NotNull @Min(0) Long priceCents,
            @NotNull @Min(0) Long costCents,
            boolean taxable,
            @Min(0) int reorderLevel,
            @Min(0) int initialStock) {}

    public record AdjustForm(@NotNull Integer delta, @NotNull StockMovement.Reason reason, @Size(max = 40) String note) {}

    public record CountForm(@NotNull @Min(0) Integer counted) {}

    private final ProductRepository products;
    private final InventoryService inventory;

    public ProductController(ProductRepository products, InventoryService inventory) {
        this.products = products;
        this.inventory = inventory;
    }

    @GetMapping("/products")
    public Page<Product> search(@RequestParam(required = false) String q,
                                @RequestParam(defaultValue = "0") int page,
                                @RequestParam(defaultValue = "25") int size) {
        String query = q == null || q.isBlank() ? null : q.trim();
        return products.search(query, PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size))));
    }

    /** Barcode scanner lookup. */
    @GetMapping("/products/scan/{code}")
    public Product scan(@PathVariable String code) {
        return products.findByBarcode(code).or(() -> products.findBySku(code.toUpperCase()))
                .filter(Product::isActive)
                .orElseThrow(() -> PosException.notFound("Barcode", code));
    }

    @GetMapping("/products/low-stock")
    public List<Product> lowStock() {
        return products.findLowStock();
    }

    @PostMapping("/products")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public Product create(@Valid @RequestBody ProductForm form, Authentication auth) {
        Product p = new Product(form.sku(), form.name(), form.priceCents(), form.costCents());
        apply(p, form);
        products.saveAndFlush(p);
        if (form.initialStock() > 0) {
            inventory.adjust(p.getId(), form.initialStock(), StockMovement.Reason.RECEIVE, "initial stock", auth.getName());
        }
        return p;
    }

    @PutMapping("/products/{id}")
    @Transactional
    public Product update(@PathVariable Long id, @Valid @RequestBody ProductForm form,
                          @RequestHeader(value = "If-Match", required = false) Long expectedVersion) {
        Product p = products.findById(id).orElseThrow(() -> PosException.notFound("Product", id));
        if (expectedVersion != null && expectedVersion != p.getVersion()) {
            throw PosException.conflict("STALE", "product was changed by someone else", java.util.Map.of("version", p.getVersion()));
        }
        p.setSku(form.sku());
        p.setPriceCents(form.priceCents());
        p.setCostCents(form.costCents());
        apply(p, form);
        p.touch();
        return p;
    }

    @DeleteMapping("/products/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void deactivate(@PathVariable Long id) {
        // Products are never hard-deleted: historic sale lines reference them.
        products.findById(id).orElseThrow(() -> PosException.notFound("Product", id)).setActive(false);
    }

    @PostMapping("/inventory/{id}/adjust")
    public Product adjust(@PathVariable Long id, @Valid @RequestBody AdjustForm form, Authentication auth) {
        return inventory.adjust(id, form.delta(), form.reason(), form.note(), auth.getName());
    }

    @PostMapping("/inventory/{id}/count")
    public Product count(@PathVariable Long id, @Valid @RequestBody CountForm form, Authentication auth) {
        return inventory.count(id, form.counted(), auth.getName());
    }

    @GetMapping("/inventory/{id}/history")
    public List<StockMovement> history(@PathVariable Long id) {
        return inventory.history(id);
    }

    private static void apply(Product p, ProductForm form) {
        p.setBarcode(form.barcode() == null || form.barcode().isBlank() ? null : form.barcode().trim());
        p.setName(form.name().trim());
        p.setCategory(form.category());
        p.setTaxable(form.taxable());
        p.setReorderLevel(form.reorderLevel());
    }
}
