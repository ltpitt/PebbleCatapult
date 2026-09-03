package com.matejdro.catapult.tasker

interface InteractiveSessionManager {
   suspend fun awaitResult(request: InteractiveTaskerRequest): InteractiveTaskerResult

   fun cancelActive(reason: String)

   suspend fun acceptResult(sessionId: UInt, result: InteractiveTaskerResult)
}
