CREATE TABLE products (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    sku            VARCHAR(32)  NOT NULL UNIQUE,
    barcode        VARCHAR(32)  UNIQUE,
    name           VARCHAR(120) NOT NULL,
    category       VARCHAR(60),
    price_cents    BIGINT       NOT NULL,
    cost_cents     BIGINT       NOT NULL,
    taxable        BOOLEAN      NOT NULL DEFAULT TRUE,
    stock_on_hand  INT          NOT NULL DEFAULT 0,
    reorder_level  INT          NOT NULL DEFAULT 0,
    active         BOOLEAN      NOT NULL DEFAULT TRUE,
    version        BIGINT       NOT NULL DEFAULT 0,
    updated_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_products_name ON products (name);

CREATE TABLE sales (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    receipt_no      VARCHAR(20) NOT NULL UNIQUE,
    cashier         VARCHAR(60) NOT NULL,
    status          VARCHAR(20) NOT NULL,
    subtotal_cents  BIGINT      NOT NULL,
    discount_cents  BIGINT      NOT NULL,
    tax_cents       BIGINT      NOT NULL,
    total_cents     BIGINT      NOT NULL,
    tendered_cents  BIGINT      NOT NULL,
    change_cents    BIGINT      NOT NULL,
    refunded_cents  BIGINT      NOT NULL DEFAULT 0,
    payment_method  VARCHAR(10) NOT NULL,
    created_at      TIMESTAMP   NOT NULL
);
CREATE INDEX idx_sales_created ON sales (created_at);

CREATE TABLE sale_lines (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    sale_id            BIGINT       NOT NULL,
    product_id         BIGINT       NOT NULL,
    sku                VARCHAR(32)  NOT NULL,
    name               VARCHAR(120) NOT NULL,
    unit_price_cents   BIGINT       NOT NULL,
    unit_cost_cents    BIGINT       NOT NULL,
    quantity           INT          NOT NULL,
    discount_cents     BIGINT       NOT NULL,
    tax_cents          BIGINT       NOT NULL,
    returned_quantity  INT          NOT NULL DEFAULT 0,
    CONSTRAINT fk_line_sale FOREIGN KEY (sale_id) REFERENCES sales (id),
    CONSTRAINT fk_line_product FOREIGN KEY (product_id) REFERENCES products (id)
);

CREATE TABLE stock_movements (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id     BIGINT      NOT NULL,
    delta          INT         NOT NULL,
    balance_after  INT         NOT NULL,
    reason         VARCHAR(10) NOT NULL,
    reference      VARCHAR(40),
    performed_by   VARCHAR(60) NOT NULL,
    created_at     TIMESTAMP   NOT NULL,
    CONSTRAINT fk_movement_product FOREIGN KEY (product_id) REFERENCES products (id)
);
CREATE INDEX idx_movements_product ON stock_movements (product_id, id);
