DROP TABLE IF EXISTS product_images_rebuilt;

CREATE TABLE product_images_rebuilt (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    product_id BIGINT NOT NULL,
    image_data LONGBLOB NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    file_name VARCHAR(255),
    sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_product_images_product_v20
        FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE,
    INDEX idx_product_images_product (product_id, sort_order, id)
) ENGINE=InnoDB;

INSERT INTO product_images_rebuilt (
    id,
    product_id,
    image_data,
    content_type,
    file_name,
    sort_order,
    created_at
)
SELECT
    id,
    product_id,
    image_data,
    COALESCE(NULLIF(content_type, ''), 'image/jpeg'),
    file_name,
    sort_order,
    created_at
FROM product_images
WHERE image_data IS NOT NULL
  AND OCTET_LENGTH(image_data) > 0;

DROP TABLE product_images;
RENAME TABLE product_images_rebuilt TO product_images;
