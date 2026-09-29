package com.daily.nexamartpartner.features.delivery.profile.data.repository

import com.daily.nexamartpartner.core.result.*
import com.daily.nexamartpartner.features.delivery.profile.data.source.DeliveryPartnerProfileDataSource
import com.daily.nexamartpartner.features.delivery.profile.domain.model.*
import com.daily.nexamartpartner.features.delivery.profile.domain.repository.DeliveryPartnerProfileRepository

class DeliveryPartnerProfileRepositoryImpl(private val source: DeliveryPartnerProfileDataSource) : DeliveryPartnerProfileRepository {
    private fun map(d: com.daily.nexamartpartner.features.delivery.profile.data.model.DeliveryPartnerProfileDto) = DeliveryPartnerProfile(d.id,d.name,d.phone,d.email,d.profileImageUrl,d.verificationStatus,d.accountStatus,d.vehicleType,d.vehicleNumber,d.licenseReference,d.dateOfBirth,d.drivingLicenseNumber,d.aadhaarNumber,d.aadhaarPhotoUrl,d.registeredAt,d.lastActiveAt,d.editableFields?.toSet() ?: emptySet())
    override suspend fun getProfile(): AppResult<DeliveryPartnerProfile> = when(val r=source.getProfile()){is AppResult.Success->AppResult.Success(map(r.data));is AppResult.Failure->r}
    override suspend fun updateProfile(u: DeliveryPartnerProfileUpdate): AppResult<DeliveryPartnerProfile> = when(val r=source.updateProfile(u)){is AppResult.Success->AppResult.Success(map(r.data));is AppResult.Failure->r}
}
