-- Phase 4: batches become pullable records (change_seq) and point at the purchase line they came from.
ALTER TABLE batch
    ADD COLUMN purchase_item_id BINARY(16) NULL AFTER purchase_id,
    ADD COLUMN change_seq BIGINT NOT NULL DEFAULT 0,
    ADD KEY idx_batch_business_seq (business_id, change_seq),
    ADD KEY idx_batch_business_purchase (business_id, purchase_id);

UPDATE batch b JOIN purchase_delivery_item d ON d.id = b.delivery_item_id
SET b.purchase_item_id = d.purchase_item_id;

ALTER TABLE batch
    MODIFY COLUMN purchase_item_id BINARY(16) NOT NULL,
    ADD CONSTRAINT fk_batch_purchase_item FOREIGN KEY (business_id, purchase_item_id)
        REFERENCES purchase_item (business_id, id) ON DELETE RESTRICT;

-- Item-wise history: purchase lines of one item, newest first.
CREATE INDEX idx_purchase_business_supplier_date ON purchase (business_id, supplier_id, purchase_date);
