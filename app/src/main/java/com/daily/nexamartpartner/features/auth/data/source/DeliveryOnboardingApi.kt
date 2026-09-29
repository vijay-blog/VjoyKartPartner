package com.daily.nexamartpartner.features.auth.data.source

import com.daily.nexamartpartner.features.auth.data.model.RegistrationResponseDto
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

interface DeliveryOnboardingApi {
    @Multipart
    @POST("auth/partner/register")
    suspend fun register(
        @Part("name") name: RequestBody,
        @Part("email") email: RequestBody,
        @Part("phone") phone: RequestBody,
        @Part("password") password: RequestBody,
        @Part("confirmPassword") confirmPassword: RequestBody,
        @Part("dateOfBirth") dateOfBirth: RequestBody,
        @Part("vehicleType") vehicleType: RequestBody,
        @Part("vehicleNumber") vehicleNumber: RequestBody,
        @Part("drivingLicenseNumber") drivingLicenseNumber: RequestBody,
        @Part("aadhaarNumber") aadhaarNumber: RequestBody,
        @Part aadhaarPhoto: MultipartBody.Part
    ): Response<RegistrationResponseDto>
}
