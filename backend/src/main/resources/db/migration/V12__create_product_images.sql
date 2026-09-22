CREATE TABLE product_images (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    product_id BIGINT NOT NULL,
    image_data MEDIUMBLOB NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    file_name VARCHAR(255),
    sort_order INT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_product_images_product FOREIGN KEY(product_id) REFERENCES products(id) ON DELETE CASCADE
);
CREATE INDEX idx_product_images_product ON product_images(product_id, sort_order, id);
