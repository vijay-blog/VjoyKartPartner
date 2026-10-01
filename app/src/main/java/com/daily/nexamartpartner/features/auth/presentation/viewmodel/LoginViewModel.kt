package com.daily.nexamartpartner.features.auth.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.daily.nexamartpartner.core.result.AppResult
import com.daily.nexamartpartner.core.util.PhoneNumbers
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
    val canResend: Boolean = false,
    val phoneError: String? = null,
    val otpError: String? = null,
    val formError: String? = null
)

class LoginViewModel(
    private val sendOtpUseCase: SendPartnerOtpUseCase,
    private val verifyOtpUseCase: VerifyPartnerOtpUseCase,
    private val authStateStore: AuthStateStore,
    private val clock: () -> Long = System::currentTimeMillis
) : ViewModel() {
    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    private var lastOtpRequestAtMillis: Long = 0L

    fun onPhoneChanged(value: String) =
        _uiState.update { it.copy(phone = value, phoneError = null, formError = null) }

    fun onOtpChanged(value: String) =
        _uiState.update { it.copy(otp = value.filter(Char::isDigit).take(6), otpError = null, formError = null) }

    fun sendOtp() = requestOtp(isResend = false)

    /** Resend reuses the same endpoint; the backend remains the authoritative cooldown. */
    fun resendOtp() = requestOtp(isResend = true)

    private fun requestOtp(isResend: Boolean) {
        val phone = PhoneNumbers.normalizeIndianMobileOrNull(_uiState.value.phone)
        if (phone == null) {
            _uiState.update { it.copy(phoneError = "Enter a valid 10-digit mobile number.") }
            return
        }
        val elapsed = clock() - lastOtpRequestAtMillis
        if (lastOtpRequestAtMillis != 0L && elapsed < RESEND_COOLDOWN_MILLIS) {
            val wait = (RESEND_COOLDOWN_MILLIS - elapsed + 999) / 1000
            _uiState.update { it.copy(formError = "Please wait $wait seconds before requesting another OTP.") }
            return
        }
        lastOtpRequestAtMillis = clock()
        _uiState.update { it.copy(phone = phone, isSendingOtp = true, canResend = false, formError = null) }
        viewModelScope.launch {
            when (val result = sendOtpUseCase(phone)) {
                is AppResult.Success -> _uiState.update {
                    it.copy(
                        isSendingOtp = false,
                        otpSent = true,
                        canResend = true,
                        expiresInSeconds = result.data.expiresInSeconds,
                        devOtp = result.data.devOtp,
                        formError = null
                    )
                }
                is AppResult.Failure -> {
                    // A failed request never produced an OTP, so do not hold the user in cooldown.
                    lastOtpRequestAtMillis = 0L
                    _uiState.update {
                        it.copy(
                            isSendingOtp = false,
                            canResend = isResend || it.otpSent,
                            formError = result.error.message
                        )
                    }
                }
            }
        }
    }

    fun verifyOtp() {
        val s = _uiState.value
        val phone = PhoneNumbers.normalizeIndianMobileOrNull(s.phone)
        if (phone == null) {
            _uiState.update { it.copy(phoneError = "Enter a valid 10-digit mobile number.") }
            return
        }
        if (!s.otp.matches(OTP_REGEX)) {
            _uiState.update { it.copy(otpError = "Enter the 6-digit OTP.") }
            return
        }
        _uiState.update { it.copy(isVerifying = true, formError = null) }
        viewModelScope.launch {
            when (val result = verifyOtpUseCase(phone, s.otp)) {
                is AppResult.Success -> {
                    _uiState.update { it.copy(isVerifying = false, formError = null) }
                    authStateStore.setAuthenticated(result.data)
                }
                is AppResult.Failure -> _uiState.update {
                    it.copy(isVerifying = false, formError = result.error.message)
                }
            }
        }
    }

    companion object {
        private val OTP_REGEX = Regex("^\\d{6}$")
        private const val RESEND_COOLDOWN_MILLIS = 30_000L
    }
}
