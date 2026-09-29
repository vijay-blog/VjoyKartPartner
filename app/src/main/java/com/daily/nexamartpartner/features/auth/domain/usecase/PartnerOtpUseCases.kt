package com.daily.nexamartpartner.features.auth.domain.usecase
import com.daily.nexamartpartner.core.result.AppResult
import com.daily.nexamartpartner.features.auth.data.model.OtpSendResponseDto
import com.daily.nexamartpartner.features.auth.domain.model.UserSession
import com.daily.nexamartpartner.features.auth.domain.repository.AuthRepository
class SendPartnerOtpUseCase(private val repository: AuthRepository){ suspend operator fun invoke(phone:String):AppResult<OtpSendResponseDto> = repository.sendPartnerOtp(phone) }
class VerifyPartnerOtpUseCase(private val repository: AuthRepository){ suspend operator fun invoke(phone:String,otp:String):AppResult<UserSession> = repository.verifyPartnerOtp(phone,otp) }
