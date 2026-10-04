package com.daily.nexamartpartner.features.delivery.domain.usecase

import com.daily.nexamartpartner.core.result.AppResult
import com.daily.nexamartpartner.features.delivery.domain.model.*
import com.daily.nexamartpartner.features.delivery.domain.repository.DeliveryOrderWorkflowRepository

class GetAssignedDeliveryOrdersUseCase(private val r:DeliveryOrderWorkflowRepository){suspend operator fun invoke(q:DeliveryOrdersQuery)=r.getAssignedOrders(q)}
class GetDeliveryOrderDetailsUseCase(private val r:DeliveryOrderWorkflowRepository){suspend operator fun invoke(id:String)=r.getDetails(id)}
class PerformDeliveryOrderActionUseCase(private val r:DeliveryOrderWorkflowRepository){suspend operator fun invoke(id:String,a:DeliveryOrderAction,latitude:Double?=null,longitude:Double?=null)=r.performAction(id,a,latitude,longitude)}

class GetDeliveryHistoryUseCase(private val repository: DeliveryOrderWorkflowRepository) {
    suspend operator fun invoke(query: DeliveryOrdersQuery): AppResult<PagedDeliveryOrders> = repository.getHistoryOrders(query)
}
