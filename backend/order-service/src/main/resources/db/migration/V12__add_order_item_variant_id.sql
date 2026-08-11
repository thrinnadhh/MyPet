ALTER TABLE orders.order_items
ADD COLUMN IF NOT EXISTS variant_id UUID;
