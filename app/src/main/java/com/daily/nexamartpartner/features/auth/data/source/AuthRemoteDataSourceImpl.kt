package com.daily.nexamartpartner.features.auth.data.source

import com.daily.nexamartpartner.core.network.ApiCallExecutor
import com.daily.nexamartpartner.core.result.AppFailure
import com.daily.nexamartpartner.core.result.AppResult
import com.daily.nexamartpartner.core.result.FailureType
import com.daily.nexamartpartner.features.auth.data.contract.AuthRequestContract
import com.daily.nexamartpartner.features.auth.data.model.LoginResponseDto
import com.daily.nexamartpartner.features.auth.domain.model.LoginCredentials

class AuthRemoteDataSourceImpl(
    private val api: AuthApi,
    private val requestContract: AuthRequestContract,
    private val apiCallExecutor: ApiCallExecutor
) : AuthRemoteDataSource {

    override suspend fun login(credentials: LoginCredentials): AppResult<LoginResponseDto> {
        val payload = requestContract.buildLoginBody(credentials)
            ?: return AppResult.Failure(
                AppFailure(
                    message = "Authentication service is not configured yet. Please contact support.",
                    type = FailureType.CONTRACT_MISSING
                )
            )

        val response = apiCallExecutor.execute { api.login(payload) }
        return mapLoginFailure(response)
    }

    override suspend fun sendPartnerOtp(phone: String): AppResult<com.daily.nexamartpartner.features.auth.data.model.OtpSendResponseDto> =
        apiCallExecutor.execute { api.sendPartnerOtp(mapOf("phone" to phone.trim())) }

    override suspend fun verifyPartnerOtp(phone: String, otp: String): AppResult<LoginResponseDto> =
        apiCallExecutor.execute { api.verifyPartnerOtp(mapOf("phone" to phone.trim(), "otp" to otp.trim())) }

    override suspend fun refresh(refreshToken: String): AppResult<LoginResponseDto> {
        val payload = requestContract.buildRefreshBody(refreshToken)
            ?: return AppResult.Failure(
                AppFailure(
                    message = "Authentication service is not configured yet. Please contact support.",
                    type = FailureType.CONTRACT_MISSING
                )
            )

        return apiCallExecutor.execute { api.refresh(payload) }
    }

    override suspend fun logout(): AppResult<Unit> {
        return when (val response = apiCallExecutor.execute { api.logout() }) {
            is AppResult.Success -> AppResult.Success(Unit)
            is AppResult.Failure -> response
        }
    }

    private fun mapLoginFailure(result: AppResult<LoginResponseDto>): AppResult<LoginResponseDto> {
        if (result is AppResult.Failure && result.error.code in setOf(400, 401)) {
            return AppResult.Failure(
                result.error.copy(
                    message = "Invalid login details. Please try again.",
                    type = FailureType.UNAUTHORIZED
                )
            )
        }
        if (result is AppResult.Failure && result.error.type == FailureType.FORBIDDEN) {
            return AppResult.Failure(
                result.error.copy(message = "You do not have permission to access this application.")
            )
        }
        return result
    }
}
