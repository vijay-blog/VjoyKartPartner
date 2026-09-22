SET @product_image_file_name_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'product_images'
      AND column_name = 'file_name'
);

SET @add_product_image_file_name = IF(
    @product_image_file_name_exists = 0,
    'ALTER TABLE product_images ADD COLUMN file_name VARCHAR(255) NULL',
    'SELECT 1'
);

PREPARE add_product_image_file_name_statement FROM @add_product_image_file_name;
EXECUTE add_product_image_file_name_statement;
DEALLOCATE PREPARE add_product_image_file_name_statement;

SET @product_image_data_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'product_images'
      AND column_name = 'image_data'
);

SET @add_product_image_data = IF(
    @product_image_data_exists = 0,
    'ALTER TABLE product_images ADD COLUMN image_data MEDIUMBLOB NULL',
    'SELECT 1'
);

PREPARE add_product_image_data_statement FROM @add_product_image_data;
EXECUTE add_product_image_data_statement;
DEALLOCATE PREPARE add_product_image_data_statement;

SET @product_image_content_type_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'product_images'
      AND column_name = 'content_type'
);

SET @add_product_image_content_type = IF(
    @product_image_content_type_exists = 0,
    'ALTER TABLE product_images ADD COLUMN content_type VARCHAR(100) NULL',
    'SELECT 1'
);

PREPARE add_product_image_content_type_statement FROM @add_product_image_content_type;
EXECUTE add_product_image_content_type_statement;
DEALLOCATE PREPARE add_product_image_content_type_statement;
