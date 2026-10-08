package com.daily.nexamartpartner.features.delivery.data.model

data class DeliveryOrderActionRequest(
    val action: String,
    val reason: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val locationRecordedAt: String? = null
)
