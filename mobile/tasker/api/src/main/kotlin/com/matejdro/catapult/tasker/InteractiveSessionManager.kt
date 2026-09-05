package com.matejdro.catapult.tasker

import kotlin.time.Duration.Companion.minutes

interface InteractiveSessionManager {
   fun registerSender(sender: InteractiveRequestSender)
   fun registerSender(watchId: String, sender: InteractiveRequestSender) = registerSender(sender)

   fun unregisterSender(watchId: String, sender: InteractiveRequestSender) = Unit
   fun unregisterSender(sender: InteractiveRequestSender) = unregisterSender("default", sender)

   suspend fun awaitResult(request: InteractiveTaskerRequest): InteractiveTaskerResult
   suspend fun awaitResult(request: InteractiveTaskerRequest, timeout: kotlin.time.Duration) =
      awaitResult(request)

   suspend fun sendNotification(title: String, body: String, vibration: Int, durationMs: Long) {
      error("Notifications are not supported")
   }

   suspend fun sendNotification(
      title: String,
      body: String,
      vibration: Int,
      durationMs: Long,
      startWatchapp: suspend () -> Unit,
   ) {
      sendNotification(title, body, vibration, durationMs)
   }

   fun cancelActive(reason: String)
   suspend fun cancelActive(watchId: String, reason: String) = cancelActive(reason)

   suspend fun acceptResult(watchId: String, sessionId: UInt, result: InteractiveTaskerResult)
}
