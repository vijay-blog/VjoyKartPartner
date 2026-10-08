package com.daily.nexamartpartner.features.delivery.data.repository

import com.daily.nexamartpartner.core.result.AppResult
import com.daily.nexamartpartner.features.delivery.data.source.DeliveryOrderWorkflowDataSource
import com.daily.nexamartpartner.features.delivery.data.model.*
import com.daily.nexamartpartner.features.delivery.domain.model.*
import com.daily.nexamartpartner.features.delivery.domain.repository.DeliveryOrderWorkflowRepository
import java.math.BigDecimal

class DeliveryOrderWorkflowRepositoryImpl(private val source:DeliveryOrderWorkflowDataSource):DeliveryOrderWorkflowRepository {
 override suspend fun getAssignedOrders(q:DeliveryOrdersQuery):AppResult<PagedDeliveryOrders> = when(val r=source.getAssignedOrders(q)){
  is AppResult.Failure->r
  is AppResult.Success->AppResult.Success(PagedDeliveryOrders(r.data.content.orEmpty().map{it.toDomain()},r.data.number?:q.page,r.data.size?:q.pageSize,r.data.totalPages?:0,r.data.totalElements?:0L,r.data.last?.not()?:false))
 }
 override suspend fun getHistoryOrders(q:DeliveryOrdersQuery):AppResult<PagedDeliveryOrders> = when(val r=source.getHistoryOrders(q)){
  is AppResult.Failure->r
  is AppResult.Success->AppResult.Success(PagedDeliveryOrders(r.data.content.orEmpty().map{it.toDomain()},r.data.number?:q.page,r.data.size?:q.pageSize,r.data.totalPages?:0,r.data.totalElements?:0L,r.data.last?.not()?:false))
 }
 override suspend fun getDetails(id:String):AppResult<DeliveryOrderDetails> = when(val r=source.getDetails(id)){is AppResult.Failure->r;is AppResult.Success->AppResult.Success(r.data.toDomain())}
 override suspend fun performAction(id:String,action:DeliveryOrderAction,latitude:Double?,longitude:Double?,locationRecordedAt:String?):AppResult<Unit> = source.performAction(id,action,latitude,longitude,locationRecordedAt)
 private fun DeliveryOrderSummaryDto.toDomain()=DeliveryOrderSummary(orderId.orEmpty(),customerName.orEmpty(),customerPhone,address,totalAmount.toDecimal(),currencyCode,status.orEmpty(),paymentStatus,assignedAt,createdAt)
 private fun DeliveryOrderDetailsDto.toDomain()=DeliveryOrderDetails(orderId.orEmpty(),customerName.orEmpty(),customerPhone,address,totalAmount.toDecimal(),currencyCode,status.orEmpty(),paymentStatus,items.orEmpty().map{DeliveryOrderItem(it.productName.orEmpty(),it.quantity?:0,it.unitPrice.toDecimal(),it.lineTotal.toDecimal())},assignedAt,createdAt,timeline.orEmpty().map{DeliveryOrderTimeline(it.status.orEmpty(),it.timestamp)},allowedActions.orEmpty().mapNotNull{DeliveryOrderAction.fromBackend(it)},proofOfDeliveryRequired?:false,proofOfDeliveryStatus,proofOfDeliveryUrl)
 private fun String?.toDecimal():BigDecimal?=this?.trim()?.takeIf{it.isNotEmpty()}?.let{runCatching{BigDecimal(it)}.getOrNull()}
}
