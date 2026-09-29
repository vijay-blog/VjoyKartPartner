package com.daily.nexamartpartner.features.delivery.availability.presentation.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.daily.nexamartpartner.R
import com.daily.nexamartpartner.databinding.FragmentDeliveryAvailabilityBinding
import com.daily.nexamartpartner.di.appContainer
import com.daily.nexamartpartner.features.auth.presentation.viewmodel.AuthCoordinatorViewModel
import com.daily.nexamartpartner.features.auth.presentation.viewmodel.AuthCoordinatorViewModelFactory
import com.daily.nexamartpartner.features.delivery.availability.presentation.viewmodel.DeliveryAvailabilityViewModel
import com.daily.nexamartpartner.features.delivery.availability.presentation.viewmodel.DeliveryAvailabilityViewModelFactory
import com.daily.nexamartpartner.features.delivery.location.DeliveryLocationForegroundService
import kotlinx.coroutines.launch

class DeliveryAvailabilityScreen : Fragment(R.layout.fragment_delivery_availability) {
    private var _binding: FragmentDeliveryAvailabilityBinding? = null
    private val binding get() = requireNotNull(_binding)
    private val authViewModel: AuthCoordinatorViewModel by activityViewModels {
        AuthCoordinatorViewModelFactory(requireContext().appContainer.restoreSessionUseCase, requireContext().appContainer.logoutUseCase, requireContext().appContainer.authStateStore)
    }
    private var pendingOnlineRequest = false

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val locationGranted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (pendingOnlineRequest && locationGranted) {
            pendingOnlineRequest = false
            viewModel.setAvailable(true)
            DeliveryLocationForegroundService.start(requireContext())
        } else if (pendingOnlineRequest) {
            pendingOnlineRequest = false
            binding.availabilitySwitch.isChecked = false
            android.widget.Toast.makeText(requireContext(), "Location permission is required to receive nearby delivery orders.", android.widget.Toast.LENGTH_LONG).show()
        }
    }

    private val viewModel: DeliveryAvailabilityViewModel by viewModels {
        DeliveryAvailabilityViewModelFactory(requireContext().appContainer.provideGetDeliveryAvailabilityUseCase(), requireContext().appContainer.provideUpdateDeliveryAvailabilityUseCase())
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentDeliveryAvailabilityBinding.bind(view)
        binding.availabilitySwipeRefresh.setOnRefreshListener { viewModel.refresh() }
        binding.retryButton.setOnClickListener { viewModel.load() }
        binding.availabilitySwitch.setOnCheckedChangeListener { _, checked ->
            val current = viewModel.state.value.availability
            if (current?.available == checked) return@setOnCheckedChangeListener
            if (checked) {
                if (hasLocationPermission()) {
                    viewModel.setAvailable(true)
                    DeliveryLocationForegroundService.start(requireContext())
                } else {
                    pendingOnlineRequest = true
                    locationPermissionLauncher.launch(requiredPermissions())
                }
            } else {
                viewModel.setAvailable(false)
                DeliveryLocationForegroundService.stop(requireContext())
            }
        }
        viewLifecycleOwner.lifecycleScope.launch { viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) { viewModel.state.collect(::render) } }
        viewLifecycleOwner.lifecycleScope.launch { viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) { viewModel.events.collect { if (it is DeliveryAvailabilityViewModel.Event.SessionExpired) authViewModel.onSessionExpired() } } }
    }

    private fun render(state: com.daily.nexamartpartner.features.delivery.availability.presentation.state.DeliveryAvailabilityUiState) {
        binding.availabilitySwipeRefresh.isRefreshing = state.refreshing
        binding.savingProgress.isVisible = state.saving
        val data = state.availability
        binding.availabilitySwitch.setOnCheckedChangeListener(null)
        binding.availabilitySwitch.isEnabled = data?.canChange == true && !state.saving
        binding.availabilitySwitch.isChecked = data?.available == true
        binding.availabilitySwitch.setOnCheckedChangeListener { _, checked ->
            if (data?.available == checked) return@setOnCheckedChangeListener
            if (checked) {
                if (hasLocationPermission()) {
                    viewModel.setAvailable(true)
                    DeliveryLocationForegroundService.start(requireContext())
                } else {
                    pendingOnlineRequest = true
                    locationPermissionLauncher.launch(requiredPermissions())
                }
            } else {
                viewModel.setAvailable(false)
                DeliveryLocationForegroundService.stop(requireContext())
            }
        }
        binding.statusText.text = when (data?.available) { true -> "You are online"; false -> "You are offline"; null -> if (state.loading) "Loading availability…" else "Availability unavailable" }
        binding.reasonText.text = data?.reason.orEmpty()
        binding.reasonText.isVisible = !data?.reason.isNullOrBlank()
        binding.updatedAtText.text = data?.updatedAt?.let { "Last updated: $it" }.orEmpty()
        binding.stateCard.isVisible = state.unavailable || state.error != null
        binding.stateTitle.text = if (state.unavailable) "Backend contract pending" else "Could not load availability"
        binding.stateMessage.text = state.error ?: "Availability is waiting for the confirmed Spring Boot API contract."
        binding.retryButton.isVisible = !state.unavailable
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun requiredPermissions(): Array<String> = buildList {
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        add(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
    }.toTypedArray()

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
