package com.daily.nexamartpartner.features.delivery.data.source

import com.daily.nexamartpartner.core.network.ApiCallExecutor
import com.daily.nexamartpartner.core.result.AppFailure
import com.daily.nexamartpartner.core.result.AppResult
import com.daily.nexamartpartner.core.result.FailureType
import com.daily.nexamartpartner.features.delivery.data.contract.DeliveryOrderWorkflowContract
import com.daily.nexamartpartner.features.delivery.data.model.DeliveryOrderDetailsDto
import com.daily.nexamartpartner.features.delivery.data.model.DeliveryOrdersPageDto
import com.daily.nexamartpartner.features.delivery.domain.model.DeliveryOrderAction
import com.daily.nexamartpartner.features.delivery.domain.model.DeliveryOrdersQuery

interface DeliveryOrderWorkflowDataSource {
    suspend fun getAssignedOrders(q: DeliveryOrdersQuery): AppResult<DeliveryOrdersPageDto>
    suspend fun getHistoryOrders(q: DeliveryOrdersQuery): AppResult<DeliveryOrdersPageDto>
    suspend fun getDetails(id: String): AppResult<DeliveryOrderDetailsDto>
    suspend fun performAction(id: String, a: DeliveryOrderAction, latitude: Double? = null, longitude: Double? = null, locationRecordedAt: String? = null): AppResult<Unit>
}

class DeliveryOrderWorkflowDataSourceImpl(
    private val api: DeliveryOrderWorkflowApi,
    private val contract: DeliveryOrderWorkflowContract,
    private val executor: ApiCallExecutor
) : DeliveryOrderWorkflowDataSource {
    override suspend fun getAssignedOrders(q: DeliveryOrdersQuery): AppResult<DeliveryOrdersPageDto> {
        val path = contract.listAssignedOrdersPath
            ?: return missing("Assigned-orders API contract is not confirmed yet.")
        val params = contract.buildListQuery(q)
            ?: return missing("Assigned-orders query contract is not confirmed yet.")
        return executor.execute { api.getAssignedOrders(path, params) }
    }

    override suspend fun getHistoryOrders(q: DeliveryOrdersQuery): AppResult<DeliveryOrdersPageDto> {
        val path = contract.listHistoryOrdersPath
            ?: return missing("Delivery-history API contract is not confirmed yet.")
        val params = contract.buildListQuery(q)
            ?: return missing("Delivery-history query contract is not confirmed yet.")
        return executor.execute { api.getAssignedOrders(path, params) }
    }

    override suspend fun getDetails(id: String): AppResult<DeliveryOrderDetailsDto> {
        val path = contract.resolvePath(contract.orderDetailsPathTemplate, id)
            ?: return missing("Delivery order details API contract is not confirmed yet.")
        return executor.execute { api.getOrderDetails(path) }
    }

    override suspend fun performAction(id: String, a: DeliveryOrderAction, latitude: Double?, longitude: Double?, locationRecordedAt: String?): AppResult<Unit> {
        val path = contract.resolvePath(contract.actionPathTemplate, id)
            ?: return missing("Delivery order action API contract is not confirmed yet.")
        val body = contract.buildActionBody(a, latitude, longitude, locationRecordedAt)
            ?: return missing("Delivery order action request contract is not confirmed yet.")
        return when (val result = executor.execute { api.performAction(path, body) }) {
            is AppResult.Success -> AppResult.Success(Unit)
            is AppResult.Failure -> result
        }
    }

    private fun <T> missing(message: String): AppResult<T> =
        AppResult.Failure(AppFailure(message, type = FailureType.CONTRACT_MISSING))
}
