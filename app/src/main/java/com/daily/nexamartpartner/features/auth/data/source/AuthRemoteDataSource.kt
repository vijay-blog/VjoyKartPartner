package com.daily.nexamartpartner.features.auth.data.source

import com.daily.nexamartpartner.core.result.AppResult
import com.daily.nexamartpartner.features.auth.data.model.LoginResponseDto
import com.daily.nexamartpartner.features.auth.data.model.OtpSendResponseDto
import com.daily.nexamartpartner.features.auth.domain.model.LoginCredentials

interface AuthRemoteDataSource {
    suspend fun login(credentials: LoginCredentials): AppResult<LoginResponseDto>
    suspend fun sendPartnerOtp(phone: String): AppResult<OtpSendResponseDto>
    suspend fun verifyPartnerOtp(phone: String, otp: String): AppResult<LoginResponseDto>
    suspend fun refresh(refreshToken: String): AppResult<LoginResponseDto>
    suspend fun logout(): AppResult<Unit>
}
