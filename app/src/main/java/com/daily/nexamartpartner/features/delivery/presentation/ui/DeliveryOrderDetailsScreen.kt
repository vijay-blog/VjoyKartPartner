package com.daily.nexamartpartner.features.delivery.presentation.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import android.provider.Settings
import android.location.LocationManager
import androidx.core.content.ContextCompat
import android.Manifest
import android.content.pm.PackageManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.core.net.toUri
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.daily.nexamartpartner.R
import com.daily.nexamartpartner.databinding.FragmentDeliveryOrderDetailsBinding
import com.daily.nexamartpartner.di.appContainer
import com.daily.nexamartpartner.features.auth.presentation.viewmodel.AuthCoordinatorViewModel
import com.daily.nexamartpartner.features.auth.presentation.viewmodel.AuthCoordinatorViewModelFactory
import com.daily.nexamartpartner.features.delivery.domain.model.DeliveryOrderAction
import com.daily.nexamartpartner.features.delivery.domain.model.DeliveryOrderDetails
import com.daily.nexamartpartner.features.delivery.presentation.location.DeliveryNavigationHelper
import com.daily.nexamartpartner.features.delivery.presentation.location.DeliveryLocationProvider
import com.daily.nexamartpartner.features.delivery.presentation.viewmodel.DeliveryOrderDetailsViewModel
import com.daily.nexamartpartner.features.delivery.presentation.viewmodel.DeliveryOrderDetailsViewModelFactory
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch

class DeliveryOrderDetailsScreen : Fragment(R.layout.fragment_delivery_order_details) {
    private var _binding: FragmentDeliveryOrderDetailsBinding? = null
    private val b get() = requireNotNull(_binding)
    private val id: String get() = requireArguments().getString("orderId").orEmpty()
    private val vm: DeliveryOrderDetailsViewModel by viewModels {
        DeliveryOrderDetailsViewModelFactory(
            id,
            requireContext().appContainer.provideGetDeliveryOrderDetailsUseCase(),
            requireContext().appContainer.providePerformDeliveryOrderActionUseCase()
        )
    }
    private val locationProvider by lazy { DeliveryLocationProvider(requireContext().applicationContext) }

    private val auth: AuthCoordinatorViewModel by activityViewModels {
        AuthCoordinatorViewModelFactory(
            requireContext().appContainer.restoreSessionUseCase,
            requireContext().appContainer.logoutUseCase,
            requireContext().appContainer.authStateStore
        )
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentDeliveryOrderDetailsBinding.bind(view)
        b.retryButton.setOnClickListener { vm.retry() }
        b.swipeRefresh.setOnRefreshListener { vm.refresh() }
        b.shareButton.setOnClickListener { shareOrder() }
        collect()
    }

    private fun collect() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { vm.state.collect(::render) }
                launch {
                    vm.events.collect {
                        when (it) {
                            DeliveryOrderDetailsViewModel.Event.SessionExpired -> auth.onSessionExpired()
                            is DeliveryOrderDetailsViewModel.Event.ActionSuccess -> Toast.makeText(requireContext(), "${it.action.label} successful", Toast.LENGTH_SHORT).show()
                            is DeliveryOrderDetailsViewModel.Event.Message -> Toast.makeText(requireContext(), it.text, Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }
    }

    private fun render(state: DeliveryOrderDetailsViewModel.State) {
        b.progress.isVisible = state.loading
        b.swipeRefresh.isRefreshing = state.refreshing
        b.stateCard.isVisible = !state.loading && state.order == null
        b.content.isVisible = state.order != null
        b.shareButton.isVisible = state.order != null
        if (state.order == null) {
            b.stateTitle.text = if (state.unavailable) "Order unavailable" else "Unable to load order"
            b.stateMessage.text = state.error ?: "Please try again."
            return
        }
        renderOrder(state.order!!, state.busy)
    }

    private fun renderOrder(order: DeliveryOrderDetails, busy: Boolean) {
        b.orderId.text = order.orderId
        b.status.text = "Status: ${order.status}"
        b.customer.text = order.customerName
        b.phone.text = order.customerPhone?.takeIf { it.isNotBlank() }?.let { "📞 $it" } ?: "Phone unavailable"
        b.phone.isEnabled = !order.customerPhone.isNullOrBlank()
        b.phone.setOnClickListener { order.customerPhone?.let(::dial) }
        b.address.text = order.address ?: "Address unavailable"
        b.address.isEnabled = !order.address.isNullOrBlank()
        b.address.setOnClickListener { order.address?.let(::openMaps) }
        b.mapButton.isEnabled = !order.address.isNullOrBlank() && !busy
        b.mapButton.setOnClickListener { order.address?.let(::openMaps) }
        b.navigateButton.isEnabled = !order.address.isNullOrBlank() && !busy
        b.navigateButton.setOnClickListener { order.address?.let(::navigateToAddress) }
        b.copyAddressButton.isEnabled = !order.address.isNullOrBlank()
        b.copyAddressButton.setOnClickListener { order.address?.let(::copyAddress) }
        renderProof(order)
        b.amount.text = "Total: ${order.totalAmount?.let { if (order.currencyCode.isNullOrBlank()) it.toPlainString() else "${order.currencyCode} ${it.toPlainString()}" } ?: "Unavailable"}"
        b.payment.text = "Payment: ${order.paymentStatus ?: "Unavailable"}"

        b.items.removeAllViews()
        if (order.items.isEmpty()) addLine(b.items, "No item details available")
        else order.items.forEach { item ->
            val price = item.lineTotal?.toPlainString()?.let { "  •  ${item.currencySafePrefix(order.currencyCode)}$it" } ?: ""
            addLine(b.items, "${item.productName} × ${item.quantity}$price")
        }

        b.timeline.removeAllViews()
        if (order.timeline.isEmpty()) addLine(b.timeline, "No timeline events available")
        else order.timeline.forEach { event -> addLine(b.timeline, "${event.status}  ${event.timestamp ?: "Time unavailable"}") }

        b.actions.removeAllViews()
        if (order.allowedActions.isEmpty()) addLine(b.actions, "No actions available for this order")
        else order.allowedActions.forEach { action ->
            val button = MaterialButton(requireContext()).apply {
                text = action.label
                isEnabled = !busy
                setOnClickListener {
                    when (action) {
                        DeliveryOrderAction.ACCEPT -> acceptWithLocation(action)
                        DeliveryOrderAction.COMPLETE -> confirmCompletion(action, order)
                        else -> confirm(action)
                    }
                }
            }
            b.actions.addView(button)
        }
    }

    private fun addLine(parent: android.widget.LinearLayout, text: String) {
        parent.addView(TextView(requireContext()).apply {
            this.text = text
            textSize = 15f
            setPadding(0, 6, 0, 6)
        })
    }


    private fun renderProof(order: DeliveryOrderDetails) {
        val hasProofData = order.proofOfDeliveryRequired || !order.proofOfDeliveryStatus.isNullOrBlank() || !order.proofOfDeliveryUrl.isNullOrBlank()
        b.proofCard.isVisible = hasProofData
        if (!hasProofData) return
        val requirement = if (order.proofOfDeliveryRequired) "Required by backend" else "Optional / not specified"
        val status = order.proofOfDeliveryStatus?.takeIf { it.isNotBlank() } ?: "Not submitted"
        b.proofStatus.text = "Requirement: $requirement\nStatus: $status"
        b.viewProofButton.isVisible = !order.proofOfDeliveryUrl.isNullOrBlank()
        b.viewProofButton.setOnClickListener {
            val url = order.proofOfDeliveryUrl ?: return@setOnClickListener
            runCatching { startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }
                .onFailure { Toast.makeText(requireContext(), "Unable to open proof", Toast.LENGTH_SHORT).show() }
        }
    }


    private fun acceptWithLocation(action: DeliveryOrderAction) {
        if (!hasLocationPermission()) {
            Toast.makeText(requireContext(), "Location permission is required to accept a delivery. Enable Location permission and try again.", Toast.LENGTH_LONG).show()
            return
        }
        val manager = requireContext().getSystemService(android.content.Context.LOCATION_SERVICE) as? LocationManager
        if (manager != null && !manager.isProviderEnabled(LocationManager.GPS_PROVIDER) && !manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            Toast.makeText(requireContext(), "Turn on device Location and try again.", Toast.LENGTH_LONG).show()
            runCatching { startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }
            return
        }
        Toast.makeText(requireContext(), "Getting your current location…", Toast.LENGTH_SHORT).show()
        locationProvider.getCurrentLocation { location ->
            if (!isAdded) return@getCurrentLocation
            if (location == null) {
                Toast.makeText(requireContext(), "Unable to get your current location. Keep Location ON and try again.", Toast.LENGTH_LONG).show()
            } else {
                vm.perform(action, location.latitude, location.longitude)
            }
        }
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun confirmCompletion(action: DeliveryOrderAction, order: DeliveryOrderDetails) {
        val proofLine = when {
            order.proofOfDeliveryRequired && order.proofOfDeliveryStatus.isNullOrBlank() -> "\n\nProof of delivery is marked required by the backend. Complete only after following the configured proof process."
            !order.proofOfDeliveryStatus.isNullOrBlank() -> "\n\nProof status: ${order.proofOfDeliveryStatus}"
            else -> ""
        }
        AlertDialog.Builder(requireContext())
            .setTitle("Confirm delivery")
            .setMessage("You are about to mark order ${order.orderId} as delivered.$proofLine\n\nOnly continue after handing the order to the customer.")
            .setNegativeButton("Not yet", null)
            .setPositiveButton("Confirm delivered") { _, _ -> vm.perform(action) }
            .show()
    }

    private fun confirm(action: DeliveryOrderAction) {
        AlertDialog.Builder(requireContext())
            .setTitle(action.label)
            .setMessage("Confirm ${action.label.lowercase()} for order $id?")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Confirm") { _, _ -> vm.perform(action) }
            .show()
    }

    private fun dial(phone: String) {
        startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${phone.trim()}")))
    }

    private fun openMaps(address: String) {
        if (!DeliveryNavigationHelper.openMapsSearch(requireContext(), address)) {
            Toast.makeText(requireContext(), "No maps app available", Toast.LENGTH_SHORT).show()
        }
    }

    private fun navigateToAddress(address: String) {
        if (!DeliveryNavigationHelper.openDestination(requireContext(), address)) {
            Toast.makeText(requireContext(), "No navigation app available", Toast.LENGTH_SHORT).show()
        }
    }

    private fun copyAddress(address: String) {
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Delivery address", address))
        Toast.makeText(requireContext(), "Address copied", Toast.LENGTH_SHORT).show()
    }

    private fun shareOrder() {
        val text = "NexaMart delivery order ${id}. Please use the order ID to view delivery details."
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }, "Share order"))
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}

private fun com.daily.nexamartpartner.features.delivery.domain.model.DeliveryOrderItem.currencySafePrefix(currency: String?): String =
    if (currency.isNullOrBlank()) "₹" else "$currency "
