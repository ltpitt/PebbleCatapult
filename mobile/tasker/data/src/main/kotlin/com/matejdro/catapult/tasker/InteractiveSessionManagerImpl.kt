package com.matejdro.catapult.tasker

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
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
      val watch: String,
      val sender: InteractiveRequestSender,
      val request: InteractiveTaskerRequest,
      val result: CompletableDeferred<InteractiveTaskerResult>,
   )

   private val mutex = Mutex()
   private var nextSessionId = 1u
   private var activeSession: ActiveSession? = null
   private val senders = mutableMapOf<String, InteractiveRequestSender>()

   override fun registerSender(sender: InteractiveRequestSender) {
      registerSender("default", sender)
   }
   override fun registerSender(watch: String, sender: InteractiveRequestSender) {
      synchronized(senders) { senders[watch] = sender }
   }

   override fun unregisterSender(watch: String, sender: InteractiveRequestSender) {
      synchronized(senders) { if (senders[watch] === sender) senders.remove(watch) }
   }

   override suspend fun awaitResult(request: InteractiveTaskerRequest) =
      awaitResult(request, timeout)

   override suspend fun awaitResult(request: InteractiveTaskerRequest, timeout: Duration): InteractiveTaskerResult {
      val session = mutex.withLock {
         if (activeSession != null) return InteractiveTaskerResult.Failed("Another interactive session is active")
         val entry = synchronized(senders) { senders.entries.firstOrNull() }
            ?: object : Map.Entry<String, InteractiveRequestSender> {
               override val key = "default"
               override val value = InteractiveRequestSender { _, _ -> }
            }
         ActiveSession(nextSessionId++, entry.key, entry.value, request, CompletableDeferred()).also { activeSession = it }
      }
      try {
         try {
            session.sender.send(session.id, request)
         } catch (e: CancellationException) {
            throw e
         } catch (e: Exception) {
            return InteractiveTaskerResult.Failed(e.message ?: "Failed to send interactive request")
         }
         return withTimeoutOrNull(timeout) { session.result.await() }
            ?: InteractiveTaskerResult.TimedOut("Interactive session timed out").also {
               runCatching { session.sender.cancel(session.id, "Interactive session timed out") }
            }
      } finally {
         withContext(NonCancellable) {
            mutex.withLock { if (activeSession?.id == session.id) activeSession = null }
         }
      }
   }

   override fun cancelActive(reason: String) {
      kotlinx.coroutines.runBlocking { cancelActive("default", reason) }
   }

   override suspend fun cancelActive(watch: String, reason: String) {
      val session = mutex.withLock {
         activeSession?.takeIf { it.watch == watch }?.also {
            activeSession = null
            it.result.complete(InteractiveTaskerResult.Cancelled(reason))
         }
      }
      if (session != null) runCatching { session.sender.cancel(session.id, reason) }
   }

   override suspend fun acceptResult(sessionId: UInt, result: InteractiveTaskerResult) {
      mutex.withLock {
         activeSession?.takeIf { it.id == sessionId }?.takeIf { it.accepts(result) }?.let {
            it.result.complete(result)
            activeSession = null
         }
      }
   }

   private fun ActiveSession.accepts(result: InteractiveTaskerResult) = when (request) {
      is InteractiveTaskerRequest.List -> result is InteractiveTaskerResult.Selection &&
         request.items.any { it.id == result.id && it.value == result.value }
      is InteractiveTaskerRequest.Confirmation -> result is InteractiveTaskerResult.Confirmation
   } || result is InteractiveTaskerResult.Cancelled || result is InteractiveTaskerResult.Failed
}
