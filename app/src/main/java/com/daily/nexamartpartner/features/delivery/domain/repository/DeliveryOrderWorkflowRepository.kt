package com.daily.nexamartpartner.features.delivery.domain.repository

import com.daily.nexamartpartner.core.result.AppResult
import com.daily.nexamartpartner.features.delivery.domain.model.*

interface DeliveryOrderWorkflowRepository {
 suspend fun getHistoryOrders(q: DeliveryOrdersQuery): AppResult<PagedDeliveryOrders>
 suspend fun getAssignedOrders(query:DeliveryOrdersQuery):AppResult<PagedDeliveryOrders>
 suspend fun getDetails(orderId:String):AppResult<DeliveryOrderDetails>
 suspend fun performAction(orderId:String,action:DeliveryOrderAction,latitude:Double? = null,longitude:Double? = null,locationRecordedAt:String? = null):AppResult<Unit>
}
