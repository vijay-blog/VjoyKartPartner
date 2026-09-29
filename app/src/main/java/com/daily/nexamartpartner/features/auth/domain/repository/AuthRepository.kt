package com.daily.nexamartpartner.features.auth.domain.repository

import com.daily.nexamartpartner.core.result.AppResult
import com.daily.nexamartpartner.features.auth.domain.model.LoginCredentials
import com.daily.nexamartpartner.features.auth.data.model.OtpSendResponseDto
import com.daily.nexamartpartner.features.auth.domain.model.UserSession

interface AuthRepository {
    suspend fun login(credentials: LoginCredentials): AppResult<UserSession>
    suspend fun sendPartnerOtp(phone: String): AppResult<OtpSendResponseDto>
    suspend fun verifyPartnerOtp(phone: String, otp: String): AppResult<UserSession>
    suspend fun restoreSession(): AppResult<UserSession?>
    suspend fun logout(): AppResult<Unit>
    suspend fun refreshToken(): AppResult<UserSession>
}
