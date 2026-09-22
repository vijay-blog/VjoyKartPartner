package com.nexamart.backend.service;

import com.nexamart.backend.api.ApiModels.*;
import com.nexamart.backend.domain.*;
import com.nexamart.backend.exception.ApiException;
import com.nexamart.backend.repository.*;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Service
public class CatalogService {
    final CategoryRepository cats;
    final ProductRepository products;
    final OrderItemRepository orderItems;
    final ProductImageRepository productImages;
    final MappingService map;

    public CatalogService(CategoryRepository c, ProductRepository p, OrderItemRepository oi, ProductImageRepository pi, MappingService m) {
        cats = c; products = p; orderItems = oi; productImages = pi; map = m;
    }

    public PageResponse<CategoryResponse> categories(int page, int size, String q, Boolean active) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), 100);
        String query = q == null ? null : q.trim();
        Page<Category> p = cats.search(query == null || query.isBlank() ? null : query, active,
            PageRequest.of(safePage, safeSize, Sort.by("sortOrder").ascending().and(Sort.by("name").ascending())));
        List<CategoryResponse> list = p.getContent().stream()
            .map(c -> map.category(c, (int) products.countByCategoryId(c.getId())))
            .toList();
        return new PageResponse<>(list, p.getNumber(), p.getSize(), p.getTotalPages(), p.getTotalElements(), p.hasNext());
    }

    public PageResponse<ProductResponse> products(int page, int size, String q, Boolean active, Boolean outOfStock, Long categoryId, String sort) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), 100);
        String query = q == null ? null : q.trim();
        Sort productSort = switch (sort == null ? "NAME_A_Z" : sort.toUpperCase(Locale.ROOT)) {
            case "NEWEST" -> Sort.by(Sort.Direction.DESC, "createdAt");
            case "PRICE_LOW_TO_HIGH" -> Sort.by(Sort.Direction.ASC, "price");
            case "PRICE_HIGH_TO_LOW" -> Sort.by(Sort.Direction.DESC, "price");
            default -> Sort.by(Sort.Direction.ASC, "name");
        };
        Page<Product> p = products.search(query == null || query.isBlank() ? null : query, active, outOfStock, categoryId,
            PageRequest.of(safePage, safeSize, productSort));
        return new PageResponse<>(p.getContent().stream().map(map::product).toList(), p.getNumber(), p.getSize(),
            p.getTotalPages(), p.getTotalElements(), p.hasNext());
    }

    public CategoryResponse categoryDetail(Long id) {
        Category c = cats.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Category not found."));
        int count = (int) products.countByCategoryId(id);
        return map.category(c, count);
    }

    public CategoryResponse createCategory(Map<String, Object> b) {
        String name = stringValue(b.get("name"));
        if (name.isBlank()) throw new ApiException(HttpStatus.BAD_REQUEST, "Category name is required.");
        if (cats.findByNameIgnoreCase(name).isPresent()) throw new ApiException(HttpStatus.CONFLICT, "Category already exists.");
        Category c = new Category();
        c.setName(name); c.setDescription(stringOrNull(b.get("description")));
        c.setSortOrder(intVal(b.get("sortOrder"), 0));
        return map.category(cats.save(c), 0);
    }

    public CategoryResponse updateCategory(Long id, Map<String, Object> b) {
        Category c = cats.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Category not found."));
        if (b.containsKey("name")) {
            String name = stringValue(b.get("name"));
            if (name.isBlank()) throw new ApiException(HttpStatus.BAD_REQUEST, "Category name is required.");
            cats.findByNameIgnoreCase(name).filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> { throw new ApiException(HttpStatus.CONFLICT, "Category already exists."); });
            c.setName(name);
        }
        if (b.containsKey("description")) c.setDescription(stringOrNull(b.get("description")));
        if (b.containsKey("sortOrder")) c.setSortOrder(intVal(b.get("sortOrder"), c.getSortOrder()));
        if (b.containsKey("active")) c.setActive(booleanVal(b.get("active"), c.isActive()));
        c.touch();
        return map.category(cats.save(c), (int) products.countByCategoryId(id));
    }

    @Transactional
    public void categoryAction(Long id, ActionRequest r) {
        Category c = cats.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Category not found."));
        switch (r.action().trim().toUpperCase(Locale.ROOT)) {
            case "ACTIVATE" -> c.setActive(true);
            case "DEACTIVATE" -> c.setActive(false);
            case "DELETE" -> {
                if (products.search(null, null, null, id, PageRequest.of(0, 1)).getTotalElements() > 0)
                    throw new ApiException(HttpStatus.CONFLICT, "Category has products. Deactivate it instead.");
                cats.delete(c);
                return;
            }
            default -> throw new ApiException(HttpStatus.BAD_REQUEST, "Unsupported category action.");
        }
        c.touch(); cats.save(c);
    }

    @Transactional
    public ProductResponse createProduct(Map<String, Object> b) {
        String name = stringValue(b.get("name"));
        if (name.isBlank()) throw new ApiException(HttpStatus.BAD_REQUEST, "Product name is required.");
        Category c = categoryFromBody(b);
        BigDecimal price = money(b.get("price"), "Price is required.");
        BigDecimal discountAmount = resolveDiscountAmount(b, price);
        validateMoney(price, "Price"); validateDiscount(discountAmount, price);
        Product x = new Product();
        x.setName(name); x.setDescription(stringOrNull(b.get("description"))); x.setCategory(c);
        x.setPrice(price); x.setDiscount(discountAmount); x.setSku(stringOrNull(b.get("sku"))); x.setUnit(stringOrNull(b.get("unit")));
        x.setStock(intValNonNegative(b.get("stock"), 0)); x.setAvailable(booleanVal(b.get("available"), true));
        x.setImageUrl(stringOrNull(b.get("imageUrl")));
        return map.product(products.save(x));
    }

    @Transactional
    public ProductResponse updateProduct(Long id, Map<String, Object> b) {
        Product x = products.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Product not found."));
        if (b.containsKey("name")) {
            String name = stringValue(b.get("name"));
            if (name.isBlank()) throw new ApiException(HttpStatus.BAD_REQUEST, "Product name is required.");
            x.setName(name);
        }
        if (b.containsKey("description")) x.setDescription(stringOrNull(b.get("description")));
        if (b.containsKey("categoryId")) x.setCategory(categoryFromBody(b));
        if (b.containsKey("price")) x.setPrice(money(b.get("price"), "Price is required."));
        if (b.containsKey("discountPercent") || b.containsKey("discount")) x.setDiscount(resolveDiscountAmount(b, x.getPrice()));
        validateMoney(x.getPrice(), "Price"); validateDiscount(x.getDiscount(), x.getPrice());
        if (b.containsKey("sku")) x.setSku(stringOrNull(b.get("sku")));
        if (b.containsKey("unit")) x.setUnit(stringOrNull(b.get("unit")));
        if (b.containsKey("stock")) x.setStock(intValNonNegative(b.get("stock"), x.getStock()));
        if (b.containsKey("available")) x.setAvailable(booleanVal(b.get("available"), x.isAvailable()));
        if (b.containsKey("imageUrl")) x.setImageUrl(stringOrNull(b.get("imageUrl")));
        x.touch();
        return map.product(products.save(x));
    }

    @Transactional
    public void productAction(Long id, ActionRequest r) {
        Product x = products.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Product not found."));
        switch (r.action().trim().toUpperCase(Locale.ROOT)) {
            case "ACTIVATE" -> x.setAvailable(true);
            case "DEACTIVATE" -> x.setAvailable(false);
            case "DELETE" -> {
                if (orderItems.existsByProductId(id))
                    throw new ApiException(HttpStatus.CONFLICT, "This product is used by existing orders. Deactivate it instead of deleting it.");
                productImages.deleteByProductId(id);
                products.delete(x);
                return;
            }
            default -> throw new ApiException(HttpStatus.BAD_REQUEST, "Unsupported product action.");
        }
        x.touch(); products.save(x);
    }

    public ProductResponse productDetail(Long id) {
        return map.product(products.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Product not found.")));
    }

    @Transactional
    public ProductImage uploadProductImage(Long productId, byte[] data, String contentType, String fileName, Integer requestedSortOrder) {
        Product product = products.findById(productId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Product not found."));
        if (data == null || data.length == 0) throw new ApiException(HttpStatus.BAD_REQUEST, "Image is empty.");
        if (data.length > 5 * 1024 * 1024) throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "Each image must be 5 MB or smaller.");
        if (contentType == null || !Set.of("image/jpeg", "image/png", "image/webp").contains(contentType.toLowerCase(Locale.ROOT)))
            throw new ApiException(HttpStatus.BAD_REQUEST, "Only JPG, PNG and WEBP images are supported.");
        List<ProductImage> existing = productImages.findByProductIdOrderBySortOrderAscIdAsc(productId);
        if (existing.size() >= 3) throw new ApiException(HttpStatus.CONFLICT, "A product can have a maximum of 3 images.");
        int sortOrder = requestedSortOrder == null ? existing.size() : Math.max(0, Math.min(2, requestedSortOrder));
        if (existing.stream().anyMatch(i -> i.getSortOrder() == sortOrder)) sortOrder = existing.size();
        ProductImage image = new ProductImage();
        image.setProduct(product); image.setImageData(data); image.setContentType(contentType);
        image.setFileName(fileName == null ? "product-image" : fileName.replaceAll("[^a-zA-Z0-9._-]", "_"));
        image.setSortOrder(sortOrder);
        return productImages.save(image);
    }

    @Transactional
    public void deleteProductImage(Long productId, Long imageId) {
        Product product = products.findById(productId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Product not found."));
        ProductImage image = productImages.findById(imageId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Product image not found."));
        if (!image.getProduct().getId().equals(product.getId())) throw new ApiException(HttpStatus.NOT_FOUND, "Product image not found.");
        productImages.delete(image);
        List<ProductImage> remaining = productImages.findByProductIdOrderBySortOrderAscIdAsc(productId);
        for (int i = 0; i < remaining.size(); i++) remaining.get(i).setSortOrder(i);
        productImages.saveAll(remaining);
    }

    public List<CategoryOption> categoryOptions() {
        return cats.findAll(Sort.by("sortOrder").ascending().and(Sort.by("name").ascending())).stream()
            .filter(Category::isActive).map(c -> new CategoryOption(c.getId().toString(), c.getName())).toList();
    }

    private Category categoryFromBody(Map<String, Object> b) {
        String raw = stringValue(b.get("categoryId"));
        if (raw.isBlank()) throw new ApiException(HttpStatus.BAD_REQUEST, "Category is required.");
        try {
            return cats.findById(Long.valueOf(raw)).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Category not found."));
        } catch (NumberFormatException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Category ID is invalid.");
        }
    }

    private BigDecimal money(Object raw, String missingMessage) {
        String value = stringValue(raw);
        if (value.isBlank()) throw new ApiException(HttpStatus.BAD_REQUEST, missingMessage);
        try { return new BigDecimal(value).setScale(2, RoundingMode.HALF_UP); }
        catch (NumberFormatException e) { throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid monetary value."); }
    }

    private BigDecimal resolveDiscountAmount(Map<String, Object> b, BigDecimal price) {
        if (b.containsKey("discountPercent")) {
            String raw = stringValue(b.get("discountPercent"));
            if (raw.isBlank()) return BigDecimal.ZERO.setScale(2);
            try {
                BigDecimal percent = new BigDecimal(raw);
                if (percent.compareTo(BigDecimal.ZERO) < 0 || percent.compareTo(BigDecimal.valueOf(100)) > 0)
                    throw new ApiException(HttpStatus.BAD_REQUEST, "Discount percentage must be between 0 and 100.");
                return price.multiply(percent).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            } catch (NumberFormatException e) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid discount percentage.");
            }
        }
        if (b.containsKey("discount")) return money(b.get("discount"), "Invalid discount amount.");
        return BigDecimal.ZERO.setScale(2);
    }

    private void validateMoney(BigDecimal value, String field) {
        if (value == null || value.compareTo(BigDecimal.ZERO) < 0) throw new ApiException(HttpStatus.BAD_REQUEST, field + " cannot be negative.");
    }

    private void validateDiscount(BigDecimal discount, BigDecimal price) {
        if (discount == null || discount.compareTo(BigDecimal.ZERO) < 0) throw new ApiException(HttpStatus.BAD_REQUEST, "Discount cannot be negative.");
        if (price != null && discount.compareTo(price) > 0) throw new ApiException(HttpStatus.BAD_REQUEST, "Discount cannot exceed price.");
    }

    private String stringValue(Object v) { return v == null ? "" : String.valueOf(v).trim(); }
    private String stringOrNull(Object v) { String s = stringValue(v); return s.isBlank() ? null : s; }
    private boolean booleanVal(Object v, boolean d) { return v == null ? d : Boolean.parseBoolean(String.valueOf(v)); }
    private int intVal(Object v, int d) { try { return v == null ? d : Integer.parseInt(String.valueOf(v)); } catch (Exception e) { throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid integer value."); } }
    private int intValNonNegative(Object v, int d) { int n = intVal(v, d); if (n < 0) throw new ApiException(HttpStatus.BAD_REQUEST, "Stock cannot be negative."); return n; }

    public record CategoryOption(String categoryId, String name) {}
}
