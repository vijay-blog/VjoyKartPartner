package com.daily.nexamartpartner.features.auth.data.repository

import com.daily.nexamartpartner.core.result.AppFailure
import com.daily.nexamartpartner.core.result.AppResult
import com.daily.nexamartpartner.core.result.FailureType
import com.daily.nexamartpartner.features.auth.data.model.LoginResponseDto
import com.daily.nexamartpartner.features.auth.data.source.AuthRemoteDataSource
import com.daily.nexamartpartner.features.auth.domain.model.LoginCredentials
import com.daily.nexamartpartner.features.auth.domain.model.UserRole
import com.daily.nexamartpartner.features.auth.domain.model.UserSession
import com.daily.nexamartpartner.features.auth.domain.repository.AuthRepository
import com.daily.nexamartpartner.features.auth.domain.session.SessionManager

class AuthRepositoryImpl(
    private val remoteDataSource: AuthRemoteDataSource,
    private val sessionManager: SessionManager
) : AuthRepository {
    override suspend fun login(credentials: LoginCredentials): AppResult<UserSession> {
        return when (val result = remoteDataSource.login(credentials)) {
            is AppResult.Success -> mapAndPersistSession(result.data)
            is AppResult.Failure -> result
        }
    }

    override suspend fun sendPartnerOtp(phone: String): AppResult<com.daily.nexamartpartner.features.auth.data.model.OtpSendResponseDto> = remoteDataSource.sendPartnerOtp(phone)

    override suspend fun verifyPartnerOtp(phone: String, otp: String): AppResult<UserSession> {
        return when (val result = remoteDataSource.verifyPartnerOtp(phone, otp)) {
            is AppResult.Success -> mapAndPersistSession(result.data)
            is AppResult.Failure -> result
        }
    }

    override suspend fun restoreSession(): AppResult<UserSession?> {
        return try {
            AppResult.Success(sessionManager.initialize())
        } catch (throwable: Throwable) {
            AppResult.Failure(
                AppFailure(
                    message = throwable.message ?: "Unable to restore session.",
                    type = FailureType.UNKNOWN
                )
            )
        }
    }

    override suspend fun logout(): AppResult<Unit> {
        remoteDataSource.logout()
        sessionManager.clearSession()
        return AppResult.Success(Unit)
    }

    override suspend fun refreshToken(): AppResult<UserSession> {
        val existingSession = sessionManager.currentSession.value
            ?: return AppResult.Failure(
                AppFailure(
                    message = "Your session has expired. Please sign in again.",
                    type = FailureType.UNAUTHORIZED
                )
            )
        return when (val result = remoteDataSource.refresh(existingSession.refreshToken)) {
            is AppResult.Success -> mapAndPersistSession(result.data)
            is AppResult.Failure -> {
                sessionManager.clearSession()
                result
            }
        }
    }

    private suspend fun mapAndPersistSession(dto: LoginResponseDto): AppResult<UserSession> {
        val user = dto.user
            ?: return AppResult.Failure(
                AppFailure(
                    message = "Invalid login response from server.",
                    type = FailureType.SERVER
                )
            )
        val accessToken = dto.accessToken
            ?: return AppResult.Failure(
                AppFailure(
                    message = "Invalid login response from server.",
                    type = FailureType.SERVER
                )
            )
        val refreshToken = dto.refreshToken
            ?: return AppResult.Failure(
                AppFailure(
                    message = "Invalid login response from server.",
                    type = FailureType.SERVER
                )
            )
        val userId = user.id
            ?: return AppResult.Failure(
                AppFailure(
                    message = "Invalid login response from server.",
                    type = FailureType.SERVER
                )
            )
        val role = UserRole.fromRaw(user.role)
        if (role == UserRole.UNSUPPORTED) {
            return AppResult.Failure(
                AppFailure(
                    message = "This application is only for Admin and Delivery Partners.",
                    type = FailureType.UNSUPPORTED_ROLE
                )
            )
        }

        val session = UserSession(
            accessToken = accessToken,
            refreshToken = refreshToken,
            userId = userId,
            name = user.name.orEmpty(),
            contact = user.phone ?: user.email,
            role = role
        )
        sessionManager.saveSession(session)
        return AppResult.Success(session)
    }
}
