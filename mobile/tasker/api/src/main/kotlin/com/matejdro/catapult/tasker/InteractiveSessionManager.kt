package com.matejdro.catapult.tasker

interface InteractiveSessionManager {
   fun registerSender(sender: InteractiveRequestSender)

   fun unregisterSender(sender: InteractiveRequestSender) = Unit

   suspend fun awaitResult(request: InteractiveTaskerRequest): InteractiveTaskerResult

   fun cancelActive(reason: String)

   suspend fun acceptResult(sessionId: UInt, result: InteractiveTaskerResult)
}
