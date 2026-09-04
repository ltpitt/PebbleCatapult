package com.matejdro.catapult.tasker

fun interface InteractiveRequestSender {
   suspend fun send(sessionId: UInt, request: InteractiveTaskerRequest)
}
