package com.daily.nexamartpartner.features.admin.domain.repository

import com.daily.nexamartpartner.core.result.AppResult
import com.daily.nexamartpartner.features.admin.domain.model.CategoryOption
import com.daily.nexamartpartner.features.admin.domain.model.ProductAdminAction
import com.daily.nexamartpartner.features.admin.domain.model.ProductDetails
import com.daily.nexamartpartner.features.admin.domain.model.ProductImage
import com.daily.nexamartpartner.features.admin.domain.model.ProductDraft
import com.daily.nexamartpartner.features.admin.domain.model.PagedProducts
import com.daily.nexamartpartner.features.admin.domain.model.ProductsQuery

interface ProductManagementRepository {
    suspend fun getProducts(query: ProductsQuery): AppResult<PagedProducts>
    suspend fun getProductDetails(productId: String): AppResult<ProductDetails>

    /** Category lookup only, for product form selection. Full category CRUD is out of scope. */
    suspend fun getCategoryOptions(): AppResult<List<CategoryOption>>

    suspend fun createProduct(draft: ProductDraft): AppResult<ProductDetails>
    suspend fun updateProduct(productId: String, draft: ProductDraft): AppResult<ProductDetails>
    suspend fun performProductAction(productId: String, action: ProductAdminAction): AppResult<Unit>
    suspend fun uploadProductImage(productId: String, file: okhttp3.MultipartBody.Part, sortOrder: Int): AppResult<ProductImage> =
        AppResult.Failure(com.daily.nexamartpartner.core.result.AppFailure("Image upload is not supported by this repository implementation.", type = com.daily.nexamartpartner.core.result.FailureType.CONTRACT_MISSING))
    suspend fun deleteProductImage(productId: String, imageId: Long): AppResult<Unit> =
        AppResult.Failure(com.daily.nexamartpartner.core.result.AppFailure("Image deletion is not supported by this repository implementation.", type = com.daily.nexamartpartner.core.result.FailureType.CONTRACT_MISSING))
}
