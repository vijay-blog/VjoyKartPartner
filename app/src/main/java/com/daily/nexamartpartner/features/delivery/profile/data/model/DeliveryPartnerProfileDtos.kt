package com.daily.nexamartpartner.features.delivery.profile.data.model

data class DeliveryPartnerProfileDto(
    val id: Long? = null, val name: String? = null, val phone: String? = null, val email: String? = null,
    val profileImageUrl: String? = null, val verificationStatus: String? = null, val accountStatus: String? = null,
    val vehicleType: String? = null, val vehicleNumber: String? = null, val licenseReference: String? = null,
    val dateOfBirth: String? = null, val drivingLicenseNumber: String? = null, val aadhaarNumber: String? = null, val aadhaarPhotoUrl: String? = null,
    val registeredAt: String? = null, val lastActiveAt: String? = null, val editableFields: List<String>? = null
)
