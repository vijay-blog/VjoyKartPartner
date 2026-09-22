# VJoyKart Partner - Product Images

Product creation/editing now supports up to 3 product images. Images are resized/compressed by the Android partner app and uploaded to the Spring Boot backend. The backend stores images in MySQL `product_images` as MEDIUMBLOB data and exposes public catalog image URLs.

## Admin API
- `POST /api/v1/admin/products/{productId}/images` multipart field `file`, optional `sortOrder`
- `DELETE /api/v1/admin/products/{productId}/images/{imageId}`

## Customer/catalog API
- Product responses retain `imageUrl` for the primary image and now include `images: [{imageId,url,sortOrder}]`.
- `GET /api/v1/catalog/products/{productId}/images/{imageId}` returns the image bytes.

Maximum: 3 images/product, 5 MB/image at the backend.
