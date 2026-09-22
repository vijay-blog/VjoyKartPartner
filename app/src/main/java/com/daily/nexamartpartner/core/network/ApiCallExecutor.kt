package com.daily.nexamartpartner.core.network

import com.daily.nexamartpartner.core.result.AppFailure
import com.daily.nexamartpartner.core.result.AppResult
import com.daily.nexamartpartner.core.result.FailureType
import android.util.Log
import java.io.IOException
import java.net.SocketTimeoutException
import retrofit2.Response

/**
 * Single translation point from Retrofit/transport failures to safe domain failures.
 * It deliberately does not retry writes: callers can retry safe reads explicitly.
 */
class ApiCallExecutor {
    private companion object { const val TAG = "ApiCallExecutor" }
    suspend fun <T> execute(call: suspend () -> Response<T>): AppResult<T> {
        return try {
            val response = call()
            if (response.isSuccessful) {
                if (response.code() == 204) {
                    @Suppress("UNCHECKED_CAST")
                    return AppResult.Success(Unit as T)
                }
                val body = response.body()
                if (body != null) {
                    AppResult.Success(body)
                } else {
                    AppResult.Failure(
                        AppFailure(
                            message = "The server returned an empty response.",
                            code = response.code(),
                            type = FailureType.SERVER
                        )
                    )
                }
            } else {
                val code = response.code()
                val serverMessage = ApiErrorParser.parse(response.errorBody()?.string())
                val type = when (code) {
                    400, 413, 415, 422 -> FailureType.VALIDATION
                    401 -> FailureType.UNAUTHORIZED
                    403 -> FailureType.FORBIDDEN
                    404 -> FailureType.NOT_FOUND
                    408, 429 -> FailureType.TRANSIENT
                    409 -> FailureType.CONFLICT
                    in 500..599 -> FailureType.SERVER
                    else -> FailureType.UNKNOWN
                }
                AppResult.Failure(
                    AppFailure(
                        message = serverMessage ?: ErrorMessageResolver.resolve(code),
                        code = code,
                        type = type
                    )
                )
            }
        } catch (_: SocketTimeoutException) {
            AppResult.Failure(
                AppFailure(
                    message = "The request took too long. Please try again.",
                    type = FailureType.TRANSIENT
                )
            )
        } catch (_: IOException) {
            AppResult.Failure(
                AppFailure(
                    message = "Unable to reach the server. Check your connection and try again.",
                    type = FailureType.NETWORK
                )
            )
        } catch (throwable: Throwable) {
            // Keep technical details out of the UI, but log the exception in debug builds
            // so JSON/Retrofit contract errors can be diagnosed instead of becoming a
            // completely opaque "Something unexpected happened" failure.
            Log.e(TAG, "Unexpected API failure: ${throwable::class.java.simpleName}: ${throwable.message}", throwable)
            AppResult.Failure(
                AppFailure(
                    message = "Something unexpected happened. Please try again.",
                    type = FailureType.UNKNOWN
                )
            )
        }
    }
}
