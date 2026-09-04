package com.matejdro.catapult.bluetooth

import com.matejdro.bucketsync.BucketSyncWatchLoop
import com.matejdro.catapult.actionlist.api.CatapultActionRepository
import com.matejdro.catapult.common.flow.firstData
import com.matejdro.catapult.tasker.TaskerTaskStarter
import com.matejdro.catapult.tasker.InteractiveSessionManager
import com.matejdro.catapult.tasker.InteractiveTaskerRequest
import com.matejdro.catapult.tasker.InteractiveRequestSender
import com.matejdro.catapult.tasker.InteractiveTaskerResult
import com.matejdro.pebble.bluetooth.common.PacketQueue
import com.matejdro.pebble.bluetooth.common.WatchAppConnection
import com.matejdro.pebble.bluetooth.common.di.WatchappConnectionGraph
import com.matejdro.pebble.bluetooth.common.di.WatchappConnectionScope
import com.matejdro.pebble.bluetooth.common.util.requireString
import com.matejdro.pebble.bluetooth.common.util.requireUint
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import io.rebble.pebblekit2.common.model.PebbleDictionary
import io.rebble.pebblekit2.common.model.PebbleDictionaryItem
import io.rebble.pebblekit2.common.model.PebbleDictionaryItem.UInt16
import io.rebble.pebblekit2.common.model.PebbleDictionaryItem.UInt8
import io.rebble.pebblekit2.common.model.ReceiveResult
import io.rebble.pebblekit2.common.model.WatchIdentifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import logcat.logcat

@Inject
@ContributesBinding(WatchappConnectionScope::class, binding<WatchAppConnection>())
@Suppress("MagicNumber") // Packet processing involves a lot of numbers, it would be less readable to make consts
class WatchappConnectionImpl(
   coroutineScope: CoroutineScope,
   private val actionRepository: CatapultActionRepository,
   private val taskerTaskStarter: TaskerTaskStarter,
   private val watchappOpenController: WatchappOpenController,
   private val packetQueue: PacketQueue,
   private val bucketSyncWatchLoop: BucketSyncWatchLoop,
   private val interactiveSessionManager: InteractiveSessionManager,
   private val watch: WatchIdentifier,
) : WatchAppConnection, InteractiveRequestSender {
   private var watchBufferSize: Int = 0

   init {
      interactiveSessionManager.registerSender(watch.toString(), this)
      coroutineScope.launch {
         try {
            packetQueue.runQueue()
         } finally {
            interactiveSessionManager.cancelActive(watch.toString(), "Watch connection closed")
            interactiveSessionManager.unregisterSender(watch.toString(), this@WatchappConnectionImpl)
         }
      }
   }

   override suspend fun sendInteractivePackets(packets: List<PebbleDictionary>) {
      try {
        withTimeout(INTERACTIVE_SEND_TIMEOUT) {
           packets.forEach { packetQueue.sendPacket(it) }
        }
      } catch (e: TimeoutCancellationException) {
        logcat { "Interactive request could not be sent before the connection timed out" }
        throw e
      }
   }

   override suspend fun send(sessionId: UInt, request: InteractiveTaskerRequest) {
      val message = when (request) {
        is InteractiveTaskerRequest.List -> InteractiveWatchMessage.ShowList(
           sessionId,
           request.title,
           request.items.map { InteractiveWatchMessage.Item(it.id, it.value) },
        )
        is InteractiveTaskerRequest.Confirmation -> InteractiveWatchMessage.ShowConfirmation(
           sessionId,
           request.title,
           request.message,
        )
      }

      sendInteractiveRequest(message)
   }

   override suspend fun cancel(sessionId: UInt, reason: String) {
      sendInteractiveRequest(InteractiveWatchMessage.Cancel(sessionId, reason))
   }

   override suspend fun onPacketReceived(data: PebbleDictionary): ReceiveResult {
      val id = (data.get(0u) as PebbleDictionaryItem.UInt32?)?.value
      logcat { "Received packet ${id ?: "null"}" }

      return when (id) {
         0u -> {
            processWatchWelcomePacket(data)
         }

         4u -> {
            processStartTaskPacket(data)
         }

         in InteractiveWatchMessage.PACKET_SHOW_LIST..InteractiveWatchMessage.PACKET_CANCEL_OR_ERROR -> {
            processInteractiveResponse(data)
         }

         else -> {
            logcat { "Unknown packet ID. Nacking..." }
            ReceiveResult.Nack
         }

      }
   }

   suspend fun sendInteractiveRequest(message: InteractiveWatchMessage) {
      val limit = watchBufferSize
      if (limit <= 0) {
         failInteractive(message.sessionId, "Watch connection is unavailable")
         return
      }

      try {
         sendInteractivePackets(message.packets(limit))
      } catch (e: TimeoutCancellationException) {
         failInteractive(message.sessionId, "Interactive request could not be sent")
      } catch (e: CancellationException) {
         throw e
      } catch (e: Exception) {
         failInteractive(message.sessionId, e.message ?: "Failed to send interactive request")
      }
   }

   suspend fun sendInteractiveMessage(message: InteractiveWatchMessage) = sendInteractiveRequest(message)

   private suspend fun processInteractiveResponse(data: PebbleDictionary): ReceiveResult {
      val message = try {
         InteractiveWatchMessage.decode(data)
      } catch (e: IllegalArgumentException) {
         logcat { "Malformed interactive packet: ${e.message}" }
         return ReceiveResult.Nack
      }

      val result = when (message) {
         is InteractiveWatchMessage.ListSelection ->
            InteractiveTaskerResult.Selection(message.selectedItemId, message.selectedItemValue)
         is InteractiveWatchMessage.ConfirmationResult ->
            InteractiveTaskerResult.Confirmation(message.accepted)
         is InteractiveWatchMessage.Cancel ->
            InteractiveTaskerResult.Cancelled(message.reason)
         is InteractiveWatchMessage.CancelOrError ->
            message.error?.let(InteractiveTaskerResult::Failed)
               ?: InteractiveTaskerResult.Cancelled("Watch cancelled interactive session")
         is InteractiveWatchMessage.ShowList,
         is InteractiveWatchMessage.ShowConfirmation,
         is InteractiveWatchMessage.ListChunk,
         -> {
            logcat { "Unexpected interactive request from watch" }
            return ReceiveResult.Nack
         }
      }

      interactiveSessionManager.acceptResult(watch.toString(), message.sessionId, result)
      return ReceiveResult.Ack
   }

   private suspend fun failInteractive(sessionId: UInt, reason: String) {
      interactiveSessionManager.acceptResult(watch.toString(), sessionId, InteractiveTaskerResult.Failed(reason))
   }

   private suspend fun processWatchWelcomePacket(data: PebbleDictionary): ReceiveResult {
      val watchProtocolVersion = data.requireUint(1u)
      if (watchProtocolVersion != PROTOCOL_VERSION.toUInt()) {
         logcat { "Mismatch protocol version $watchProtocolVersion" }
         packetQueue.sendPacket(
            mapOf(
               0u to PebbleDictionaryItem.UInt8(1u),
               1u to PebbleDictionaryItem.UInt16(PROTOCOL_VERSION)
            )
         )
         return ReceiveResult.Ack
      }

      val activeBuckets = data[7u]
         ?.let { it as? PebbleDictionaryItem.Bytes }
         ?.value
         ?.map { it.toUByte() }
         .orEmpty()

      val watchVersion = data.requireUint(2u).toUShort()
      watchBufferSize = data.requireUint(3u).toInt()
      logcat { "Watch data: version=$watchVersion, buffer size=$watchBufferSize" }

      bucketSyncWatchLoop.sendFirstPacketAndStartLoop(
         mapOfNotNull(
            0u to UInt8(1u),
            1u to UInt16(PROTOCOL_VERSION),
            (3u to UInt8(1u)).takeIf { watchappOpenController.isNextWatchappOpenForAutoSync() },
         ),
         watchVersion,
         watchBufferSize,
         activeBuckets,
      )

      return ReceiveResult.Ack
   }

   private suspend fun processStartTaskPacket(data: PebbleDictionary): ReceiveResult {
      val actionId = data.requireUint(1u)
      val remoteActionTitle = data.requireString(2u)
      val parameter = (data[3u] as PebbleDictionaryItem.Text?)?.value

      val action = actionRepository.getById(actionId.toInt()).firstData()
      if (action == null) {
         logcat { "Unknown action. Nacking..." }
         return ReceiveResult.Nack
      }

      if (action.title != remoteActionTitle) {
         logcat { "Mismatch action. local='${action.title}' vs remote='$remoteActionTitle'. Nacking..." }
         return ReceiveResult.Nack
      }

      val taskerTask = action.taskerTaskName
      if (taskerTask == null) {
         logcat { "Target action has no task. Nacking..." }
         return ReceiveResult.Nack
      }

      logcat { "Starting task $taskerTask, parameter '${parameter ?: "NULL"}'" }

      val success = taskerTaskStarter.startTask(taskerTask, parameter)

      return if (success) {
         logcat { "Task successfully started" }
         ReceiveResult.Ack
      } else {
         logcat { "Tasker task launch failed" }
         ReceiveResult.Nack
      }
   }

   @Inject
   @ContributesBinding(AppScope::class)
   class Factory(
      private val subgraphFactory: WatchappConnectionGraph.Factory,
   ) : WatchAppConnection.Factory {
      override fun create(watch: WatchIdentifier, scope: CoroutineScope): WatchAppConnection {
         return subgraphFactory.create(scope, watch).createWatchappConnection()
      }
   }
}

private fun <K, V> mapOfNotNull(vararg pairs: Pair<K, V>?): Map<K, V> =
   pairs.filterNotNull().toMap()

private const val INTERACTIVE_SEND_TIMEOUT = 5_000L
