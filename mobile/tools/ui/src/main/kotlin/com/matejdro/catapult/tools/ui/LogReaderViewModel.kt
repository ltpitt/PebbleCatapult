package com.matejdro.catapult.tools.ui

import androidx.compose.runtime.Stable
import com.matejdro.catapult.common.logging.ActionLogger
import com.matejdro.catapult.logging.FileLoggingController
import com.matejdro.catapult.navigation.keys.LogReaderScreenKey
import dev.zacsweers.metro.Inject
import dispatch.core.withDefault
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import si.inova.kotlinova.core.outcome.CoroutineResourceManager
import si.inova.kotlinova.core.outcome.Outcome
import si.inova.kotlinova.navigation.services.ContributesScopedService
import si.inova.kotlinova.navigation.services.SingleScreenViewModel
import java.time.LocalDate

@Stable
@Inject
@ContributesScopedService
class LogReaderViewModel(
   private val resources: CoroutineResourceManager,
   private val actionLogger: ActionLogger,
   private val fileLoggingController: FileLoggingController,
   private val today: () -> LocalDate = LocalDate::now,
) : SingleScreenViewModel<LogReaderScreenKey>(resources.scope) {
   private val _logContent = MutableStateFlow<Outcome<String?>>(Outcome.Success(null))
   val logContent: StateFlow<Outcome<String?>> = _logContent

   fun readTodayLogs() = resources.launchResourceControlTask(_logContent) {
      actionLogger.logAction { "LogReaderViewModel.readTodayLogs()" }

      val content = withDefault {
         fileLoggingController.flush()
         LogReader().read(fileLoggingController.getLogFolder(), today())
      }

      emit(Outcome.Success(content))
   }

   fun resetLogContent() {
      _logContent.value = Outcome.Success(null)
   }
}
