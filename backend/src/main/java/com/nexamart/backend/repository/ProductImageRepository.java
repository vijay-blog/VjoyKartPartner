package com.nexamart.backend.repository;

import com.nexamart.backend.domain.ProductImage;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ProductImageRepository extends JpaRepository<ProductImage, Long> {
    List<ProductImage> findByProductIdOrderBySortOrderAscIdAsc(Long productId);
    long countByProductId(Long productId);
    void deleteByProductId(Long productId);
}
