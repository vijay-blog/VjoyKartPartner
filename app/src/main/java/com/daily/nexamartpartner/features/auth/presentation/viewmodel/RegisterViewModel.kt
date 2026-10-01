package com.daily.nexamartpartner.features.auth.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.daily.nexamartpartner.core.result.AppResult
import com.daily.nexamartpartner.features.auth.domain.model.RegistrationData
import com.daily.nexamartpartner.features.auth.domain.usecase.RegisterUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private val EMAIL_REGEX = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
private val PHONE_REGEX = Regex("^(?:\\+91[- ]?)?[6-9]\\d{9}$")

data class RegisterUiState(
    val name: String = "",
    val email: String = "",
    val phone: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val isSubmitting: Boolean = false,
    val nameError: String? = null,
    val emailError: String? = null,
    val phoneError: String? = null,
    val passwordError: String? = null,
    val confirmPasswordError: String? = null,
    val formError: String? = null,
    val successMessage: String? = null
)

class RegisterViewModel(
    private val registerUseCase: RegisterUseCase
) : ViewModel() {
    private val _uiState = MutableStateFlow(RegisterUiState())
    val uiState: StateFlow<RegisterUiState> = _uiState.asStateFlow()

    fun onNameChanged(value: String) = _uiState.update { it.copy(name = value, nameError = null, formError = null, successMessage = null) }
    fun onEmailChanged(value: String) = _uiState.update { it.copy(email = value, emailError = null, formError = null, successMessage = null) }
    fun onPhoneChanged(value: String) = _uiState.update { it.copy(phone = value, phoneError = null, formError = null, successMessage = null) }
    fun onPasswordChanged(value: String) = _uiState.update { it.copy(password = value, passwordError = null, confirmPasswordError = null, formError = null, successMessage = null) }
    fun onConfirmPasswordChanged(value: String) = _uiState.update { it.copy(confirmPassword = value, confirmPasswordError = null, formError = null, successMessage = null) }

    fun submitRegistration() {
        val current = _uiState.value
        if (current.isSubmitting) return

        val nameError = if (current.name.trim().length < 2) "Please enter your name." else null
        val emailError = if (!EMAIL_REGEX.matches(current.email.trim())) "Please enter a valid email address." else null
        val normalizedPhone = current.phone.trim().replace("-", "").replace(" ", "")
        val phoneError = if (!PHONE_REGEX.matches(normalizedPhone)) "Please enter a valid 10-digit Indian mobile number." else null
        val passwordError = if (current.password.length < 8) "Password must be at least 8 characters." else null
        val confirmError = if (current.confirmPassword != current.password) "Passwords do not match." else null

        if (nameError != null || emailError != null || phoneError != null || passwordError != null || confirmError != null) {
            _uiState.update {
                it.copy(
                    nameError = nameError,
                    emailError = emailError,
                    phoneError = phoneError,
                    passwordError = passwordError,
                    confirmPasswordError = confirmError
                )
            }
            return
        }

        val phone = com.daily.nexamartpartner.core.util.PhoneNumbers.normalizeForInput(normalizedPhone)
        _uiState.update { it.copy(isSubmitting = true, formError = null, successMessage = null) }

        viewModelScope.launch {
            when (val result = registerUseCase(
                RegistrationData(
                    name = current.name.trim(),
                    email = current.email.trim().lowercase(),
                    phone = phone,
                    password = current.password,
                    confirmPassword = current.confirmPassword
                )
            )) {
                is AppResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            formError = null,
                            successMessage = "Account created successfully. Please sign in with your phone number or email."
                        )
                    }
                }
                is AppResult.Failure -> {
                    _uiState.update { it.copy(isSubmitting = false, formError = result.error.message) }
                }
            }
        }
    }
}
