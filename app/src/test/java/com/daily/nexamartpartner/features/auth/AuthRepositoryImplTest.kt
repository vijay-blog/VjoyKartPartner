package com.daily.nexamartpartner.features.auth

import com.daily.nexamartpartner.core.result.AppFailure
import com.daily.nexamartpartner.core.result.AppResult
import com.daily.nexamartpartner.core.result.FailureType
import com.daily.nexamartpartner.features.auth.data.model.AuthUserDto
import com.daily.nexamartpartner.features.auth.data.model.LoginResponseDto
import com.daily.nexamartpartner.features.auth.data.model.OtpSendResponseDto
import com.daily.nexamartpartner.features.auth.data.repository.AuthRepositoryImpl
import com.daily.nexamartpartner.features.auth.data.source.AuthRemoteDataSource
import com.daily.nexamartpartner.features.auth.domain.model.LoginCredentials
import com.daily.nexamartpartner.features.auth.domain.model.UserRole
import com.daily.nexamartpartner.features.auth.domain.session.SessionManager
import com.daily.nexamartpartner.testutil.InMemorySessionStorage
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthRepositoryImplTest {
    @Test
    fun `login success persists session`() = runTest {
        val remote = FakeAuthRemoteDataSource(
            loginResult = AppResult.Success(loginResponse("ADMIN"))
        )
        val repository = AuthRepositoryImpl(
            remoteDataSource = remote,
            sessionManager = SessionManager(InMemorySessionStorage())
        )

        val result = repository.login(LoginCredentials("identifier", "password"))

        assertTrue(result is AppResult.Success)
        val success = result as AppResult.Success
        assertEquals(UserRole.ADMIN, success.data.role)
    }

    @Test
    fun `login failure propagates`() = runTest {
        val remote = FakeAuthRemoteDataSource(
            loginResult = AppResult.Failure(
                AppFailure("Invalid login details. Please try again.", 401, FailureType.UNAUTHORIZED)
            )
        )
        val repository = AuthRepositoryImpl(
            remoteDataSource = remote,
            sessionManager = SessionManager(InMemorySessionStorage())
        )

        val result = repository.login(LoginCredentials("identifier", "password"))

        assertTrue(result is AppResult.Failure)
        val failure = result as AppResult.Failure
        assertEquals(FailureType.UNAUTHORIZED, failure.error.type)
    }

    @Test
    fun `unsupported role is rejected`() = runTest {
        val remote = FakeAuthRemoteDataSource(
            loginResult = AppResult.Success(loginResponse("CUSTOMER"))
        )
        val repository = AuthRepositoryImpl(
            remoteDataSource = remote,
            sessionManager = SessionManager(InMemorySessionStorage())
        )

        val result = repository.login(LoginCredentials("identifier", "password"))

        assertTrue(result is AppResult.Failure)
        val failure = result as AppResult.Failure
        assertEquals(FailureType.UNSUPPORTED_ROLE, failure.error.type)
    }

    @Test
    fun `network failure propagates`() = runTest {
        val remote = FakeAuthRemoteDataSource(
            loginResult = AppResult.Failure(
                AppFailure(
                    "Unable to connect to the server. Please check your internet connection.",
                    type = FailureType.NETWORK
                )
            )
        )
        val repository = AuthRepositoryImpl(
            remoteDataSource = remote,
            sessionManager = SessionManager(InMemorySessionStorage())
        )

        val result = repository.login(LoginCredentials("identifier", "password"))

        assertTrue(result is AppResult.Failure)
        val failure = result as AppResult.Failure
        assertEquals(FailureType.NETWORK, failure.error.type)
    }

    private fun loginResponse(role: String): LoginResponseDto {
        return LoginResponseDto(
            accessToken = "access",
            refreshToken = "refresh",
            user = AuthUserDto(
                id = 1L,
                name = "User",
                phone = "9999999999",
                email = null,
                role = role
            )
        )
    }

    private class FakeAuthRemoteDataSource(
        private val loginResult: AppResult<LoginResponseDto>
    ) : AuthRemoteDataSource {
        override suspend fun login(credentials: LoginCredentials): AppResult<LoginResponseDto> = loginResult
        override suspend fun sendPartnerOtp(phone: String): AppResult<OtpSendResponseDto> =
            AppResult.Success(OtpSendResponseDto(success = true))

        override suspend fun verifyPartnerOtp(
            phone: String,
            otp: String
        ): AppResult<LoginResponseDto> = loginResult

        override suspend fun refresh(refreshToken: String): AppResult<LoginResponseDto> = loginResult

        override suspend fun logout(): AppResult<Unit> = AppResult.Success(Unit)
    }
}
