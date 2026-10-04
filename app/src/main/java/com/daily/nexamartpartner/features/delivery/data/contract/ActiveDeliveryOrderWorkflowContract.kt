package com.daily.nexamartpartner.features.delivery.data.contract
import com.daily.nexamartpartner.features.delivery.domain.model.*
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

class ActiveDeliveryOrderWorkflowContract : DeliveryOrderWorkflowContract {
    override val listAssignedOrdersPath = "delivery/orders"
    override val listHistoryOrdersPath = "delivery/orders"
    override val orderDetailsPathTemplate = "delivery/orders/{id}"
    override val actionPathTemplate = "delivery/orders/{id}/action"

    override fun buildListQuery(query: DeliveryOrdersQuery): Map<String, String> = buildMap {
        put("page", query.page.toString())
        put("pageSize", query.pageSize.toString())
        query.searchText?.trim()?.takeIf { it.isNotEmpty() }?.let { put("search", it) }
        query.status?.let { put("status", it) }
        normalizeFromDate(query.fromDate)?.let { put("fromDate", it) }
        normalizeToDateExclusive(query.toDate)?.let { put("toDate", it) }
    }

    override fun buildActionBody(action: DeliveryOrderAction, latitude: Double?, longitude: Double?) = buildMap {
        put("action", action.backendValue)
        if (latitude != null && longitude != null) {
            put("latitude", latitude.toString())
            put("longitude", longitude.toString())
        }
    }

    override fun resolvePath(template: String?, orderId: String) = template?.replace("{id}", orderId)

    private fun normalizeFromDate(raw: String?): String? {
        val value = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (value.contains("T")) return value
        val parsed = runCatching { dateParser.parse(value) }.getOrNull() ?: return null
        return instantFormatter.format(parsed)
    }

    private fun normalizeToDateExclusive(raw: String?): String? {
        val value = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (value.contains("T")) return value
        val parsed = runCatching { dateParser.parse(value) }.getOrNull() ?: return null
        val cal = Calendar.getInstance(utc).apply { time = parsed; add(Calendar.DAY_OF_MONTH, 1) }
        return instantFormatter.format(cal.time)
    }

    companion object {
        private val utc = TimeZone.getTimeZone("UTC")
        private val dateParser = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = utc
            isLenient = false
        }
        private val instantFormatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = utc
        }
    }
}
