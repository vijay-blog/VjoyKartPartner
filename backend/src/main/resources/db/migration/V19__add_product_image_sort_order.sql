SET @product_image_sort_order_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'product_images'
      AND column_name = 'sort_order'
);

SET @add_product_image_sort_order = IF(
    @product_image_sort_order_exists = 0,
    'ALTER TABLE product_images ADD COLUMN sort_order INT NOT NULL DEFAULT 0',
    'SELECT 1'
);

PREPARE add_product_image_sort_order_statement FROM @add_product_image_sort_order;
EXECUTE add_product_image_sort_order_statement;
DEALLOCATE PREPARE add_product_image_sort_order_statement;
