package com.daily.nexamartpartner.features.delivery

import com.daily.nexamartpartner.features.delivery.profile.domain.model.DeliveryPartnerProfile
import com.daily.nexamartpartner.features.delivery.profile.domain.model.DeliveryPartnerProfileUpdate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeliveryPartnerProfileModelsTest {
    @Test fun editableFields_areBackendControlled() {
        val p = DeliveryPartnerProfile(
            id = 1,
            name = "Partner",
            phone = "999",
            email = null,
            profileImageUrl = null,
            verificationStatus = "VERIFIED",
            accountStatus = "ACTIVE",
            vehicleType = "Bike",
            vehicleNumber = "TS01",
            licenseReference = "DL01",
            dateOfBirth = null,
            drivingLicenseNumber = null,
            aadhaarNumber = null,
            aadhaarPhotoUrl = null,
            registeredAt = null,
            lastActiveAt = null,
            editableFields = setOf("name")
        )
        assertTrue(p.editableFields.contains("name"))
        assertTrue(!p.editableFields.contains("phone"))
    }

    @Test fun updateModel_doesNotContainSecuritySecrets() {
        val u = DeliveryPartnerProfileUpdate("Partner", "p@example.com", "Bike", "TS01", "DL01")
        assertEquals("Partner", u.name)
        assertEquals("p@example.com", u.email)
    }
}
