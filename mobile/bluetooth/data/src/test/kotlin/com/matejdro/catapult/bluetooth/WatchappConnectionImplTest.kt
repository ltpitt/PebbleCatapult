package com.matejdro.catapult.bluetooth

import com.matejdro.bucketsync.BucketSyncWatchLoopImpl
import com.matejdro.bucketsync.FakeBucketSyncRepository
import com.matejdro.bucketsync.background.FakeBackgroundSyncNotifier
import com.matejdro.catapult.actionlist.api.CatapultAction
import com.matejdro.catapult.actionlist.test.FakeCatapultActionRepository
import com.matejdro.catapult.bluetooth.api.WATCHAPP_UUID
import com.matejdro.catapult.tasker.FakeTaskerTaskStarter
import com.matejdro.catapult.tasker.InteractiveSessionManager
import com.matejdro.catapult.tasker.InteractiveTaskerRequest
import com.matejdro.catapult.tasker.InteractiveTaskerResult
import com.matejdro.pebble.bluetooth.common.PacketQueue
import com.matejdro.pebble.bluetooth.common.test.FakePebbleSender
import com.matejdro.pebble.bluetooth.common.test.sentData
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.maps.shouldContainKey
import io.kotest.matchers.shouldBe
import io.rebble.pebblekit2.common.model.PebbleDictionaryItem
import io.rebble.pebblekit2.common.model.ReceiveResult
import io.rebble.pebblekit2.common.model.WatchIdentifier
import kotlinx.coroutines.async
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import io.rebble.pebblekit2.common.model.TransmissionResult
import si.inova.kotlinova.core.test.TestScopeWithDispatcherProvider
import si.inova.kotlinova.core.test.time.virtualTimeProvider

class WatchappConnectionImplTest {
   private val scope = TestScopeWithDispatcherProvider()

   private val sender = FakePebbleSender(scope.virtualTimeProvider())
   private val bucketSyncRepository = FakeBucketSyncRepository()
   private val actionRepository = FakeCatapultActionRepository()
   private val taskerTaskStarter = FakeTaskerTaskStarter()
   private val interactiveSessionManager = RecordingInteractiveSessionManager()

   private val watchappOpenController = FakeWatchappOpenController()

   private val watch = WatchIdentifier("watch")

   private val packetQueue = PacketQueue(sender, watch, WATCHAPP_UUID)

   private val bucketSyncWatchLoop = BucketSyncWatchLoopImpl(
      scope.backgroundScope,
      packetQueue,
      bucketSyncRepository,
      watchappOpenController,
      FakeBackgroundSyncNotifier(),
      watch,
   )
   private val connection = WatchappConnectionImpl(
      scope.backgroundScope,
      actionRepository,
      taskerTaskStarter,
      watchappOpenController,
      packetQueue,
      bucketSyncWatchLoop,
      interactiveSessionManager,
      watch,
   )

   @Test
   fun `Nack unknown packets`() = scope.runTest {
      val result = connection.onPacketReceived(
         mapOf(
            0u to PebbleDictionaryItem.UInt32(255u),
         )
      )
      runCurrent()

      result shouldBe ReceiveResult.Nack
   }

   private class RecordingInteractiveSessionManager : InteractiveSessionManager {
      val results = mutableListOf<Pair<UInt, InteractiveTaskerResult>>()
      var activeSessionId: UInt? = 42u
      var completedActiveSession = false

      override suspend fun awaitResult(request: InteractiveTaskerRequest): InteractiveTaskerResult =
         error("Not used")

      override fun registerSender(sender: com.matejdro.catapult.tasker.InteractiveRequestSender) = Unit

      override fun cancelActive(reason: String) = Unit

      override suspend fun acceptResult(watchId: String, sessionId: UInt, result: InteractiveTaskerResult) {
         results += sessionId to result
         if (sessionId == activeSessionId) completedActiveSession = true
      }
   }

   @Test
   fun `Send only version back when watch packets do not match`() = scope.runTest {
      val result = connection.onPacketReceived(
         mapOf(
            0u to PebbleDictionaryItem.UInt32(0u),
            1u to PebbleDictionaryItem.UInt32(PROTOCOL_VERSION + 1u),
            2u to PebbleDictionaryItem.UInt32(1u),
            3u to PebbleDictionaryItem.UInt32(1000u),
         )
      )
      runCurrent()

      result shouldBe ReceiveResult.Ack

      sender.sentData.shouldContainExactly(
         mapOf(
            0u to PebbleDictionaryItem.UInt8(1u),
            1u to PebbleDictionaryItem.UInt16(PROTOCOL_VERSION),
         )
      )
   }

   @Test
   fun `Previous protocol version cannot receive notifications`() = scope.runTest {
      connection.onPacketReceived(
         mapOf(
            0u to PebbleDictionaryItem.UInt32(0u),
            1u to PebbleDictionaryItem.UInt32(PROTOCOL_VERSION - 1u),
         )
      )
      runCurrent()

      var failed = false
      try {
         connection.sendNotification(
            WatchNotificationMessage.Show(
               "Title", "Body", WatchNotificationMessage.Vibration.NONE, 0,
            )
         )
      } catch (_: IllegalArgumentException) {
         failed = true
      }
      failed shouldBe true
      sender.sentData shouldContainExactly listOf(
         mapOf(
            0u to PebbleDictionaryItem.UInt8(1u),
            1u to PebbleDictionaryItem.UInt16(PROTOCOL_VERSION),
         )
      )
   }

   @Test
   fun `Current protocol version sends a notification packet`() = scope.runTest {
      receiveStandardHelloPacket()
      runCurrent()

      connection.sendNotification(
         WatchNotificationMessage.Show(
            "Title", "Body", WatchNotificationMessage.Vibration.DOUBLE, 5_000,
         )
      )
      sender.sentData.last() shouldBe mapOf(
         0u to PebbleDictionaryItem.UInt32(11u),
         2u to PebbleDictionaryItem.Text("Title"),
         7u to PebbleDictionaryItem.Text("Body"),
         6u to PebbleDictionaryItem.UInt8(2u),
         8u to PebbleDictionaryItem.UInt32(5_000u),
      )
   }

   @Test
   fun `Reject invalid notification vibration before packet dispatch`() = scope.runTest {
      val exception = assertThrows<IllegalArgumentException> {
         connection.sendNotification("Title", "Body", 3, 5_000)
      }

      exception.message shouldBe "Invalid vibration value"
      sender.sentData.shouldBeEmpty()
   }

   @Test
   fun `Unsupported inbound notification packet is rejected`() = scope.runTest {
      val result = connection.onPacketReceived(
         mapOf(
            0u to PebbleDictionaryItem.UInt32(11u),
            2u to PebbleDictionaryItem.Text("Title"),
            6u to PebbleDictionaryItem.UInt8(1u),
            7u to PebbleDictionaryItem.Text("Body"),
            8u to PebbleDictionaryItem.UInt32(5_000u),
         )
      )

      result shouldBe ReceiveResult.Nack
   }

   @Test
   fun `Send a list of updated buckets`() = scope.runTest {
      bucketSyncRepository.updateBucket(1u, byteArrayOf(1))
      bucketSyncRepository.updateBucket(2u, byteArrayOf(2))

      val result = receiveStandardHelloPacket(bufferSize = 37u)
      runCurrent()

      result shouldBe ReceiveResult.Ack

      sender.sentData.shouldContainExactly(
         mapOf(
            0u to PebbleDictionaryItem.UInt8(1u),
            1u to PebbleDictionaryItem.UInt16(PROTOCOL_VERSION),
            2u to PebbleDictionaryItem.Bytes(
               byteArrayOf(
                  0, // Status
                  0, 2, // Latest version
                  2, // Num of active buckets
                  1, 0, // Metadata for bucket 1
                  2, 0, // Metadata for bucket 2
                  1, 1, 1, // Sync data for bucket 1
               )
            ),
         ),
         mapOf(
            0u to PebbleDictionaryItem.UInt8(3u),
            1u to PebbleDictionaryItem.Bytes(
               byteArrayOf(
                  1, // Status
                  2, 1, 2, // Sync data for bucket 2
               )
            ),
         )
      )
   }

   @Test
   fun `Send bucketsync data after Acking first packet`() = scope.runTest {
      sender.pauseSending = true

      val result = async { receiveStandardHelloPacket() }
      runCurrent()

      result.getCompleted() shouldBe ReceiveResult.Ack
   }

   @Test
   fun `Trigger tasker task when the watch requests it`() = scope.runTest {
      taskerTaskStarter.reportStartSuccessful = true
      actionRepository.insert(CatapultAction("Action A", 1, 1, "Tasker A"))

      val result = connection.onPacketReceived(
         mapOf(
            0u to PebbleDictionaryItem.UInt32(4u),
            1u to PebbleDictionaryItem.UInt32(1u),
            2u to PebbleDictionaryItem.Text("Action A"),
         )
      )
      runCurrent()

      result shouldBe ReceiveResult.Ack
      taskerTaskStarter.startedTasks.shouldContainExactly("Tasker A" to null)
   }

   @Test
   fun `Return nack when attempting to start missing task`() = scope.runTest {
      taskerTaskStarter.reportStartSuccessful = true
      actionRepository.insert(CatapultAction("Action A", 1, 1, "Tasker A"))

      val result = connection.onPacketReceived(
         mapOf(
            0u to PebbleDictionaryItem.UInt32(4u),
            1u to PebbleDictionaryItem.UInt32(2u),
            2u to PebbleDictionaryItem.Text("Action A"),
         )
      )
      runCurrent()

      result shouldBe ReceiveResult.Nack
      taskerTaskStarter.startedTasks.shouldBeEmpty()
   }

   @Test
   fun `Return nack when the starting the task fails`() = scope.runTest {
      taskerTaskStarter.reportStartSuccessful = false
      actionRepository.insert(CatapultAction("Action A", 1, 1, "Tasker A"))

      val result = connection.onPacketReceived(
         mapOf(
            0u to PebbleDictionaryItem.UInt32(4u),
            1u to PebbleDictionaryItem.UInt32(1u),
            2u to PebbleDictionaryItem.Text("Action A"),
         )
      )
      runCurrent()

      result shouldBe ReceiveResult.Nack
   }

   @Test
   fun `Return nack when the target action has no tasker task`() = scope.runTest {
      taskerTaskStarter.reportStartSuccessful = true
      actionRepository.insert(CatapultAction("Action A", 1, 1))

      val result = connection.onPacketReceived(
         mapOf(
            0u to PebbleDictionaryItem.UInt32(4u),
            1u to PebbleDictionaryItem.UInt32(1u),
            2u to PebbleDictionaryItem.Text("Action A"),
         )
      )
      runCurrent()

      result shouldBe ReceiveResult.Nack
   }

   @Test
   fun `Return Nack when local action name does not match remote action name`() = scope.runTest {
      taskerTaskStarter.reportStartSuccessful = true
      actionRepository.insert(CatapultAction("Action A", 1, 1, "Tasker A"))

      val result = connection.onPacketReceived(
         mapOf(
            0u to PebbleDictionaryItem.UInt32(4u),
            1u to PebbleDictionaryItem.UInt32(1u),
            2u to PebbleDictionaryItem.Text("Action B"),
         )
      )
      runCurrent()

      result shouldBe ReceiveResult.Nack
      taskerTaskStarter.startedTasks.shouldBeEmpty()
   }

   @Test
   fun `Send auto-close flag when watchapp was started by auto sync`() = scope.runTest {
      watchappOpenController.setNextWatchappOpenForAutoSync()

      bucketSyncRepository.updateBucket(1u, byteArrayOf(1))
      bucketSyncRepository.updateBucket(2u, byteArrayOf(2))

      receiveStandardHelloPacket(bufferSize = 61u)
      runCurrent()

      sender.sentData.first().shouldContainKey(3u)
   }

   @Test
   fun `Send auto-close flag when watchapp was started by auto sync and there are no updates`() = scope.runTest {
      watchappOpenController.setNextWatchappOpenForAutoSync()

      receiveStandardHelloPacket(bufferSize = 61u)
      runCurrent()

      sender.sentData.first().shouldContainKey(3u)
   }

   @Test
   fun `Pass currently active packets in init packet`() = scope.runTest {
      bucketSyncRepository.updateBucket(1u, byteArrayOf(1))
      bucketSyncRepository.updateBucket(2u, byteArrayOf(2))

      receiveStandardHelloPacket(bufferSize = 38u, currentlyActiveBuckets = byteArrayOf(4, 5))
      runCurrent()

      bucketSyncWatchLoop.lastActiveBuckets shouldBe listOf(4u.toUByte(), 5u.toUByte())
   }

   @Test
   fun `Pass task parameter`() = scope.runTest {
      taskerTaskStarter.reportStartSuccessful = true
      actionRepository.insert(CatapultAction("Action A", 1, 1, "Tasker A"))

      val result = connection.onPacketReceived(
         mapOf(
            0u to PebbleDictionaryItem.UInt32(4u),
            1u to PebbleDictionaryItem.UInt32(1u),
            2u to PebbleDictionaryItem.Text("Action A"),
            3u to PebbleDictionaryItem.Text("Param"),
         )
      )
      runCurrent()

      result shouldBe ReceiveResult.Ack
      taskerTaskStarter.startedTasks.shouldContainExactly("Tasker A" to "Param")
   }

   @Test
   fun `Send interactive list as ordered chunks`() = scope.runTest {
      receiveStandardHelloPacket(bufferSize = 256u)
      runCurrent()

      connection.sendInteractiveRequest(
         InteractiveWatchMessage.ShowList(
            42u,
            "Places",
            listOf(InteractiveWatchMessage.Item("home", "Home"), InteractiveWatchMessage.Item("work", "Work")),
         )
      )
      runCurrent()

      sender.sentData.takeLast(2).map { (it[3u] as PebbleDictionaryItem.UInt32).value } shouldBe listOf(0u, 1u)
   }

   @Test
   fun `Route matching interactive selection to session manager`() = scope.runTest {
      val selection = InteractiveWatchMessage.ListSelection(42u, "home", "Home")

      connection.onPacketReceived(selection.toPacket(256))

      interactiveSessionManager.results shouldContainExactly listOf(
         42u to InteractiveTaskerResult.Selection("home", "Home"),
      )
   }

   @Test
   fun `Ignore stale interactive response without completing a session`() = scope.runTest {
      connection.onPacketReceived(InteractiveWatchMessage.ListSelection(41u, "home", "Home").toPacket(256))

      interactiveSessionManager.results shouldContainExactly listOf(
         41u to InteractiveTaskerResult.Selection("home", "Home"),
      )
      interactiveSessionManager.completedActiveSession shouldBe false
   }

   @Test
   fun `Report explicit failure when interactive connection is unavailable`() = scope.runTest {
      connection.sendInteractiveRequest(InteractiveWatchMessage.Cancel(42u, "done"))

      interactiveSessionManager.results shouldContainExactly listOf(
         42u to InteractiveTaskerResult.Failed("Watch connection is unavailable"),
      )
   }

   @Test
   fun `Timeout notification retries when watch is disconnected`() = scope.runTest {
      receiveStandardHelloPacket()
      runCurrent()
      sender.sendingResult = TransmissionResult.FailedWatchNotConnected

      val send = async {
         connection.sendNotification(
            WatchNotificationMessage.Show("Title", "Body", WatchNotificationMessage.Vibration.NONE, 0)
         )
      }
      runCurrent()
      advanceTimeBy(5_000)

      assertThrows<TimeoutCancellationException> { send.await() }
   }

   private suspend fun receiveStandardHelloPacket(
      version: UInt = 0u,
      bufferSize: UInt = 1000u,
      currentlyActiveBuckets: ByteArray = byteArrayOf(),
   ): ReceiveResult =
      connection.onPacketReceived(
         mapOf(
            0u to PebbleDictionaryItem.UInt32(0u),
            1u to PebbleDictionaryItem.UInt32(PROTOCOL_VERSION.toUInt()),
            2u to PebbleDictionaryItem.UInt32(version),
            3u to PebbleDictionaryItem.UInt32(bufferSize),
            7u to PebbleDictionaryItem.Bytes(currentlyActiveBuckets),
         )
      )
}
