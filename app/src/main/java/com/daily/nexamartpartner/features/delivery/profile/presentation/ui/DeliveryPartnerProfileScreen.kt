package com.daily.nexamartpartner.features.delivery.profile.presentation.ui

import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.daily.nexamartpartner.R
import com.daily.nexamartpartner.databinding.FragmentDeliveryProfileBinding
import com.daily.nexamartpartner.di.appContainer
import com.daily.nexamartpartner.features.auth.presentation.viewmodel.AuthCoordinatorViewModel
import com.daily.nexamartpartner.features.auth.presentation.viewmodel.AuthCoordinatorViewModelFactory
import com.daily.nexamartpartner.features.delivery.profile.domain.model.DeliveryPartnerProfile
import com.daily.nexamartpartner.features.delivery.profile.domain.model.DeliveryPartnerProfileUpdate
import com.daily.nexamartpartner.features.delivery.profile.presentation.state.DeliveryPartnerProfileUiState
import com.daily.nexamartpartner.features.delivery.profile.presentation.viewmodel.DeliveryPartnerProfileViewModel
import com.daily.nexamartpartner.features.delivery.profile.presentation.viewmodel.DeliveryPartnerProfileViewModelFactory
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

class DeliveryPartnerProfileScreen : Fragment(R.layout.fragment_delivery_profile) {
 private var _binding:FragmentDeliveryProfileBinding?=null; private val b get()=requireNotNull(_binding)
 private val vm:DeliveryPartnerProfileViewModel by viewModels{DeliveryPartnerProfileViewModelFactory(requireContext().appContainer.provideGetDeliveryPartnerProfileUseCase(),requireContext().appContainer.provideUpdateDeliveryPartnerProfileUseCase())}
 private val auth:AuthCoordinatorViewModel by activityViewModels{AuthCoordinatorViewModelFactory(requireContext().appContainer.restoreSessionUseCase,requireContext().appContainer.logoutUseCase,requireContext().appContainer.authStateStore)}
 override fun onViewCreated(view:View,savedInstanceState:Bundle?){super.onViewCreated(view,savedInstanceState);_binding=FragmentDeliveryProfileBinding.bind(view);b.swipeRefresh.setOnRefreshListener{vm.load()};b.retryButton.setOnClickListener{vm.load()};b.editButton.setOnClickListener{showEditDialog(vm.state.value.profile)};collect()}
 private fun collect(){viewLifecycleOwner.lifecycleScope.launch{viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED){launch{vm.state.collect(::render)};launch{vm.events.collect{when(it){is DeliveryPartnerProfileViewModel.Event.SessionExpired->auth.onSessionExpired();is DeliveryPartnerProfileViewModel.Event.Message->b.messageText.text=it.text}}}}}}
 private fun render(s:DeliveryPartnerProfileUiState){b.progress.isVisible=s.loading;b.swipeRefresh.isRefreshing=false;b.retryButton.isVisible=s.error!=null;b.stateCard.isVisible=s.error!=null||s.unavailable;b.editButton.isVisible=s.profile?.editableFields?.isNotEmpty()==true;b.editButton.isEnabled=!s.saving; s.profile?.let{renderProfile(it)};if(s.unavailable){b.stateTitle.text=getString(R.string.delivery_profile_unavailable);b.stateMessage.text=getString(R.string.delivery_profile_contract_pending)}else if(s.error!=null){b.stateTitle.text=getString(R.string.delivery_profile_error);b.stateMessage.text=s.error}else if(s.profile==null&&!s.loading){b.stateTitle.text=getString(R.string.delivery_profile_empty);b.stateMessage.text=getString(R.string.delivery_profile_no_data)}}
 private fun renderProfile(p:DeliveryPartnerProfile){b.nameText.text=p.name?:getString(R.string.unavailable);b.phoneText.text=p.phone?:getString(R.string.unavailable);b.emailText.text=p.email?:getString(R.string.unavailable);b.verificationText.text=p.verificationStatus?:getString(R.string.unavailable);b.accountText.text=p.accountStatus?:getString(R.string.unavailable);b.vehicleText.text=p.vehicleType?:getString(R.string.unavailable);b.vehicleNumberText.text=p.vehicleNumber?:getString(R.string.unavailable);b.licenseText.text=p.licenseReference?:getString(R.string.unavailable);b.dobText.text="DOB: "+(p.dateOfBirth?:getString(R.string.unavailable));b.drivingLicenseNumberText.text="Driving licence: "+(p.drivingLicenseNumber?:getString(R.string.unavailable));b.aadhaarText.text="Aadhaar: "+(p.aadhaarNumber?.let{if(it.length>=4) "•••• •••• "+it.takeLast(4) else it}?:getString(R.string.unavailable));b.registeredText.text=p.registeredAt?:getString(R.string.unavailable);b.lastActiveText.text=p.lastActiveAt?:getString(R.string.unavailable)}
 private fun showEditDialog(p:DeliveryPartnerProfile?){if(p==null)return;val v=layoutInflater.inflate(R.layout.dialog_delivery_profile_edit,null);val name=v.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.nameInput);val email=v.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.emailInput);val vehicle=v.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.vehicleTypeInput);val number=v.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.vehicleNumberInput);val license=v.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.licenseInput);val dob=v.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.dobInput);val dl=v.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.drivingLicenseNumberInput);val aadhaar=v.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.aadhaarNumberInput);name.setText(p.name);email.setText(p.email);vehicle.setText(p.vehicleType);number.setText(p.vehicleNumber);license.setText(p.licenseReference);dob.setText(p.dateOfBirth);dl.setText(p.drivingLicenseNumber);aadhaar.setText(p.aadhaarNumber);listOf(R.id.nameInput,R.id.emailInput,R.id.vehicleTypeInput,R.id.vehicleNumberInput,R.id.licenseInput,R.id.dobInput,R.id.drivingLicenseNumberInput,R.id.aadhaarNumberInput).forEach{v.findViewById<View>(it).isEnabled=p.editableFields.contains(fieldName(it))};MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.delivery_profile_edit_title).setView(v).setNegativeButton(R.string.cancel,null).setPositiveButton(R.string.delivery_profile_save){_,_->vm.save(DeliveryPartnerProfileUpdate(name.text?.toString()?.trim(),email.text?.toString()?.trim(),vehicle.text?.toString()?.trim(),number.text?.toString()?.trim(),license.text?.toString()?.trim(),dob.text?.toString()?.trim(),dl.text?.toString()?.trim(),aadhaar.text?.toString()?.trim()))}.show()}
 private fun fieldName(id:Int)=when(id){R.id.nameInput->"name";R.id.emailInput->"email";R.id.vehicleTypeInput->"vehicleType";R.id.vehicleNumberInput->"vehicleNumber";R.id.licenseInput->"licenseReference";R.id.dobInput->"dateOfBirth";R.id.drivingLicenseNumberInput->"drivingLicenseNumber";R.id.aadhaarNumberInput->"aadhaarNumber";else->""}
 override fun onDestroyView(){_binding=null;super.onDestroyView()}
}
