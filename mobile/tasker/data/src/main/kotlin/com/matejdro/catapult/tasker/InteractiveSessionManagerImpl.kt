package com.matejdro.catapult.tasker

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import logcat.logcat
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

@Inject
@ContributesBinding(AppScope::class, binding<InteractiveSessionManager>())
@SingleIn(AppScope::class)
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
   override fun registerSender(watchId: String, sender: InteractiveRequestSender) {
      val senderCount = synchronized(senders) {
         senders[watchId] = sender
         senders.size
      }
      logcat { "Watch sender registered: id=$watchId, senderCount=$senderCount" }
   }

   override fun unregisterSender(watchId: String, sender: InteractiveRequestSender) {
      val removed = synchronized(senders) {
         if (senders[watchId] === sender) {
            senders.remove(watchId)
            true
         } else {
            false
         }
      }
      logcat { "Watch sender unregistered: id=$watchId, removed=$removed" }
   }

   override suspend fun awaitResult(request: InteractiveTaskerRequest) =
      awaitResult(request, timeout)

   override suspend fun awaitResult(request: InteractiveTaskerRequest, timeout: Duration): InteractiveTaskerResult {
      val session = mutex.withLock {
         if (activeSession != null) return InteractiveTaskerResult.Failed("Another interactive session is active")
         val entry = synchronized(senders) { senders.entries.firstOrNull() }
            ?: return InteractiveTaskerResult.Failed("Watch connection is unavailable")
         ActiveSession(nextSessionId++, entry.key, entry.value, request, CompletableDeferred()).also { activeSession = it }
      }
      val coroutineContext = currentCoroutineContext()

      try {
         try {
            session.sender.send(session.id, request)
         } catch (e: CancellationException) {
            throw e
         } catch (e: Exception) {
            return InteractiveTaskerResult.Failed(e.message ?: "Failed to send interactive request")
         }
         val result = withTimeoutOrNull(timeout) { session.result.await() }
         if (result != null) {
            return result
         }

         cancelSessionIgnoringFailures(session, INTERACTIVE_SESSION_TIMED_OUT_REASON)
         return InteractiveTaskerResult.TimedOut(INTERACTIVE_SESSION_TIMED_OUT_REASON)
      } finally {
         withContext(NonCancellable) {
            if (!coroutineContext.isActive) {
               cancelSessionIgnoringFailures(session, INTERACTIVE_SESSION_CANCELLED_REASON)
            }
            mutex.withLock { if (activeSession?.id == session.id) activeSession = null }
         }
      }
   }

   override fun cancelActive(reason: String) {
      kotlinx.coroutines.runBlocking { cancelActive("default", reason) }
   }

   override suspend fun cancelActive(watchId: String, reason: String) {
      val session = mutex.withLock {
         activeSession
            ?.takeIf { activeSessionEntry -> activeSessionEntry.watch == watchId }
            ?.also { activeSessionEntry ->
               activeSession = null
               activeSessionEntry.result.complete(InteractiveTaskerResult.Cancelled(reason))
            }
      }
      if (session != null) {
         cancelSessionIgnoringFailures(session, reason)
      }
   }

   override suspend fun acceptResult(watchId: String, sessionId: UInt, result: InteractiveTaskerResult) {
      mutex.withLock {
         activeSession
            ?.takeIf { activeSessionEntry -> activeSessionEntry.watch == watchId }
            ?.takeIf { activeSessionEntry -> activeSessionEntry.id == sessionId }
            ?.takeIf { activeSessionEntry -> activeSessionEntry.accepts(result) }
            ?.let { activeSessionEntry ->
               activeSessionEntry.result.complete(result)
               activeSession = null
            }
      }
   }

   private fun ActiveSession.accepts(result: InteractiveTaskerResult) = when (request) {
      is InteractiveTaskerRequest.List ->
         result is InteractiveTaskerResult.Selection &&
            request.items.any { item ->
               item.id == result.id && item.value == result.value
            }
      is InteractiveTaskerRequest.Confirmation ->
         result is InteractiveTaskerResult.Confirmation
   } || result is InteractiveTaskerResult.Cancelled || result is InteractiveTaskerResult.Failed

   private suspend fun cancelSessionIgnoringFailures(session: ActiveSession, reason: String) {
      try {
         session.sender.cancel(session.id, reason)
      } catch (exception: CancellationException) {
         throw exception
      } catch (exception: Exception) {
         logcat {
            "Failed to cancel interactive session ${session.id}: " +
               (exception.message ?: exception::class.simpleName.orEmpty())
         }
      }
   }
}

private const val INTERACTIVE_SESSION_TIMED_OUT_REASON = "Interactive session timed out"
private const val INTERACTIVE_SESSION_CANCELLED_REASON = "Interactive session cancelled"
