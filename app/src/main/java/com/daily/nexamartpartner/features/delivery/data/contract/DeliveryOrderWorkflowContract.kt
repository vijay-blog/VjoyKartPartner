package com.daily.nexamartpartner.features.delivery.data.contract

import com.daily.nexamartpartner.features.delivery.domain.model.DeliveryOrderAction
import com.daily.nexamartpartner.features.delivery.domain.model.DeliveryOrdersQuery

interface DeliveryOrderWorkflowContract {
    val listAssignedOrdersPath: String?
    val listHistoryOrdersPath: String?
    val orderDetailsPathTemplate: String?
    val actionPathTemplate: String?
    fun buildListQuery(query: DeliveryOrdersQuery): Map<String,String>?
    fun buildActionBody(action: DeliveryOrderAction, latitude: Double? = null, longitude: Double? = null): Map<String,String>?
    fun resolvePath(template: String?, orderId: String): String?
}

class PendingBackendDeliveryOrderWorkflowContract : DeliveryOrderWorkflowContract {
    override val listAssignedOrdersPath: String? = null
    override val listHistoryOrdersPath: String? = null
    override val orderDetailsPathTemplate: String? = null
    override val actionPathTemplate: String? = null
    override fun buildListQuery(query: DeliveryOrdersQuery): Map<String,String>? = null
    override fun buildActionBody(action: DeliveryOrderAction, latitude: Double?, longitude: Double?): Map<String,String>? = null
    override fun resolvePath(template: String?, orderId: String): String? = null
}
