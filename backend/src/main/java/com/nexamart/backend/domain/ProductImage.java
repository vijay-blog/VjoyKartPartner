package com.nexamart.backend.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "product_images", indexes = {
    @Index(name = "idx_product_images_product", columnList = "product_id,sort_order")
})
public class ProductImage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Lob
    @Column(name = "image_data", nullable = false, columnDefinition = "MEDIUMBLOB")
    private byte[] imageData;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "file_name", length = 255)
    private String fileName;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public Long getId() { return id; }
    public Product getProduct() { return product; }
    public void setProduct(Product product) { this.product = product; }
    public byte[] getImageData() { return imageData; }
    public void setImageData(byte[] imageData) { this.imageData = imageData; }
    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
    public Instant getCreatedAt() { return createdAt; }
}
