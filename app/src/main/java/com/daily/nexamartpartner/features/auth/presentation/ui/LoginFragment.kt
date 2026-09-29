package com.daily.nexamartpartner.features.auth.presentation.ui

import android.os.Bundle
import android.view.View
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.daily.nexamartpartner.R
import com.daily.nexamartpartner.databinding.FragmentLoginBinding
import com.daily.nexamartpartner.di.appContainer
import com.daily.nexamartpartner.features.auth.presentation.viewmodel.LoginViewModel
import com.daily.nexamartpartner.features.auth.presentation.viewmodel.LoginViewModelFactory
import kotlinx.coroutines.launch

class LoginFragment : Fragment(R.layout.fragment_login) {
    private var _binding: FragmentLoginBinding? = null
    private val binding get() = requireNotNull(_binding)
    private val loginViewModel: LoginViewModel by viewModels {
        LoginViewModelFactory(requireContext().appContainer.sendPartnerOtpUseCase, requireContext().appContainer.verifyPartnerOtpUseCase, requireContext().appContainer.authStateStore)
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState); _binding = FragmentLoginBinding.bind(view)
        binding.identifierInputLayout.hint = "Mobile number"
        binding.identifierInputEditText.inputType = android.text.InputType.TYPE_CLASS_PHONE
        arguments?.getString("prefillIdentifier")?.takeIf { it.isNotBlank() }?.let { binding.identifierInputEditText.setText(it) }
        binding.passwordInputLayout.visibility = View.GONE
        binding.loginButton.text = "Send OTP"
        binding.createAccountButton.text = "New delivery partner? Create account"
        binding.identifierInputEditText.doAfterTextChanged { loginViewModel.onPhoneChanged(it?.toString().orEmpty()) }
        binding.passwordInputEditText.doAfterTextChanged { loginViewModel.onOtpChanged(it?.toString().orEmpty()) }
        binding.loginButton.setOnClickListener { if (loginViewModel.uiState.value.otpSent) loginViewModel.verifyOtp() else loginViewModel.sendOtp() }
        binding.createAccountButton.setOnClickListener { findNavController().navigate(R.id.createDeliveryAccountFragment) }
        collectState()
    }
    private fun collectState() { viewLifecycleOwner.lifecycleScope.launch { viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) { loginViewModel.uiState.collect { s ->
        binding.identifierInputLayout.error=s.phoneError; binding.passwordInputLayout.error=s.otpError
        binding.passwordInputLayout.hint="6-digit OTP"; binding.passwordInputLayout.visibility=if(s.otpSent) View.VISIBLE else View.GONE; binding.passwordInputEditText.inputType=android.text.InputType.TYPE_CLASS_NUMBER; binding.passwordInputEditText.visibility=View.VISIBLE
        binding.loginButton.isEnabled=!s.isSendingOtp&&!s.isVerifying; binding.loginButton.text=when{ s.isSendingOtp->"Sending OTP…";s.isVerifying->"Verifying…";s.otpSent->"Verify & Login";else->"Send OTP" }
        binding.loginErrorText.visibility=if(s.formError.isNullOrBlank()) View.GONE else View.VISIBLE; binding.loginErrorText.text=s.formError
        if(s.devOtp!=null){binding.loginErrorText.visibility=View.VISIBLE;binding.loginErrorText.text="DEV OTP: ${s.devOtp}"}
    } } } }
    override fun onDestroyView(){_binding=null;super.onDestroyView()}
}
