package com.daily.nexamartpartner.features.delivery.availability.domain.model

data class DeliveryAvailability(
    val available: Boolean?,
    val status: String?,
    val canChange: Boolean,
    val reason: String? = null,
    val updatedAt: String? = null
)

data class DeliveryAvailabilityUpdate(val available: Boolean, val latitude: Double? = null, val longitude: Double? = null)
