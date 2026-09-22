package com.daily.nexamartpartner.core.network

/** Extracts safe, backend-provided error messages without exposing raw response bodies. */
object ApiErrorParser {
    fun parse(body: String?): String? {
        if (body.isNullOrBlank()) return null
        val candidates = listOf("message", "error", "detail")
        for (key in candidates) {
            val regex = Regex("\\\"$key\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"])*)\\\"", RegexOption.IGNORE_CASE)
            val match = regex.find(body) ?: continue
            val message = match.groupValues[1]
                .replace("\\\"", "\"")
                .replace("\\\\", "\\")
                .trim()
                .takeIf { it.isNotEmpty() }
                ?.take(200)
                ?: continue
            val errorId = Regex("\\\"errorId\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"", RegexOption.IGNORE_CASE)
                .find(body)
                ?.groupValues
                ?.get(1)
                ?.take(60)
            return if (errorId == null) message else "$message (Error ID: $errorId)"
        }
        return null
    }
}
