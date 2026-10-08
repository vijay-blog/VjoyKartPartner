package com.daily.nexamartpartner.features.delivery.availability.data.contract
import com.daily.nexamartpartner.features.delivery.availability.domain.model.DeliveryAvailabilityUpdate
class ActiveDeliveryAvailabilityContract:DeliveryAvailabilityContract{
 override val getPath="delivery/availability";override val updatePath="delivery/availability";override fun buildUpdateBody(u:DeliveryAvailabilityUpdate)=buildMap<String,Any> { put("available",u.available); u.latitude?.let { put("latitude",it) }; u.longitude?.let { put("longitude",it) }; u.recordedAt?.let { put("recordedAt",it) } }
}
