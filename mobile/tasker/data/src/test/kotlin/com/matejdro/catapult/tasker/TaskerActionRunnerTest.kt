package com.matejdro.catapult.tasker

import android.os.Bundle
import com.matejdro.catapult.actionlist.api.CatapultAction
import com.matejdro.catapult.actionlist.test.FakeCatapultActionRepository
import com.matejdro.catapult.bluetooth.FakePebbleInfoRetriever
import com.matejdro.catapult.bluetooth.FakeWatchappOpenController
import com.matejdro.catapult.bluetooth.api.WATCHAPP_UUID
import com.matejdro.pebble.bluetooth.common.test.FakePebbleSender
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.throwable.shouldHaveMessage
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.rebble.pebblekit2.common.model.TimelineLayout
import io.rebble.pebblekit2.common.model.TimelineLayoutType
import io.rebble.pebblekit2.common.model.TimelinePin
import io.rebble.pebblekit2.common.model.WatchIdentifier
import io.rebble.pebblekit2.model.Watchapp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import si.inova.kotlinova.core.test.TestScopeWithDispatcherProvider
import si.inova.kotlinova.core.test.outcomes.shouldBeSuccessWithData
import si.inova.kotlinova.core.test.time.virtualTimeProvider
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID
import kotlin.time.Duration.Companion.minutes
import kotlin.time.toKotlinInstant

class TaskerActionRunnerTest {
   private val scope = TestScopeWithDispatcherProvider()
   private val repo = FakeCatapultActionRepository()
   private val pebbleSender = FakePebbleSender(scope.virtualTimeProvider())
   private val pebbleInfoRetriever = FakePebbleInfoRetriever()
   private val openController = FakeWatchappOpenController()
   private val interactiveManager = RecordingInteractiveSessionManager()
   private val runner = TaskerActionRunner(
      repo,
      pebbleSender,
      pebbleInfoRetriever,
      openController,
      scope.virtualTimeProvider(),
      interactiveManager,
   )

   private class RecordingInteractiveSessionManager : InteractiveSessionManager {
      val requests = mutableListOf<InteractiveTaskerRequest>()
      val notifications = mutableListOf<NotificationRequest>()
      var notificationFailure: Throwable? = null
      override fun registerSender(sender: InteractiveRequestSender) = Unit
      override suspend fun awaitResult(request: InteractiveTaskerRequest): InteractiveTaskerResult {
         requests += request
         return when (request) {
            is InteractiveTaskerRequest.List -> InteractiveTaskerResult.Selection(
               request.items.first().id, request.items.first().value,
            )
            is InteractiveTaskerRequest.Confirmation -> InteractiveTaskerResult.Confirmation(true)
         }
      }
      override fun cancelActive(reason: String) = Unit
      override suspend fun sendNotification(title: String, body: String, vibration: Int, durationMs: Long) {
         notificationFailure?.let { throw it }
         notifications += NotificationRequest(title, body, VibrationStyle.entries[vibration], durationMs)
      }
      override suspend fun acceptResult(watchId: String, sessionId: UInt, result: InteractiveTaskerResult) = Unit
   }

   @Test
   fun `Runner recognizes send notification action before deferred dispatch`() = scope.runTest {
      val bundle = Bundle().apply {
         putString(BundleKeys.ACTION, TaskerAction.SEND_NOTIFICATION.name)
         putString(BundleKeys.TITLE, "Door")
         putString(BundleKeys.MESSAGE, "Front door opened")
         putString(BundleKeys.NOTIFICATION_VIBRATION, "short")
         putLong(BundleKeys.NOTIFICATION_DURATION_MS, 5_000)
      }

      runner.run(bundle) shouldBe InteractiveTaskerResult.Success
      interactiveManager.notifications.single() shouldBe
         NotificationRequest("Door", "Front door opened", VibrationStyle.SHORT, 5_000)
      NotificationRequest.fromBundle(bundle) shouldBe
         NotificationRequest("Door", "Front door opened", VibrationStyle.SHORT, 5_000)
   }

   @Test
   fun `Notification duration defaults to 5000 ms and preserves explicit zero`() = scope.runTest {
      NotificationRequest.fromBundle(Bundle()).durationMs shouldBe 5_000

      NotificationRequest.fromBundle(
         Bundle().apply {
            putLong(BundleKeys.NOTIFICATION_DURATION_MS, 0)
         },
      ).durationMs shouldBe 0
   }

   @Test
   fun `Preserve notification transport failures`() = scope.runTest {
      val failure = IllegalStateException("Disconnected")
      interactiveManager.notificationFailure = failure

      shouldThrow<IllegalStateException> {
         runner.run(
            Bundle().apply {
               putString(BundleKeys.ACTION, TaskerAction.SEND_NOTIFICATION.name)
               putString(BundleKeys.TITLE, "Door")
            },
         )
      }.shouldBeSameInstanceAs(failure)
   }

   @Test
   fun `Rethrow notification cancellation`() = scope.runTest {
      val cancellation = CancellationException("Cancelled")
      interactiveManager.notificationFailure = cancellation

      shouldThrow<CancellationException> {
         runner.run(
            Bundle().apply {
               putString(BundleKeys.ACTION, TaskerAction.SEND_NOTIFICATION.name)
               putString(BundleKeys.TITLE, "Door")
            },
         )
      }.shouldBeSameInstanceAs(cancellation)
   }

   @Test
   fun `Reject unsupported notification vibration values`() = scope.runTest {
      val bundle = Bundle().apply {
         putString(BundleKeys.TITLE, "Door")
         putString(BundleKeys.MESSAGE, "Front door opened")
         putString(BundleKeys.NOTIFICATION_VIBRATION, "unsupported")
      }

      shouldThrow<TaskerInvalidInputException> {
         NotificationRequest.fromBundle(bundle)
      }
   }

   @Test
   fun `Allow explicit no vibration`() = scope.runTest {
      val bundle = Bundle().apply {
         putString(BundleKeys.NOTIFICATION_VIBRATION, "none")
      }

      NotificationRequest.fromBundle(bundle).vibration shouldBe VibrationStyle.NONE
   }

   @Test
   fun `Run interactive confirmation through session manager`() = scope.runTest {
      runner.run(
         Bundle().apply {
            putString(BundleKeys.ACTION, TaskerAction.SHOW_CONFIRMATION.name)
            putString(BundleKeys.TITLE, "Confirm")
            putString(BundleKeys.MESSAGE, "Proceed?")
         },
      )

      interactiveManager.requests.single() shouldBe
         InteractiveTaskerRequest.Confirmation("Confirm", "Proceed?")
   }

   @Test
   fun `Run interactive list from JSON`() = scope.runTest {
      runner.run(
         Bundle().apply {
            putString(BundleKeys.ACTION, TaskerAction.SHOW_LIST.name)
            putString(BundleKeys.TITLE, "Choose")
            putString(
               BundleKeys.ITEMS,
               InteractiveTaskerItems.encode(
                  listOf(InteractiveTaskerRequest.Item("a=b", "Line 1\nLine 2")),
               ),
            )
         },
      )

      interactiveManager.requests.single() shouldBe InteractiveTaskerRequest.List(
         "Choose",
         listOf(InteractiveTaskerRequest.Item("a=b", "Line 1\nLine 2")),
      )
   }

   @Test
   fun `Reject malformed interactive list`() = scope.runTest {
      shouldThrow<TaskerInvalidInputException> {
         runner.run(
            Bundle().apply {
               putString(BundleKeys.ACTION, TaskerAction.SHOW_LIST.name)
               putString(BundleKeys.TITLE, "Choose")
               putString(BundleKeys.ITEMS, """[{"id":"","value":"value"}]""")
            },
         )
      }
   }

   @Test
   fun `Run toggle action`() = scope.runTest {
      repo.insert(
         CatapultAction("Action A", 10, 1, enabled = false),
         CatapultAction("Action B", 10, 2, enabled = false),
         CatapultAction("Action C", 10, 3, enabled = true),
         CatapultAction("Action D", 10, 4, enabled = true),
      )

      runner.run(
         Bundle().apply {
            putString(BundleKeys.ACTION, "TOGGLE_ACTIONS")
            putInt(BundleKeys.DIRECTORY_ID, 10)
            putString(BundleKeys.ENABLED_TASK_IDS, "1,2")
            putString(BundleKeys.DISABLED_TASK_IDS, "3,4")
         }
      )
      runCurrent()

      repo.getAll(10).first() shouldBeSuccessWithData listOf(
         CatapultAction("Action A", 10, 1, enabled = true),
         CatapultAction("Action B", 10, 2, enabled = true),
         CatapultAction("Action C", 10, 3, enabled = false),
         CatapultAction("Action D", 10, 4, enabled = false),
      )
   }

   @Test
   fun `Start app on all watches when running normal sync now`() = scope.runTest {
      runner.run(
         Bundle().apply {
            putString(BundleKeys.ACTION, "SYNC_NOW")
         }
      )
      runCurrent()

      openController.isNextWatchappOpenForAutoSync() shouldBe true

      pebbleSender.startedApps.shouldContainExactly(
         FakePebbleSender.AppLifecycleEvent(WATCHAPP_UUID, null)
      )
   }

   @Test
   fun `Start app only on watches that are on the watchfaces with the only watchface flag`() = scope.runTest {
      pebbleInfoRetriever.setConnectedWatchIds(
         listOf(
            WatchIdentifier("1"),
            WatchIdentifier("2"),
            WatchIdentifier("3"),
         )
      )

      pebbleInfoRetriever.setActiveApp(
         WatchIdentifier("1"),
         Watchapp(UUID(1, 1), "App", Watchapp.Type.WATCHAPP)
      )

      pebbleInfoRetriever.setActiveApp(
         WatchIdentifier("2"),
         null
      )

      pebbleInfoRetriever.setActiveApp(
         WatchIdentifier("3"),
         Watchapp(UUID(1, 2), "Watchface", Watchapp.Type.WATCHFACE)

      )

      runner.run(
         Bundle().apply {
            putString(BundleKeys.ACTION, "SYNC_NOW")
            putBoolean(BundleKeys.ONLY_ON_WATCHFACE, true)
         }
      )
      runCurrent()

      openController.isNextWatchappOpenForAutoSync() shouldBe true

      pebbleSender.startedApps.shouldContainExactly(
         FakePebbleSender.AppLifecycleEvent(
            WATCHAPP_UUID,
            listOf(WatchIdentifier("3"))
         ),
      )
   }

   @Test
   fun `Insert pin with full data`() = scope.runTest {
      runner.run(
         Bundle().apply {
            putString(BundleKeys.ACTION, "CREATE_PIN")
            putString(BundleKeys.ID, "10")
            putString(BundleKeys.TITLE, "Title")
            putString(BundleKeys.TEXT, "Text")
            putString(BundleKeys.START_DATE, "2026-02-10")
            putString(BundleKeys.START_TIME, "10:00")
            putString(BundleKeys.DURATION, "4")
            putString(BundleKeys.ICON, "TIMELINE_WEATHER")
         }
      )
      runCurrent()

      val targetInstant = LocalDateTime.of(2026, 2, 10, 10, 0)
         .atZone(ZoneId.of("UTC"))
         .toInstant()
         .toKotlinInstant()

      pebbleSender.insertedPins.shouldContainExactly(
         TimelinePin(
            "10",
            targetInstant,
            4.minutes,
            TimelineLayout(
               TimelineLayoutType.CALENDAR_PIN,
               "Title",
               body = "Text",
               tinyIcon = "system://images/TIMELINE_WEATHER",
            )
         )
      )
   }

   @Test
   fun `Insert pin with minimal data`() = scope.runTest {
      runner.run(
         Bundle().apply {
            putString(BundleKeys.ACTION, "CREATE_PIN")
            putString(BundleKeys.ID, "10")
            putString(BundleKeys.TITLE, "Title")
            putString(BundleKeys.START_DATE, "2026-02-10")
            putString(BundleKeys.START_TIME, "10:00")
         }
      )
      runCurrent()

      val targetInstant = LocalDateTime.of(2026, 2, 10, 10, 0)
         .atZone(ZoneId.of("UTC"))
         .toInstant()
         .toKotlinInstant()

      pebbleSender.insertedPins.shouldContainExactly(
         TimelinePin(
            "10",
            targetInstant,
            layout = TimelineLayout(
               TimelineLayoutType.GENERIC_PIN,
               "Title",
            )
         )
      )
   }

   @Test
   fun `Throw exception on invalid formatting`() = scope.runTest {
      shouldThrow<TaskerInvalidInputException> {
         runner.run(
            Bundle().apply {
               putString(BundleKeys.ACTION, "CREATE_PIN")
               putString(BundleKeys.ID, "10")
               putString(BundleKeys.TITLE, "Title")
               putString(BundleKeys.START_DATE, "2026-02")
               putString(BundleKeys.START_TIME, "10:00")
            }
         )
         runCurrent()
      }.shouldHaveMessage("Invalid date format: '2026-02'")
   }

   @Test
   fun `Delete pin`() = scope.runTest {
      runner.run(
         Bundle().apply {
            putString(BundleKeys.ACTION, "DELETE_PIN")
            putString(BundleKeys.ID, "10")
         }
      )
      runCurrent()

      pebbleSender.deletedPins.shouldContainExactly("10")
   }
}
