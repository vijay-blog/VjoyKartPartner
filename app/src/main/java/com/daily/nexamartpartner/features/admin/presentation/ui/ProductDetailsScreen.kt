package com.daily.nexamartpartner.features.admin.presentation.ui

import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.daily.nexamartpartner.BuildConfig
import com.daily.nexamartpartner.R
import com.daily.nexamartpartner.core.format.ValueFormatter
import com.daily.nexamartpartner.core.widgets.UiFeedback
import com.daily.nexamartpartner.databinding.FragmentAdminProductDetailsBinding
import com.daily.nexamartpartner.di.appContainer
import com.daily.nexamartpartner.features.admin.domain.model.ProductAdminAction
import com.daily.nexamartpartner.features.admin.domain.model.ProductDetails
import com.daily.nexamartpartner.features.admin.presentation.state.ProductDetailsUiState
import com.daily.nexamartpartner.features.admin.presentation.viewmodel.ProductDetailsViewModel
import com.daily.nexamartpartner.features.admin.presentation.viewmodel.ProductDetailsViewModelFactory
import com.daily.nexamartpartner.features.admin.presentation.viewmodel.ProductEvent
import com.daily.nexamartpartner.features.auth.presentation.viewmodel.AuthCoordinatorViewModel
import com.daily.nexamartpartner.features.auth.presentation.viewmodel.AuthCoordinatorViewModelFactory
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

class ProductDetailsScreen : Fragment(R.layout.fragment_admin_product_details) {
    private var _binding: FragmentAdminProductDetailsBinding? = null
    private val binding: FragmentAdminProductDetailsBinding
        get() = requireNotNull(_binding)

    private val productId by lazy { requireArguments().getString(ARG_PRODUCT_ID).orEmpty() }

    private val authCoordinatorViewModel: AuthCoordinatorViewModel by activityViewModels {
        AuthCoordinatorViewModelFactory(
            restoreSessionUseCase = requireContext().appContainer.restoreSessionUseCase,
            logoutUseCase = requireContext().appContainer.logoutUseCase,
            authStateStore = requireContext().appContainer.authStateStore
        )
    }

    private val viewModel: ProductDetailsViewModel by viewModels {
        ProductDetailsViewModelFactory(
            productId = productId,
            getProductDetails = requireContext().appContainer.provideGetProductDetailsUseCase(),
            performProductAdminAction = requireContext().appContainer.providePerformProductAdminActionUseCase()
        )
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentAdminProductDetailsBinding.bind(view)
        binding.productDetailsBackButton.setOnClickListener { findNavController().navigateUp() }
        binding.productDetailsRefreshButton.setOnClickListener { viewModel.refresh() }
        binding.productDetailsRetryButton.setOnClickListener { viewModel.retry() }
        binding.productDetailsSwipeRefresh.setOnRefreshListener { viewModel.refresh() }
        collectUi()
        collectEvents()
    }

    private fun collectUi() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state -> render(state) }
            }
        }
    }

    private fun collectEvents() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect { event ->
                    when (event) {
                        is ProductEvent.SessionExpired -> authCoordinatorViewModel.onSessionExpired()
                        is ProductEvent.Message -> UiFeedback.showSnackbar(binding.root, event.text)
                        is ProductEvent.ActionSucceeded -> {
                            UiFeedback.showSnackbar(
                                binding.root,
                                getString(R.string.admin_product_action_success)
                            )
                            if (event.action == ProductAdminAction.DELETE) {
                                findNavController().navigateUp()
                            }
                        }

                        is ProductEvent.SavedSuccessfully -> Unit
                    }
                }
            }
        }
    }

    private fun render(state: ProductDetailsUiState) {
        binding.productDetailsSwipeRefresh.isRefreshing = state.isRefreshing
        when (val content = state.content) {
            is ProductDetailsUiState.Content.Loading -> {
                binding.productDetailsLoading.isVisible = true
                binding.productDetailsStateSection.isVisible = false
                binding.productDetailsContentSection.isVisible = false
            }

            is ProductDetailsUiState.Content.Success -> {
                binding.productDetailsLoading.isVisible = false
                binding.productDetailsStateSection.isVisible = false
                binding.productDetailsContentSection.isVisible = true
                renderProduct(content.product, state.actionInProgress)
            }

            is ProductDetailsUiState.Content.Error -> {
                binding.productDetailsLoading.isVisible = false
                binding.productDetailsStateSection.isVisible = true
                binding.productDetailsContentSection.isVisible = false
                binding.productDetailsStateTitleText.text = getString(R.string.admin_products_error_title)
                binding.productDetailsStateMessageText.text = content.message
            }

            is ProductDetailsUiState.Content.Unavailable -> {
                binding.productDetailsLoading.isVisible = false
                binding.productDetailsStateSection.isVisible = true
                binding.productDetailsContentSection.isVisible = false
                binding.productDetailsStateTitleText.text = getString(R.string.admin_products_unavailable_title)
                binding.productDetailsStateMessageText.text = content.message
            }
        }
    }

    private fun renderProduct(product: ProductDetails, actionInProgress: ProductAdminAction?) {
        val unavailable = getString(R.string.admin_product_details_unavailable_value)
        binding.productNameText.text = product.name.ifBlank { unavailable }
        binding.productDescriptionText.text = product.description ?: unavailable
        binding.productCategoryText.text = getString(
            R.string.admin_product_details_category_template,
            product.categoryName ?: unavailable
        )

        val priceText = formatPrice(product.price, product.currencyCode, unavailable)
        val discountedText = product.discountedPrice?.let {
            formatPrice(it, product.currencyCode, unavailable)
        }
        binding.productPriceText.text = buildString {
            append(getString(R.string.admin_product_details_price_template, priceText))
            discountedText?.let {
                append('\n')
                append(getString(R.string.admin_product_details_discounted_price_template, it))
            }
            product.discountPercent?.let {
                append('\n')
                append(getString(R.string.admin_product_details_discount_template, it.toPlainString()))
            }
        }

        binding.productStockText.text = getString(
            R.string.admin_product_details_stock_template,
            product.stock?.toString() ?: unavailable,
            product.unit ?: unavailable
        )
        binding.productSkuText.text = getString(
            R.string.admin_product_details_sku_template,
            product.sku ?: unavailable
        )
        binding.productStatusText.text = getString(
            R.string.admin_product_details_status_template,
            product.status.backendValue,
            product.availability.backendValue
        )
        binding.productTimestampsText.text = getString(
            R.string.admin_product_details_timestamps_template,
            product.createdAt ?: unavailable,
            product.updatedAt ?: unavailable
        )

        renderProductImages(product)
        renderActions(product, actionInProgress)
    }


    private fun renderProductImages(product: ProductDetails) {
        val container = binding.productDetailsImagesContainer
        container.removeAllViews()
        val urls = product.images
            .sortedBy { it.sortOrder }
            .map { it.url.trim() }
            .filter { it.isNotEmpty() }
            .toMutableList()

        if (urls.isEmpty() && !product.imageUrl.isNullOrBlank()) {
            urls += product.imageUrl!!.trim()
        }

        if (urls.isEmpty()) {
            container.addView(ImageView(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(dp(96), dp(96))
                setBackgroundColor(0xFF3F4655.toInt())
                setPadding(dp(18), dp(18), dp(18), dp(18))
                setImageResource(android.R.drawable.ic_menu_gallery)
                imageTintList = android.content.res.ColorStateList.valueOf(0xFFB8BEC9.toInt())
                contentDescription = getString(R.string.cd_product_image_unavailable)
            })
            return
        }

        urls.take(3).forEachIndexed { index, rawUrl ->
            val imageView = ImageView(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(dp(140), dp(140)).apply {
                    if (index > 0) leftMargin = dp(12)
                }
                scaleType = ImageView.ScaleType.CENTER_CROP
                setBackgroundColor(0xFF20242D.toInt())
                contentDescription = "Product image ${index + 1}"
            }
            container.addView(imageView)
            val resolvedUrl = resolveImageUrl(rawUrl)
            viewLifecycleOwner.lifecycleScope.launch {
                val bitmap = withContext(Dispatchers.IO) { loadBitmap(resolvedUrl) }
                if (bitmap != null && view != null) imageView.setImageBitmap(bitmap)
                else {
                    imageView.setImageResource(android.R.drawable.ic_menu_gallery)
                    imageView.imageTintList = android.content.res.ColorStateList.valueOf(0xFFB8BEC9.toInt())
                }
            }
        }
    }

    private fun resolveImageUrl(raw: String): String {
        if (raw.startsWith("http://") || raw.startsWith("https://")) return raw
        val base = BuildConfig.BASE_URL.substringBefore("/api/v1/").removeSuffix("/")
        return if (raw.startsWith("/")) "$base$raw" else "$base/${raw}"
    }

    private fun loadBitmap(url: String): android.graphics.Bitmap? {
        return try {
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 15_000
                requestMethod = "GET"
                useCaches = true
                doInput = true
            }
            connection.connect()
            if (connection.responseCode !in 200..299) {
                connection.disconnect()
                return null
            }
            connection.inputStream.use { BitmapFactory.decodeStream(it) }.also { connection.disconnect() }
        } catch (_: Exception) {
            null
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun formatPrice(
        amount: java.math.BigDecimal?,
        currencyCode: String?,
        unavailable: String
    ): String = when {
        amount == null -> unavailable
        currencyCode.isNullOrBlank() -> amount.toPlainString()
        else -> ValueFormatter.formatCurrency(amount, currencyCode)
    }

    private fun renderActions(product: ProductDetails, actionInProgress: ProductAdminAction?) {
        binding.productActionsContainer.removeAllViews()
        if (product.allowedActions.isEmpty()) {
            binding.productActionsContainer.addView(
                android.widget.TextView(requireContext()).apply {
                    text = getString(R.string.admin_product_no_actions)
                }
            )
            return
        }
        product.allowedActions.forEach { action ->
            binding.productActionsContainer.addView(
                MaterialButton(requireContext()).apply {
                    text = action.backendValue
                    isEnabled = actionInProgress == null
                    setOnClickListener { handleAction(product, action) }
                }
            )
        }
    }

    private fun handleAction(product: ProductDetails, action: ProductAdminAction) {
        if (action == ProductAdminAction.EDIT) {
            findNavController().navigate(
                R.id.adminProductFormFragment,
                bundleOf("mode" to "EDIT", "productId" to product.productId)
            )
            return
        }
        val message = if (action == ProductAdminAction.DELETE) {
            getString(R.string.admin_product_delete_confirmation, product.name)
        } else {
            getString(R.string.admin_product_action_confirmation, product.name, action.backendValue)
        }
        UiFeedback.showConfirmationDialog(
            anchor = binding.root,
            title = action.backendValue,
            message = message,
            positiveActionText = getString(R.string.common_confirm),
            negativeActionText = getString(R.string.common_cancel),
            onConfirmed = { viewModel.performAction(action) }
        )
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    companion object {
        const val ARG_PRODUCT_ID = "productId"
    }
}
