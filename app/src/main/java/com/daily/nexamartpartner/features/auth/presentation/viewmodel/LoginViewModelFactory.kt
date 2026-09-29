package com.daily.nexamartpartner.features.auth.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.daily.nexamartpartner.features.auth.domain.usecase.SendPartnerOtpUseCase
import com.daily.nexamartpartner.features.auth.domain.usecase.VerifyPartnerOtpUseCase
import com.daily.nexamartpartner.features.auth.presentation.state.AuthStateStore

class LoginViewModelFactory(
    private val sendOtpUseCase: SendPartnerOtpUseCase,
    private val verifyOtpUseCase: VerifyPartnerOtpUseCase,
    private val authStateStore: AuthStateStore
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = LoginViewModel(sendOtpUseCase, verifyOtpUseCase, authStateStore) as T
}
