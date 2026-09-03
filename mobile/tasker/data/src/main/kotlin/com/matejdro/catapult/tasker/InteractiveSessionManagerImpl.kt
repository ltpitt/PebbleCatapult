package com.matejdro.catapult.tasker

import dev.zacsweers.metro.Inject
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

@Inject
class InteractiveSessionManagerImpl(
   private val timeout: Duration = 1.minutes,
) : InteractiveSessionManager {
   private data class ActiveSession(
      val id: UInt,
      val request: InteractiveTaskerRequest,
      val result: CompletableDeferred<InteractiveTaskerResult>,
   )

   private val mutex = Mutex()
   private var nextSessionId = 1u
   private var activeSession: ActiveSession? = null

   override suspend fun awaitResult(request: InteractiveTaskerRequest): InteractiveTaskerResult {
      val session = mutex.withLock {
         if (activeSession != null) {
            return InteractiveTaskerResult.Failed("Another interactive session is active")
         }

         ActiveSession(nextSessionId++, request, CompletableDeferred()).also {
            activeSession = it
         }
      }

      val result = withTimeoutOrNull(timeout) {
         session.result.await()
      } ?: InteractiveTaskerResult.TimedOut("Interactive session timed out")

      mutex.withLock {
         if (activeSession?.id == session.id) {
            activeSession = null
         }
      }
      return result
   }

   override fun cancelActive(reason: String) {
      runBlocking {
         mutex.withLock {
            activeSession?.let {
               it.result.complete(InteractiveTaskerResult.Cancelled(reason))
               activeSession = null
            }
         }
      }
   }

   override suspend fun acceptResult(sessionId: UInt, result: InteractiveTaskerResult) {
      mutex.withLock {
         activeSession
            ?.takeIf { it.id == sessionId }
            ?.takeIf { it.request.accepts(result) }
            ?.let {
               it.result.complete(result)
               activeSession = null
            }
      }
   }

   private fun InteractiveTaskerRequest.accepts(result: InteractiveTaskerResult): Boolean =
      when (this) {
         is InteractiveTaskerRequest.List -> result is InteractiveTaskerResult.Selection
         is InteractiveTaskerRequest.Confirmation -> result is InteractiveTaskerResult.Confirmation
      }
}
