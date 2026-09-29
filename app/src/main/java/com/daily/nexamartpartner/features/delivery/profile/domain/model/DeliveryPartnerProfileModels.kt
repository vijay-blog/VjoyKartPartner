package com.daily.nexamartpartner.features.delivery.profile.domain.model

data class DeliveryPartnerProfile(
    val id: Long?, val name: String?, val phone: String?, val email: String?,
    val profileImageUrl: String?, val verificationStatus: String?, val accountStatus: String?,
    val vehicleType: String?, val vehicleNumber: String?, val licenseReference: String?,
    val dateOfBirth: String?, val drivingLicenseNumber: String?, val aadhaarNumber: String?, val aadhaarPhotoUrl: String?,
    val registeredAt: String?, val lastActiveAt: String?, val editableFields: Set<String> = emptySet()
)

data class DeliveryPartnerProfileUpdate(
    val name: String?, val email: String?, val vehicleType: String?,
    val vehicleNumber: String?, val licenseReference: String?, val dateOfBirth: String? = null, val drivingLicenseNumber: String? = null, val aadhaarNumber: String? = null
)
