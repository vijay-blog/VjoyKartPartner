package com.daily.nexamartpartner.features.delivery.profile.data.contract
import com.daily.nexamartpartner.features.delivery.profile.domain.model.DeliveryPartnerProfileUpdate
class ActiveDeliveryPartnerProfileContract:DeliveryPartnerProfileContract{
 override val profilePath="delivery/profile";override val updatePath="delivery/profile"
 override fun buildUpdateBody(u:DeliveryPartnerProfileUpdate)=buildMap{u.name?.let{put("name",it)};u.email?.let{put("email",it)};u.vehicleType?.let{put("vehicleType",it)};u.vehicleNumber?.let{put("vehicleNumber",it)};u.licenseReference?.let{put("licenseReference",it)};u.dateOfBirth?.let{put("dateOfBirth",it)};u.drivingLicenseNumber?.let{put("drivingLicenseNumber",it)};u.aadhaarNumber?.let{put("aadhaarNumber",it)}}
}
