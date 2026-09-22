package com.daily.nexamartpartner.core.network

object ErrorMessageResolver {
    fun resolve(statusCode: Int?): String = when (statusCode) {
        400 -> "The request was invalid. Please review and try again."
        401 -> "Your session has expired. Please login again."
        403 -> "You don't have permission to perform this action."
        404 -> "The requested information was not found."
        408 -> "The request timed out. Please try again."
        409 -> "This data changed on the server. Refresh and try again."
        413 -> "Image is too large. Maximum 5 MB."
        415 -> "Unsupported image format."
        422 -> "Some details are invalid. Please correct them and continue."
        429 -> "Too many requests. Please wait a moment and try again."
        500 -> "Something went wrong on the server. Please try again."
        502, 503, 504 -> "Service is temporarily unavailable. Please try again shortly."
        else -> "We couldn't complete the request. Please try again."
    }
}
