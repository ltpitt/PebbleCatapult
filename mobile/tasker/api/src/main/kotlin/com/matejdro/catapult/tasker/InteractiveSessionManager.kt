package com.matejdro.catapult.tasker

interface InteractiveSessionManager {
   fun registerSender(sender: InteractiveRequestSender)

   suspend fun awaitResult(request: InteractiveTaskerRequest): InteractiveTaskerResult

   fun cancelActive(reason: String)

   suspend fun acceptResult(sessionId: UInt, result: InteractiveTaskerResult)
}
