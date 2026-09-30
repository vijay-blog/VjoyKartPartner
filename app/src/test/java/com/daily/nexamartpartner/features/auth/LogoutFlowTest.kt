package com.daily.nexamartpartner.features.auth

import com.daily.nexamartpartner.core.result.AppResult
import com.daily.nexamartpartner.features.auth.data.model.AuthUserDto
import com.daily.nexamartpartner.features.auth.data.model.LoginResponseDto
import com.daily.nexamartpartner.features.auth.data.model.OtpSendResponseDto
import com.daily.nexamartpartner.features.auth.data.repository.AuthRepositoryImpl
import com.daily.nexamartpartner.features.auth.data.source.AuthRemoteDataSource
import com.daily.nexamartpartner.features.auth.domain.model.LoginCredentials
import com.daily.nexamartpartner.features.auth.domain.model.UserRole
import com.daily.nexamartpartner.features.auth.domain.model.UserSession
import com.daily.nexamartpartner.features.auth.domain.session.SessionManager
import com.daily.nexamartpartner.testutil.InMemorySessionStorage
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LogoutFlowTest {
    @Test
    fun `logout clears session`() = runTest {
        val storage = InMemorySessionStorage()
        val sessionManager = SessionManager(storage)
        val repository = AuthRepositoryImpl(
            remoteDataSource = FakeAuthRemoteDataSource(),
            sessionManager = sessionManager
        )

        repository.login(LoginCredentials("id", "pass"))
        repository.logout()

        assertNull(sessionManager.currentSession.value)
        assertNull(storage.session)
    }

    private class FakeAuthRemoteDataSource : AuthRemoteDataSource {
        override suspend fun login(credentials: LoginCredentials): AppResult<LoginResponseDto> {
            return AppResult.Success(
                LoginResponseDto(
                    accessToken = "access",
                    refreshToken = "refresh",
                    user = AuthUserDto(
                        id = 7L,
                        name = "Delivery",
                        phone = "9999999999",
                        email = null,
                        role = UserRole.DELIVERY_PARTNER.name
                    )
                )
            )
        }

        override suspend fun sendPartnerOtp(phone: String): AppResult<OtpSendResponseDto> =
            AppResult.Success(OtpSendResponseDto(success = true))

        override suspend fun verifyPartnerOtp(
            phone: String,
            otp: String
        ): AppResult<LoginResponseDto> = login(LoginCredentials(phone, otp))

        override suspend fun refresh(refreshToken: String): AppResult<LoginResponseDto> {
            return login(LoginCredentials("id", "pass"))
        }

        override suspend fun logout(): AppResult<Unit> = AppResult.Success(Unit)
    }
}
