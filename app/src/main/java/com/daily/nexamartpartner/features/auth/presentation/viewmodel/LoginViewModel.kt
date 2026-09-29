package com.daily.nexamartpartner.features.auth.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.daily.nexamartpartner.core.result.AppResult
import com.daily.nexamartpartner.features.auth.domain.usecase.SendPartnerOtpUseCase
import com.daily.nexamartpartner.features.auth.domain.usecase.VerifyPartnerOtpUseCase
import com.daily.nexamartpartner.features.auth.presentation.state.AuthStateStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LoginUiState(
    val phone: String = "",
    val otp: String = "",
    val isSendingOtp: Boolean = false,
    val isVerifying: Boolean = false,
    val otpSent: Boolean = false,
    val expiresInSeconds: Int = 300,
    val devOtp: String? = null,
    val phoneError: String? = null,
    val otpError: String? = null,
    val formError: String? = null
)

class LoginViewModel(
    private val sendOtpUseCase: SendPartnerOtpUseCase,
    private val verifyOtpUseCase: VerifyPartnerOtpUseCase,
    private val authStateStore: AuthStateStore
) : ViewModel() {
    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun onPhoneChanged(value: String) = _uiState.update { it.copy(phone = value, phoneError = null, formError = null) }
    fun onOtpChanged(value: String) = _uiState.update { it.copy(otp = value.filter(Char::isDigit).take(6), otpError = null, formError = null) }

    fun sendOtp() {
        val phone = normalize(_uiState.value.phone)
        if (!PHONE_REGEX.matches(phone)) {
            _uiState.update { it.copy(phoneError = "Enter a valid 10-digit mobile number.") }
            return
        }
        _uiState.update { it.copy(phone = phone, isSendingOtp = true, formError = null) }
        viewModelScope.launch {
            when (val result = sendOtpUseCase(phone)) {
                is AppResult.Success -> _uiState.update { it.copy(isSendingOtp = false, otpSent = true, expiresInSeconds = result.data.expiresInSeconds, devOtp = result.data.devOtp, formError = null) }
                is AppResult.Failure -> _uiState.update { it.copy(isSendingOtp = false, formError = result.error.message) }
            }
        }
    }

    fun verifyOtp() {
        val s = _uiState.value
        if (!PHONE_REGEX.matches(normalize(s.phone))) { _uiState.update { it.copy(phoneError = "Enter a valid 10-digit mobile number.") }; return }
        if (!s.otp.matches(Regex("^\\d{6}$"))) { _uiState.update { it.copy(otpError = "Enter the 6-digit OTP.") }; return }
        _uiState.update { it.copy(isVerifying = true, formError = null) }
        viewModelScope.launch {
            when (val result = verifyOtpUseCase(normalize(s.phone), s.otp)) {
                is AppResult.Success -> { _uiState.update { it.copy(isVerifying = false, formError = null) }; authStateStore.setAuthenticated(result.data) }
                is AppResult.Failure -> _uiState.update { it.copy(isVerifying = false, formError = result.error.message) }
            }
        }
    }

    private fun normalize(value: String): String {
        var p = value.trim().replace(" ", "").replace("-", "")
        if (p.startsWith("+91")) p = p.removePrefix("+91") else if (p.startsWith("0091")) p = p.removePrefix("0091")
        return p
    }
    companion object { private val PHONE_REGEX = Regex("^[6-9]\\d{9}$") }
}
