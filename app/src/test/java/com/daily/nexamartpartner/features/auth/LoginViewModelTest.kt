package com.daily.nexamartpartner.features.auth

import com.daily.nexamartpartner.core.result.AppFailure
import com.daily.nexamartpartner.core.result.AppResult
import com.daily.nexamartpartner.core.result.FailureType
import com.daily.nexamartpartner.features.auth.domain.model.AuthState
import com.daily.nexamartpartner.features.auth.domain.model.UserRole
import com.daily.nexamartpartner.features.auth.domain.model.UserSession
import com.daily.nexamartpartner.features.auth.domain.repository.AuthRepository
import com.daily.nexamartpartner.features.auth.data.model.OtpSendResponseDto
import com.daily.nexamartpartner.features.auth.domain.model.LoginCredentials
import com.daily.nexamartpartner.features.auth.domain.usecase.SendPartnerOtpUseCase
import com.daily.nexamartpartner.features.auth.domain.usecase.VerifyPartnerOtpUseCase
import com.daily.nexamartpartner.features.auth.presentation.state.AuthStateStore
import com.daily.nexamartpartner.features.auth.presentation.viewmodel.LoginViewModel
import com.daily.nexamartpartner.testutil.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `initial state is empty`() {
        val viewModel = buildViewModel(AppResult.Success(adminSession()))
        assertEquals("", viewModel.uiState.value.phone)
        assertEquals("", viewModel.uiState.value.otp)
    }

    @Test
    fun `invalid phone sets phone error`() = runTest {
        val viewModel = buildViewModel(AppResult.Success(adminSession()))

        viewModel.sendOtp()
        advanceUntilIdle()

        assertEquals("Enter a valid 10-digit mobile number.", viewModel.uiState.value.phoneError)
    }

    @Test
    fun `OTP verification success updates auth state`() = runTest {
        val authStateStore = AuthStateStore()
        val viewModel = buildViewModel(AppResult.Success(adminSession()), authStateStore)
        viewModel.onPhoneChanged("9999999999")
        viewModel.onOtpChanged("123456")

        viewModel.verifyOtp()
        advanceUntilIdle()

        assertTrue(authStateStore.authState.value is AuthState.AuthenticatedAdmin)
    }

    @Test
    fun `OTP verification failure exposes error`() = runTest {
        val viewModel = buildViewModel(
            AppResult.Failure(
                AppFailure("Invalid login details. Please try again.", 401, FailureType.UNAUTHORIZED)
            )
        )
        viewModel.onPhoneChanged("9999999999")
        viewModel.onOtpChanged("123456")

        viewModel.verifyOtp()
        advanceUntilIdle()

        assertEquals("Invalid login details. Please try again.", viewModel.uiState.value.formError)
    }

    @Test
    fun `invalid OTP sets OTP error`() = runTest {
        val viewModel = buildViewModel(AppResult.Success(adminSession()))
        viewModel.onPhoneChanged("9999999999")
        viewModel.onOtpChanged("123")

        viewModel.verifyOtp()
        advanceUntilIdle()

        assertEquals("Enter the 6-digit OTP.", viewModel.uiState.value.otpError)
    }

    private fun buildViewModel(
        result: AppResult<UserSession>,
        authStateStore: AuthStateStore = AuthStateStore()
    ): LoginViewModel {
        val repository = object : AuthRepository {
            override suspend fun login(credentials: LoginCredentials): AppResult<UserSession> = result
            override suspend fun sendPartnerOtp(phone: String): AppResult<OtpSendResponseDto> =
                AppResult.Success(OtpSendResponseDto(success = true))
            override suspend fun verifyPartnerOtp(phone: String, otp: String): AppResult<UserSession> = result
            override suspend fun restoreSession(): AppResult<UserSession?> = AppResult.Success(null)
            override suspend fun logout(): AppResult<Unit> = AppResult.Success(Unit)
            override suspend fun refreshToken(): AppResult<UserSession> = result
        }
        return LoginViewModel(
            SendPartnerOtpUseCase(repository),
            VerifyPartnerOtpUseCase(repository),
            authStateStore
        )
    }

    private fun adminSession(): UserSession {
        return UserSession(
            accessToken = "access",
            refreshToken = "refresh",
            userId = 1L,
            name = "Admin",
            contact = "9999999999",
            role = UserRole.ADMIN
        )
    }
}
