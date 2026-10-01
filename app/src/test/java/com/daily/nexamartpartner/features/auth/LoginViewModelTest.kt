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

    @Test
    fun `send OTP normalizes a plus 91 number before calling the API`() = runTest {
        val requested = mutableListOf<String>()
        val viewModel = buildViewModel(AppResult.Success(adminSession()), onSendOtp = { requested += it })
        viewModel.onPhoneChanged("+91 99590 95202")

        viewModel.sendOtp()
        advanceUntilIdle()

        assertEquals(listOf("9959095202"), requested)
        assertEquals("9959095202", viewModel.uiState.value.phone)
        assertTrue(viewModel.uiState.value.otpSent)
        assertTrue(viewModel.uiState.value.canResend)
    }

    @Test
    fun `repeated send taps within the cooldown do not spam the API`() = runTest {
        val requested = mutableListOf<String>()
        var now = 1_000_000L
        val viewModel = buildViewModel(
            AppResult.Success(adminSession()),
            clock = { now },
            onSendOtp = { requested += it }
        )
        viewModel.onPhoneChanged("9959095202")

        viewModel.sendOtp()
        advanceUntilIdle()
        viewModel.resendOtp()
        viewModel.resendOtp()
        viewModel.resendOtp()
        advanceUntilIdle()

        assertEquals(1, requested.size)
        assertTrue(viewModel.uiState.value.formError!!.contains("Please wait"))
    }

    @Test
    fun `resend is allowed once the cooldown has elapsed`() = runTest {
        val requested = mutableListOf<String>()
        var now = 1_000_000L
        val viewModel = buildViewModel(
            AppResult.Success(adminSession()),
            clock = { now },
            onSendOtp = { requested += it }
        )
        viewModel.onPhoneChanged("9959095202")

        viewModel.sendOtp()
        advanceUntilIdle()
        now += 31_000L
        viewModel.resendOtp()
        advanceUntilIdle()

        assertEquals(2, requested.size)
    }

    @Test
    fun `backend error message is shown to the user instead of a generic message`() = runTest {
        val repository = object : AuthRepository {
            override suspend fun login(credentials: LoginCredentials): AppResult<UserSession> =
                AppResult.Success(adminSession())
            override suspend fun sendPartnerOtp(phone: String): AppResult<OtpSendResponseDto> =
                AppResult.Failure(
                    AppFailure(
                        "OTP provider is temporarily unavailable. Please try again. (Error ID: abc-123)",
                        503,
                        FailureType.SERVER
                    )
                )
            override suspend fun verifyPartnerOtp(phone: String, otp: String): AppResult<UserSession> =
                AppResult.Success(adminSession())
            override suspend fun restoreSession(): AppResult<UserSession?> = AppResult.Success(null)
            override suspend fun logout(): AppResult<Unit> = AppResult.Success(Unit)
            override suspend fun refreshToken(): AppResult<UserSession> = AppResult.Success(adminSession())
        }
        val viewModel = LoginViewModel(
            SendPartnerOtpUseCase(repository),
            VerifyPartnerOtpUseCase(repository),
            AuthStateStore()
        )
        viewModel.onPhoneChanged("9959095202")

        viewModel.sendOtp()
        advanceUntilIdle()

        assertEquals(
            "OTP provider is temporarily unavailable. Please try again. (Error ID: abc-123)",
            viewModel.uiState.value.formError
        )
    }

    private fun buildViewModel(
        result: AppResult<UserSession>,
        authStateStore: AuthStateStore = AuthStateStore(),
        clock: () -> Long = System::currentTimeMillis,
        onSendOtp: (String) -> Unit = {}
    ): LoginViewModel {
        val repository = object : AuthRepository {
            override suspend fun login(credentials: LoginCredentials): AppResult<UserSession> = result
            override suspend fun sendPartnerOtp(phone: String): AppResult<OtpSendResponseDto> {
                onSendOtp(phone)
                return AppResult.Success(OtpSendResponseDto(success = true))
            }
            override suspend fun verifyPartnerOtp(phone: String, otp: String): AppResult<UserSession> = result
            override suspend fun restoreSession(): AppResult<UserSession?> = AppResult.Success(null)
            override suspend fun logout(): AppResult<Unit> = AppResult.Success(Unit)
            override suspend fun refreshToken(): AppResult<UserSession> = result
        }
        return LoginViewModel(
            SendPartnerOtpUseCase(repository),
            VerifyPartnerOtpUseCase(repository),
            authStateStore,
            clock
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
