package com.matejdro.catapult.tasker

import android.os.Bundle
import com.matejdro.catapult.actionlist.api.CatapultActionRepository
import com.matejdro.catapult.bluetooth.WatchappOpenController
import com.matejdro.catapult.bluetooth.api.WATCHAPP_UUID
import dev.zacsweers.metro.Inject
import dispatch.core.withDefault
import io.rebble.pebblekit2.client.PebbleInfoRetriever
import io.rebble.pebblekit2.client.PebbleSender
import io.rebble.pebblekit2.common.model.TimelineLayout
import io.rebble.pebblekit2.common.model.TimelineLayoutType
import io.rebble.pebblekit2.common.model.TimelinePin
import io.rebble.pebblekit2.common.model.TimelineResult
import io.rebble.pebblekit2.model.Watchapp
import kotlinx.coroutines.flow.first
import logcat.logcat
import si.inova.kotlinova.core.exceptions.UnknownCauseException
import si.inova.kotlinova.core.time.TimeProvider
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeParseException
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.toKotlinInstant

@Inject
class TaskerActionRunner(
   private val actionRepository: CatapultActionRepository,
   private val sender: PebbleSender,
   private val pebbleInfoRetriever: PebbleInfoRetriever,
   private val openController: WatchappOpenController,
   private val timeProvider: TimeProvider,
   private val interactiveSessionManager: InteractiveSessionManager,
) {
   suspend fun run(bundle: Bundle): InteractiveTaskerResult? {
      val actionName = bundle.getString(BundleKeys.ACTION) ?: error("Missing action from bundle")
      val action = enumValueOf<TaskerAction>(actionName)

      return when (action) {
         TaskerAction.TOGGLE_ACTIONS -> runToggleAction(bundle).let { null }

         TaskerAction.SYNC_NOW -> runSyncAction(bundle).let { null }
         TaskerAction.CREATE_PIN -> runCreatePin(bundle).let { null }
         TaskerAction.DELETE_PIN -> runDeletePin(bundle).let { null }
         TaskerAction.SHOW_LIST -> runInteractiveList(bundle)
         TaskerAction.SHOW_CONFIRMATION -> runInteractiveConfirmation(bundle)
         TaskerAction.SEND_NOTIFICATION -> runNotification(bundle).let { null }
      }
   }

   private suspend fun runInteractiveList(bundle: Bundle): InteractiveTaskerResult {
      val title = bundle.getString(BundleKeys.TITLE)?.takeIf { it.isNotBlank() }
         ?: throw TaskerInvalidInputException("Title is mandatory")
      val items = InteractiveTaskerItems.decode(bundle.getString(BundleKeys.ITEMS).orEmpty())
      val result = interactiveSessionManager.awaitResult(InteractiveTaskerRequest.List(title, items), timeout(bundle))
      return result
   }

   private suspend fun runInteractiveConfirmation(bundle: Bundle): InteractiveTaskerResult {
      val title = bundle.getString(BundleKeys.TITLE)?.takeIf { it.isNotBlank() }
         ?: throw TaskerInvalidInputException("Title is mandatory")
      val message = bundle.getString(BundleKeys.MESSAGE).orEmpty()
      val result = interactiveSessionManager.awaitResult(InteractiveTaskerRequest.Confirmation(title, message), timeout(bundle))
      return result
   }

   private fun timeout(bundle: Bundle) =
      bundle.getLong(BundleKeys.TIMEOUT_MS, 60_000L).coerceAtLeast(1L).milliseconds

   private suspend fun runNotification(bundle: Bundle) {
      val request = NotificationRequest.fromBundle(bundle)
      if (request.title.isBlank()) throw TaskerInvalidInputException("Title is mandatory")
      if (request.title.toByteArray(Charsets.UTF_8).size > 64) throw TaskerInvalidInputException("Title is too long")
      if (request.body.toByteArray(Charsets.UTF_8).size > 128) throw TaskerInvalidInputException("Body is too long")
      if (request.durationMs !in 0..300_000) throw TaskerInvalidInputException("Duration is out of range")
      try {
         interactiveSessionManager.sendNotification(
            request.title,
            request.body,
            request.vibration.ordinal,
            request.durationMs,
         )
      } catch (e: TaskerInvalidInputException) {
         throw e
      } catch (e: Exception) {
         throw TaskerInvalidInputException(e.message ?: "Failed to send notification")
      }
   }

   private suspend fun runToggleAction(bundle: Bundle) {
      val directoryId = bundle.getInt(BundleKeys.DIRECTORY_ID, 1)
      val actionsToEnable = bundle.getString(BundleKeys.ENABLED_TASK_IDS)
         ?.split(",")
         ?.mapNotNull { it.toIntOrNull() }
         .orEmpty()
      val actionsToDisable = bundle.getString(BundleKeys.DISABLED_TASK_IDS)
         ?.split(",")
         ?.mapNotNull { it.toIntOrNull() }
         .orEmpty()

      actionRepository.massToggle(directory = directoryId, enable = actionsToEnable, disable = actionsToDisable)
   }

   private suspend fun runSyncAction(bundle: Bundle) {
      val onlyOnWatchface = bundle.getBoolean(BundleKeys.ONLY_ON_WATCHFACE, false)
      logcat { "Syncnow, onlyOnWatchface: $onlyOnWatchface" }
      if (onlyOnWatchface) {
         withDefault {
            val connectedWatches = pebbleInfoRetriever.getConnectedWatches().first()
            logcat { "Connected watches: ${connectedWatches.map { it.id to it.name }}" }

            val watchesOnWatchface = connectedWatches.filter { watch ->
               val runningApp = pebbleInfoRetriever.getActiveApp(watch.id).first()
               logcat { "Running app on ${watch.id}: ${runningApp ?: "null"}" }

               runningApp?.type == Watchapp.Type.WATCHFACE
            }

            openController.setNextWatchappOpenForAutoSync()
            sender.startAppOnTheWatch(WATCHAPP_UUID, watchesOnWatchface.map { it.id })
         }
      } else {
         openController.setNextWatchappOpenForAutoSync()
         sender.startAppOnTheWatch(WATCHAPP_UUID)
      }
   }

   @Suppress("ThrowsCount") // Input validation
   private suspend fun runCreatePin(bundle: Bundle) {
      val id = bundle.getString(BundleKeys.ID)
      if (id.isNullOrBlank()) {
         throw TaskerInvalidInputException("ID is mandatory")
      }

      val title = bundle.getString(BundleKeys.TITLE)?.takeIf { it.isNotBlank() }
      if (title.isNullOrBlank()) {
         throw TaskerInvalidInputException("Title is mandatory")
      }

      val body = bundle.getString(BundleKeys.TEXT)

      val startDateText = bundle.getString(BundleKeys.START_DATE).orEmpty()
      val startDate = try {
         LocalDate.parse(startDateText)
      } catch (ignored: DateTimeParseException) {
         throw TaskerInvalidInputException("Invalid date format: '$startDateText'")
      }

      val startTimeText = bundle.getString(BundleKeys.START_TIME).orEmpty()
      val startTime = try {
         LocalTime.parse(startTimeText)
      } catch (ignored: DateTimeParseException) {
         throw TaskerInvalidInputException("Invalid time format: '$startTimeText'")
      }

      val duration = bundle.getString(BundleKeys.DURATION)?.takeIf { it.isNotBlank() }?.toIntOrNull()

      val icon = bundle.getString(BundleKeys.ICON)

      val startInstant = startDate.atTime(startTime).atZone(timeProvider.systemDefaultZoneId()).toInstant().toKotlinInstant()

      val result = sender.insertTimelinePin(
         WATCHAPP_UUID,
         TimelinePin(
            id,
            startInstant,
            duration?.minutes,
            TimelineLayout(
               if (duration != null) TimelineLayoutType.CALENDAR_PIN else TimelineLayoutType.GENERIC_PIN,
               title,
               body = body,
               tinyIcon = icon?.let { "system://images/$it" }
            )
         )
      )

      when (result) {
         TimelineResult.FailedNoPebbleApp -> {
            throw TaskerInvalidInputException("Pebble app is not installed")
         }

         TimelineResult.FailedNoPermissions -> {
            throw TaskerInvalidInputException("Catapult watchapp is not installed")
         }

         TimelineResult.FailedUnknownPin -> {
            error("Received unknown pin on insertion. This should never happen")
         }

         TimelineResult.FailedUnsupportedAction -> {
            throw TaskerInvalidInputException("Installed Pebble app is too old for the Timeline feature")
         }

         is TimelineResult.Unknown -> {
            throw UnknownCauseException("Unknown timeline error '${result.message.orEmpty()}'")
         }

         TimelineResult.Success -> {
            // Success! Nothing to do
         }
      }
   }

   @Suppress("ThrowsCount") // Input validation
   private suspend fun runDeletePin(bundle: Bundle) {
      val id = bundle.getString(BundleKeys.ID)
      if (id.isNullOrBlank()) {
         throw TaskerInvalidInputException("ID is mandatory")
      }

      val result = sender.deleteTimelinePin(
         WATCHAPP_UUID,
         id,
      )

      when (result) {
         TimelineResult.FailedNoPebbleApp -> {
            throw TaskerInvalidInputException("Pebble app is not installed")
         }

         TimelineResult.FailedNoPermissions -> {
            throw TaskerInvalidInputException("Catapult watchapp is not installed")
         }

         TimelineResult.FailedUnsupportedAction -> {
            throw TaskerInvalidInputException("Installed Pebble app is too old for the Timeline feature")
         }

         is TimelineResult.Unknown -> {
            throw UnknownCauseException("Unknown timeline error '${result.message.orEmpty()}'")
         }

         TimelineResult.FailedUnknownPin -> {
            // Pin did not exist in the first place, so, deletion was a success
         }

         TimelineResult.Success -> {
            // Success! Nothing to do
         }
      }
   }
}
