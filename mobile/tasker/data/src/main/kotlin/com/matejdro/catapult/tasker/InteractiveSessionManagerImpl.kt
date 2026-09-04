package com.matejdro.catapult.tasker

import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.binding
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

@Inject
@ContributesBinding(AppScope::class, binding<InteractiveSessionManager>())
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
   private var sender: InteractiveRequestSender = InteractiveRequestSender { _, _ -> }

   override fun registerSender(sender: InteractiveRequestSender) {
      this.sender = sender
   }

   override suspend fun awaitResult(request: InteractiveTaskerRequest): InteractiveTaskerResult {
      val session = mutex.withLock {
         if (activeSession != null) {
            return InteractiveTaskerResult.Failed("Another interactive session is active")
         }

         ActiveSession(nextSessionId++, request, CompletableDeferred()).also {
            activeSession = it
         }
      }

      try {
         try {
            sender.send(session.id, request)
         } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
         } catch (e: Exception) {
            return InteractiveTaskerResult.Failed(e.message ?: "Failed to send interactive request")
         }
         return withTimeoutOrNull(timeout) {
            session.result.await()
         } ?: InteractiveTaskerResult.TimedOut("Interactive session timed out")
      } finally {
         withContext(NonCancellable) {
            mutex.withLock {
               if (activeSession?.id == session.id) {
                  activeSession = null
               }
            }
         }
      }
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
         is InteractiveTaskerRequest.List ->
            (result as? InteractiveTaskerResult.Selection)?.let { selection ->
               items.any { it.id == selection.id && it.value == selection.value }
            } == true
         is InteractiveTaskerRequest.Confirmation -> result is InteractiveTaskerResult.Confirmation
      } || result is InteractiveTaskerResult.Cancelled || result is InteractiveTaskerResult.Failed
}
