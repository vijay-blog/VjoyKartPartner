SET @product_image_created_at_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'product_images'
      AND column_name = 'created_at'
);

SET @add_product_image_created_at = IF(
    @product_image_created_at_exists = 0,
    'ALTER TABLE product_images ADD COLUMN created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)',
    'SELECT 1'
);

PREPARE add_product_image_created_at_statement FROM @add_product_image_created_at;
EXECUTE add_product_image_created_at_statement;
DEALLOCATE PREPARE add_product_image_created_at_statement;
