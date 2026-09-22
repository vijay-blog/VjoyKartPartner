package com.daily.nexamartpartner

import com.daily.nexamartpartner.core.network.ApiErrorParser
import com.daily.nexamartpartner.core.network.ErrorMessageResolver
import com.daily.nexamartpartner.core.result.FailureType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ApiReliabilityTest {
    @Test fun resolverUsesSpecificMessagesForCoreHttpFailures() {
        assertEquals("Your session has expired. Please login again.", ErrorMessageResolver.resolve(401))
        assertEquals("This data changed on the server. Refresh and try again.", ErrorMessageResolver.resolve(409))
        assertEquals("The request timed out. Please try again.", ErrorMessageResolver.resolve(408))
        assertEquals("Image is too large. Maximum 5 MB.", ErrorMessageResolver.resolve(413))
        assertEquals("Unsupported image format.", ErrorMessageResolver.resolve(415))
        assertEquals("Too many requests. Please wait a moment and try again.", ErrorMessageResolver.resolve(429))
        assertEquals("Service is temporarily unavailable. Please try again shortly.", ErrorMessageResolver.resolve(503))
    }

    @Test fun parserExtractsSafeMessageFields() {
        assertEquals("Order already delivered", ApiErrorParser.parse("{\"message\":\"Order already delivered\"}"))
        assertEquals("Invalid address", ApiErrorParser.parse("{\"error\":\"Invalid address\"}"))
        assertEquals("Details missing", ApiErrorParser.parse("{\"detail\":\"Details missing\"}"))
        assertEquals(
            "Something went wrong. (Error ID: upload-123)",
            ApiErrorParser.parse("{\"message\":\"Something went wrong.\",\"errorId\":\"upload-123\"}")
        )
    }

    @Test fun parserIgnoresBlankOrUnstructuredBodies() {
        assertNull(ApiErrorParser.parse(null))
        assertNull(ApiErrorParser.parse(""))
        assertNull(ApiErrorParser.parse("server failure"))
    }

    @Test fun failureTypesKeepConflictAndTransientDistinct() {
        assertEquals(FailureType.CONFLICT, FailureType.CONFLICT)
        assertEquals(FailureType.TRANSIENT, FailureType.TRANSIENT)
        assertEquals(FailureType.NOT_FOUND, FailureType.NOT_FOUND)
    }
}
