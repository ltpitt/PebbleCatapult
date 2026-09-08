package com.matejdro.catapult.tools.ui

import androidx.compose.runtime.Stable
import com.matejdro.catapult.bluetooth.NotificationPinSender
import com.matejdro.catapult.common.logging.ActionLogger
import com.matejdro.catapult.navigation.keys.NotificationTestScreenKey
import dev.zacsweers.metro.Inject
import io.rebble.pebblekit2.common.model.TimelineResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import si.inova.kotlinova.core.outcome.CoroutineResourceManager
import si.inova.kotlinova.core.outcome.Outcome
import si.inova.kotlinova.navigation.services.ContributesScopedService
import si.inova.kotlinova.navigation.services.SingleScreenViewModel

@Stable
@Inject
@ContributesScopedService
class NotificationTestViewModel(
   private val resources: CoroutineResourceManager,
   private val actionLogger: ActionLogger,
   private val notificationPinSender: NotificationPinSender,
) : SingleScreenViewModel<NotificationTestScreenKey>(resources.scope) {
   private val _title = MutableStateFlow(DEFAULT_TITLE)
   val title: StateFlow<String>
      get() = _title

   private val _body = MutableStateFlow(DEFAULT_BODY)
   val body: StateFlow<String>
      get() = _body

   private val _sendResult = MutableStateFlow<Outcome<TimelineResult?>>(Outcome.Success(null))
   val sendResult: StateFlow<Outcome<TimelineResult?>>
      get() = _sendResult

   fun setTitle(newTitle: String) {
      _title.value = newTitle
   }

   fun setBody(newBody: String) {
      _body.value = newBody
   }

   fun send() = resources.launchResourceControlTask(_sendResult) {
      val title = _title.value
      val body = _body.value

      actionLogger.logAction { "NotificationTestViewModel.send(title='$title', bodyLength=${body.length})" }

      emit(Outcome.Progress())
      val result = notificationPinSender.sendNotification(title, body)
      emit(Outcome.Success(result))
   }

   fun resetSendResult() {
      _sendResult.value = Outcome.Success(null)
   }
}

private const val DEFAULT_TITLE = "Test notification"
private const val DEFAULT_BODY = "Sent from the Catapult notification test tool"
