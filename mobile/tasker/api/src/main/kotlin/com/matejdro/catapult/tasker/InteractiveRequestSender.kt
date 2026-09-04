package com.matejdro.catapult.tasker

fun interface InteractiveRequestSender {
   suspend fun send(sessionId: UInt, request: InteractiveTaskerRequest)

   suspend fun cancel(sessionId: UInt, reason: String) = Unit

   suspend fun sendNotification(title: String, body: String, vibration: Int, durationMs: Long) {
      error("Notifications are not supported by this watch connection")
   }
}
