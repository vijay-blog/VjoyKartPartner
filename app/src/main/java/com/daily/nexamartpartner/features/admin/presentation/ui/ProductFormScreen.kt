package com.daily.nexamartpartner.features.admin.presentation.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.provider.OpenableColumns
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.ArrayAdapter
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.daily.nexamartpartner.R
import com.daily.nexamartpartner.core.widgets.UiFeedback
import com.daily.nexamartpartner.databinding.FragmentAdminProductFormBinding
import com.daily.nexamartpartner.di.appContainer
import com.daily.nexamartpartner.features.admin.domain.model.ProductImageUpload
import com.daily.nexamartpartner.features.admin.domain.model.ProductImage
import com.daily.nexamartpartner.features.admin.presentation.state.ProductFormUiState
import com.daily.nexamartpartner.features.admin.presentation.viewmodel.ProductEvent
import com.daily.nexamartpartner.features.admin.presentation.viewmodel.ProductFormViewModel
import com.daily.nexamartpartner.features.admin.presentation.viewmodel.ProductFormViewModelFactory
import com.daily.nexamartpartner.features.auth.presentation.viewmodel.AuthCoordinatorViewModel
import com.daily.nexamartpartner.features.auth.presentation.viewmodel.AuthCoordinatorViewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ProductFormScreen : Fragment(R.layout.fragment_admin_product_form) {
    private var _binding: FragmentAdminProductFormBinding? = null
    private val binding: FragmentAdminProductFormBinding
        get() = requireNotNull(_binding)

    private val mode by lazy {
        if (requireArguments().getString(ARG_MODE) == "EDIT") {
            ProductFormUiState.Mode.EDIT
        } else {
            ProductFormUiState.Mode.CREATE
        }
    }
    private val productId by lazy { requireArguments().getString(ARG_PRODUCT_ID) }

    private val authCoordinatorViewModel: AuthCoordinatorViewModel by activityViewModels {
        AuthCoordinatorViewModelFactory(
            restoreSessionUseCase = requireContext().appContainer.restoreSessionUseCase,
            logoutUseCase = requireContext().appContainer.logoutUseCase,
            authStateStore = requireContext().appContainer.authStateStore
        )
    }

    private val viewModel: ProductFormViewModel by viewModels {
        val container = requireContext().appContainer
        ProductFormViewModelFactory(
            mode = mode,
            productId = productId,
            getProductDetails = if (mode == ProductFormUiState.Mode.EDIT) {
                container.provideGetProductDetailsUseCase()
            } else {
                null
            },
            getCategoryOptions = container.provideGetProductCategoryOptionsUseCase(),
            createProduct = container.provideCreateProductUseCase(),
            updateProduct = container.provideUpdateProductUseCase(),
            uploadProductImage = container.provideUploadProductImageUseCase(),
            deleteProductImage = container.provideDeleteProductImageUseCase()
        )
    }

    private var categoryAdapter: ArrayAdapter<String>? = null
    private var categoryIdsByName: Map<String, String> = emptyMap()
    private var suppressCategorySelectionCallback = false

    /** Prevents programmatic `setText` calls during state rendering from being treated as user edits. */
    private var suppressFieldWatchers = false

    private val imagePicker = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNullOrEmpty()) return@registerForActivityResult
        viewLifecycleOwner.lifecycleScope.launch {
            val drafts = withContext(Dispatchers.IO) { uris.mapNotNull { uri -> readAndCompressImage(uri) } }
            viewModel.addImages(drafts)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentAdminProductFormBinding.bind(view)
        binding.productFormTitleText.text = getString(
            if (mode == ProductFormUiState.Mode.EDIT) {
                R.string.admin_product_form_edit_title
            } else {
                R.string.admin_product_form_create_title
            }
        )
        setupBackHandling()
        setupFieldWatchers()
        binding.productFormRetryButton.setOnClickListener { viewModel.retry() }
        binding.productAddImageButton.setOnClickListener { imagePicker.launch(arrayOf("image/*")) }
        binding.productFormSaveButton.setOnClickListener { viewModel.save() }
        collectUi()
        collectEvents()
    }

    private fun setupBackHandling() {
        binding.productFormBackButton.setOnClickListener { attemptNavigateBack() }
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    attemptNavigateBack()
                }
            }
        )
    }

    private fun attemptNavigateBack() {
        if (viewModel.hasUnsavedChanges()) {
            UiFeedback.showConfirmationDialog(
                anchor = binding.root,
                title = getString(R.string.admin_product_form_unsaved_title),
                message = getString(R.string.admin_product_form_unsaved_message),
                positiveActionText = getString(R.string.admin_product_form_discard),
                negativeActionText = getString(R.string.admin_product_form_keep_editing),
                onConfirmed = { findNavController().navigateUp() }
            )
        } else {
            findNavController().navigateUp()
        }
    }

    private fun setupFieldWatchers() {
        binding.productNameInput.doAfterTextChanged {
            if (!suppressFieldWatchers) viewModel.onNameChanged(it?.toString().orEmpty())
        }
        binding.productDescriptionInput.doAfterTextChanged {
            if (!suppressFieldWatchers) viewModel.onDescriptionChanged(it?.toString().orEmpty())
        }
        binding.productPriceInput.doAfterTextChanged {
            if (!suppressFieldWatchers) viewModel.onPriceChanged(it?.toString().orEmpty())
        }
        binding.productDiscountInput.doAfterTextChanged {
            if (!suppressFieldWatchers) viewModel.onDiscountChanged(it?.toString().orEmpty())
        }
        binding.productStockInput.doAfterTextChanged {
            if (!suppressFieldWatchers) viewModel.onStockChanged(it?.toString().orEmpty())
        }
        binding.productSkuInput.doAfterTextChanged {
            if (!suppressFieldWatchers) viewModel.onSkuChanged(it?.toString().orEmpty())
        }
        binding.productUnitInput.doAfterTextChanged {
            if (!suppressFieldWatchers) viewModel.onUnitChanged(it?.toString().orEmpty())
        }

        binding.productCategoryInput.setOnItemClickListener { parent, _, position, _ ->
            if (suppressCategorySelectionCallback) return@setOnItemClickListener
            val selectedName = parent.getItemAtPosition(position) as? String
            viewModel.onCategorySelected(categoryIdsByName[selectedName])
        }
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
                        is ProductEvent.ActionSucceeded -> Unit
                        is ProductEvent.SavedSuccessfully -> {
                            UiFeedback.showSnackbar(binding.root, getString(R.string.admin_product_form_save))
                            findNavController().navigateUp()
                        }
                    }
                }
            }
        }
    }

    private fun render(state: ProductFormUiState) {
        binding.productFormLoading.isVisible = state.isLoadingDetails &&
            state.content is ProductFormUiState.Content.Loading
        binding.productFormFieldsSection.isVisible = state.content == ProductFormUiState.Content.Editing
        binding.productFormStateSection.isVisible =
            state.content is ProductFormUiState.Content.Error || state.content is ProductFormUiState.Content.Unavailable

        when (val content = state.content) {
            is ProductFormUiState.Content.Error -> {
                binding.productFormStateTitleText.text = getString(R.string.admin_products_error_title)
                binding.productFormStateMessageText.text = content.message
                binding.productFormRetryButton.isVisible = true
            }

            is ProductFormUiState.Content.Unavailable -> {
                binding.productFormStateTitleText.text = getString(R.string.admin_products_unavailable_title)
                binding.productFormStateMessageText.text = content.message
                binding.productFormRetryButton.isVisible = false
            }

            else -> Unit
        }

        renderFields(state)
        binding.productFormSaveButton.isEnabled = !state.isSaving
    }

    private fun renderFields(state: ProductFormUiState) {
        suppressFieldWatchers = true
        if (binding.productNameInput.text?.toString() != state.name) {
            binding.productNameInput.setText(state.name)
        }
        if (binding.productDescriptionInput.text?.toString() != state.description) {
            binding.productDescriptionInput.setText(state.description)
        }
        if (binding.productPriceInput.text?.toString() != state.price) {
            binding.productPriceInput.setText(state.price)
        }
        if (binding.productDiscountInput.text?.toString() != state.discountPercent) {
            binding.productDiscountInput.setText(state.discountPercent)
        }
        if (binding.productStockInput.text?.toString() != state.stock) {
            binding.productStockInput.setText(state.stock)
        }
        if (binding.productSkuInput.text?.toString() != state.sku) {
            binding.productSkuInput.setText(state.sku)
        }
        if (binding.productUnitInput.text?.toString() != state.unit) {
            binding.productUnitInput.setText(state.unit)
        }
        suppressFieldWatchers = false

        renderCategoryOptions(state)
        renderImages(state)

        binding.productNameInputLayout.error = state.fieldErrors.name
        binding.productCategoryInputLayout.error = state.fieldErrors.category
        binding.productPriceInputLayout.error = state.fieldErrors.price
        binding.productDiscountInputLayout.error = state.fieldErrors.discount
        binding.productStockInputLayout.error = state.fieldErrors.stock

        binding.productCategoryUnavailableText.isVisible = state.categoryOptionsUnavailableMessage != null
        binding.productCategoryUnavailableText.text = state.categoryOptionsUnavailableMessage
            ?: getString(R.string.admin_product_form_categories_unavailable)
    }

    private fun renderImages(state: ProductFormUiState) {
        binding.productImagesContainer.removeAllViews()
        val visibleExisting = state.existingImages.filterNot { it.imageId in state.removedImageIds }
        val total = visibleExisting.size + state.selectedImages.size
        binding.productImageCountText.text = getString(R.string.admin_product_form_images_count, total)
        visibleExisting.forEach { image ->
            binding.productImagesContainer.addView(createImageCard(image))
        }
        state.selectedImages.forEachIndexed { index, image ->
            binding.productImagesContainer.addView(createSelectedImageCard(image.bytes, index))
        }
        binding.productAddImageButton.isEnabled = total < 3 && !state.isSaving
    }

    private fun createImageCard(image: ProductImage): View {
        return android.widget.FrameLayout(requireContext()).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(140.dp(), 140.dp()).apply { rightMargin = 10.dp() }
            setBackgroundResource(com.daily.nexamartpartner.R.drawable.bg_product_image_card)
            val imageView = android.widget.ImageView(context).apply {
                layoutParams = android.widget.FrameLayout.LayoutParams(-1,-1)
                scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
                contentDescription = getString(R.string.admin_product_form_image_content_description)
            }
            addView(imageView)
            loadRemoteImage(image.url, imageView)
            val remove = com.google.android.material.button.MaterialButton(context).apply {
                text = "×"; minWidth = 0; minimumWidth = 0; setPadding(0,0,0,0)
                layoutParams = android.widget.FrameLayout.LayoutParams(42.dp(),42.dp(),android.view.Gravity.END or android.view.Gravity.TOP)
                setOnClickListener { viewModel.removeExistingImage(image.imageId) }
            }
            addView(remove)
        }
    }

    private fun createSelectedImageCard(bytes: ByteArray, index: Int): View {
        return android.widget.FrameLayout(requireContext()).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(140.dp(), 140.dp()).apply { rightMargin = 10.dp() }
            setBackgroundResource(com.daily.nexamartpartner.R.drawable.bg_product_image_card)
            val imageView = android.widget.ImageView(context).apply {
                layoutParams = android.widget.FrameLayout.LayoutParams(-1,-1); scaleType=android.widget.ImageView.ScaleType.CENTER_CROP; setImageBitmap(BitmapFactory.decodeByteArray(bytes,0,bytes.size)); contentDescription=getString(R.string.admin_product_form_image_content_description)
            }; addView(imageView)
            val remove=com.google.android.material.button.MaterialButton(context).apply { text="×";minWidth=0;minimumWidth=0;setPadding(0,0,0,0);layoutParams=android.widget.FrameLayout.LayoutParams(42.dp(),42.dp(),android.view.Gravity.END or android.view.Gravity.TOP);setOnClickListener{viewModel.removeSelectedImage(index)} }; addView(remove)
        }
    }

    private fun loadRemoteImage(url:String,imageView:android.widget.ImageView){
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO){
            try{
                val full=if(url.startsWith("http"))url else if(url.startsWith("/api/")){
                    val base=BuildConfig.BASE_URL.substringBefore("/api/v1/").removeSuffix("/")
                    base+url
                }else{
                    BuildConfig.BASE_URL.removeSuffix("/")+"/"+url.trimStart('/')
                }
                val bitmap=java.net.HttpURLConnection::class.java.let {
                    val c=(full.openConnection() as java.net.HttpURLConnection);c.connectTimeout=10000;c.readTimeout=15000;c.inputStream.use{BitmapFactory.decodeStream(it)}
                }
                if(bitmap!=null) withContext(Dispatchers.Main){ if(isAdded) imageView.setImageBitmap(bitmap) }
            }catch(_:Exception){}
        }
    }

    private fun readAndCompressImage(uri:android.net.Uri):ProductImageUpload?{
        return try{
            val resolver=requireContext().contentResolver
            val type=resolver.getType(uri)?.lowercase() ?: "image/jpeg"
            if(type !in setOf("image/jpeg","image/png","image/webp")) return null
            val original=resolver.openInputStream(uri)?.use{it.readBytes()} ?: return null
            if(original.size>10*1024*1024) return null
            val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true}
            BitmapFactory.decodeByteArray(original,0,original.size,bounds)
            var sample=1; while(bounds.outWidth/sample>1600 || bounds.outHeight/sample>1600) sample*=2
            val opts=BitmapFactory.Options().apply{inSampleSize=sample}
            val bitmap=BitmapFactory.decodeByteArray(original,0,original.size,opts) ?: return null
            val out=java.io.ByteArrayOutputStream();bitmap.compress(Bitmap.CompressFormat.JPEG,82,out);bitmap.recycle()
            val name=queryDisplayName(uri) ?: "product_${System.currentTimeMillis()}.jpg"
            ProductImageUpload(out.toByteArray(),"image/jpeg",name.substringBeforeLast('.',name)+".jpg")
        }catch(_:Exception){null}
    }

    private fun queryDisplayName(uri:android.net.Uri):String?{
        return requireContext().contentResolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use{if(it.moveToFirst())it.getString(0) else null}
    }

    private fun Int.dp():Int=(this*resources.displayMetrics.density).toInt()

    private fun renderCategoryOptions(state: ProductFormUiState) {
        val names = state.categoryOptions.map { it.name }
        categoryIdsByName = state.categoryOptions.associateBy({ it.name }, { it.categoryId })
        if (categoryAdapter == null) {
            categoryAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, names.toMutableList())
            binding.productCategoryInput.setAdapter(categoryAdapter)
        } else {
            categoryAdapter?.clear()
            categoryAdapter?.addAll(names)
        }

        val selectedName = state.categoryOptions.firstOrNull { it.categoryId == state.selectedCategoryId }?.name.orEmpty()
        if (binding.productCategoryInput.text?.toString() != selectedName) {
            suppressCategorySelectionCallback = true
            binding.productCategoryInput.setText(selectedName, false)
            suppressCategorySelectionCallback = false
        }
    }

    override fun onDestroyView() {
        categoryAdapter = null
        _binding = null
        super.onDestroyView()
    }

    companion object {
        const val ARG_MODE = "mode"
        const val ARG_PRODUCT_ID = "productId"
    }
}
