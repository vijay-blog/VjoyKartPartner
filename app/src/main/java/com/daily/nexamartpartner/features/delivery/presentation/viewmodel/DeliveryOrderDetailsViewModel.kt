package com.daily.nexamartpartner.features.delivery.presentation.viewmodel

import androidx.lifecycle.*
import com.daily.nexamartpartner.core.result.*
import com.daily.nexamartpartner.features.delivery.domain.model.*
import com.daily.nexamartpartner.features.delivery.domain.usecase.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class DeliveryOrderDetailsViewModel(private val id:String,private val get:GetDeliveryOrderDetailsUseCase,private val action:PerformDeliveryOrderActionUseCase):ViewModel(){
 sealed interface Event{data object SessionExpired:Event;data class ActionSuccess(val action:DeliveryOrderAction):Event;data class Message(val text:String):Event}
 data class State(val loading:Boolean=true,val busy:Boolean=false,val refreshing:Boolean=false,val order:DeliveryOrderDetails?=null,val error:String?=null,val unavailable:Boolean=false)
 private val _state=MutableStateFlow(State());val state:StateFlow<State> =_state.asStateFlow();private val _events=MutableSharedFlow<Event>(extraBufferCapacity=2);val events=_events.asSharedFlow()
 init{load()}
 fun retry(){load()}
 fun refresh(){if(_state.value.busy)return;load(refresh=true)}
 private fun load(refresh:Boolean=false){viewModelScope.launch{_state.update{it.copy(loading=!refresh,error=null,unavailable=false,refreshing=refresh)};when(val r=get(id)){is AppResult.Success->_state.update{it.copy(loading=false,refreshing=false,order=r.data)};is AppResult.Failure->{_state.update{it.copy(loading=false,refreshing=false,error=r.error.message,unavailable=r.error.type==FailureType.CONTRACT_MISSING)};if(r.error.type==FailureType.UNAUTHORIZED)_events.tryEmit(Event.SessionExpired)}}}}
 fun perform(a:DeliveryOrderAction, latitude:Double?=null, longitude:Double?=null){if(_state.value.busy)return;viewModelScope.launch{_state.update{it.copy(busy=true)};when(val r=action(id,a,latitude,longitude)){is AppResult.Success->{_state.update{it.copy(busy=false)};_events.emit(Event.ActionSuccess(a));load()};is AppResult.Failure->{_state.update{it.copy(busy=false)};if(r.error.type==FailureType.UNAUTHORIZED)_events.tryEmit(Event.SessionExpired) else _events.emit(Event.Message(r.error.message))}}}}
}
