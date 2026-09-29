package com.daily.nexamartpartner.features.auth.data.source

import com.daily.nexamartpartner.features.auth.data.model.LoginResponseDto
import com.daily.nexamartpartner.features.auth.data.model.OtpSendResponseDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface AuthApi {
    @POST("auth/login")
    suspend fun login(@Body body: Map<String, String>): Response<LoginResponseDto>

    @POST("auth/partner/send-otp")
    suspend fun sendPartnerOtp(@Body body: Map<String, String>): Response<OtpSendResponseDto>

    @POST("auth/partner/verify-otp")
    suspend fun verifyPartnerOtp(@Body body: Map<String, String>): Response<LoginResponseDto>

    @POST("auth/refresh")
    suspend fun refresh(@Body body: Map<String, String>): Response<LoginResponseDto>

    @POST("auth/logout")
    suspend fun logout(): Response<Unit>
}
