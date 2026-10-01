package com.daily.nexamartpartner.features.auth.presentation.ui

import android.app.DatePickerDialog
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.daily.nexamartpartner.R
import com.daily.nexamartpartner.core.result.AppResult
import com.daily.nexamartpartner.databinding.FragmentCreateDeliveryAccountBinding
import com.daily.nexamartpartner.di.appContainer
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class CreateDeliveryAccountFragment : Fragment(R.layout.fragment_create_delivery_account) {
    private var _binding: FragmentCreateDeliveryAccountBinding? = null
    private val binding get() = requireNotNull(_binding)
    private var aadhaarPhotoUri: Uri? = null
    private val photoPicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) { aadhaarPhotoUri = uri; binding.aadhaarPhotoButton.text = "Aadhaar photo selected ✓" }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState); _binding = FragmentCreateDeliveryAccountBinding.bind(view)
        binding.dobInput.setOnClickListener { pickDob() }
        binding.aadhaarPhotoButton.setOnClickListener { photoPicker.launch("image/*") }
        binding.createAccountButton.setOnClickListener { createAccount() }
        binding.createAccountBackButton.setOnClickListener { findNavController().navigateUp() }
    }

    private fun pickDob() {
        val c=Calendar.getInstance(); c.add(Calendar.YEAR,-18)
        DatePickerDialog(requireContext(), { _,y,m,d -> binding.dobInput.setText(String.format(Locale.US,"%04d-%02d-%02d",y,m+1,d)) }, c.get(Calendar.YEAR),c.get(Calendar.MONTH),c.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun createAccount() {
        binding.createAccountErrorText.visibility=View.GONE
        val name=binding.createNameInput.text?.toString()?.trim().orEmpty(); val phone=normalize(binding.createPhoneInput.text?.toString().orEmpty()); val email=binding.createEmailInput.text?.toString()?.trim().orEmpty()
        val password=binding.createPasswordInput.text?.toString().orEmpty(); val confirm=binding.createConfirmPasswordInput.text?.toString().orEmpty(); val dob=binding.dobInput.text?.toString()?.trim().orEmpty(); val vehicle=binding.vehicleTypeInput.text?.toString()?.trim().orEmpty(); val bike=binding.bikeNumberInput.text?.toString()?.trim().orEmpty(); val dl=binding.drivingLicenseInput.text?.toString()?.trim().orEmpty(); val aadhaar=binding.aadhaarNumberInput.text?.toString()?.trim().orEmpty()
        when { name.length<2->showError("Please enter your full name.");!PHONE_REGEX.matches(phone)->showError("Enter a valid 10-digit mobile number.");!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()->showError("Enter a valid email address.");password.length<8->showError("Password must be at least 8 characters.");password!=confirm->showError("Passwords do not match.");dob.isBlank()->showError("Select your date of birth.");vehicle.isBlank()->showError("Enter your bike type.");bike.isBlank()->showError("Enter your bike number.");dl.isBlank()->showError("Enter your driving licence number.");aadhaar.length!=12->showError("Enter a valid 12-digit Aadhaar number.");aadhaarPhotoUri==null->showError("Please upload your Aadhaar photo.");else->submit(name,phone,email,password,confirm,dob,vehicle,bike,dl,aadhaar,aadhaarPhotoUri!!) }
    }

    private fun submit(name:String,phone:String,email:String,password:String,confirm:String,dob:String,vehicle:String,bike:String,dl:String,aadhaar:String,photo:Uri){
        binding.createAccountButton.isEnabled=false; binding.createAccountButton.text="Creating secure profile…"
        viewLifecycleOwner.lifecycleScope.launch {
            val resolver=requireContext().contentResolver; val bytes=resolver.openInputStream(photo)?.use{it.readBytes()} ?: return@launch showError("Unable to read Aadhaar photo.")
            if(bytes.size>5*1024*1024){binding.createAccountButton.isEnabled=true;binding.createAccountButton.text="Create account";return@launch showError("Aadhaar photo must be 5 MB or smaller.")}
            val media=(resolver.getType(photo) ?: "image/jpeg").toMediaTypeOrNull(); val photoPart=MultipartBody.Part.createFormData("aadhaarPhoto","aadhaar.jpg",bytes.toRequestBody(media))
            fun String.rb()=toRequestBody("text/plain".toMediaTypeOrNull())
            when(val result=requireContext().appContainer.apiCallExecutor.execute{requireContext().appContainer.deliveryOnboardingApi.register(name.rb(),email.rb(),phone.rb(),password.rb(),confirm.rb(),dob.rb(),vehicle.rb(),bike.rb(),dl.rb(),aadhaar.rb(),photoPart)}){
                is AppResult.Success->{Toast.makeText(requireContext(),"Account created. You can now login with OTP.",Toast.LENGTH_LONG).show();findNavController().navigate(R.id.loginFragment,Bundle().apply{putString("prefillIdentifier",phone)})}
                is AppResult.Failure->{binding.createAccountButton.isEnabled=true;binding.createAccountButton.text="Create account";showError(result.error.message)}
            }
        }
    }
    private fun normalize(value:String):String = com.daily.nexamartpartner.core.util.PhoneNumbers.normalizeForInput(value)
    private fun showError(message:String){binding.createAccountErrorText.text=message;if(isAdded)binding.createAccountErrorText.visibility=View.VISIBLE}
    override fun onDestroyView(){_binding=null;super.onDestroyView()}
    companion object{private val PHONE_REGEX=Regex("^[6-9]\\d{9}$")}
}
