package com.matejdro.catapult.bluetooth

import com.matejdro.catapult.bluetooth.api.WATCHAPP_UUID
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import io.rebble.pebblekit2.client.PebbleSender
import io.rebble.pebblekit2.common.model.TimelineLayout
import io.rebble.pebblekit2.common.model.TimelineLayoutType
import io.rebble.pebblekit2.common.model.TimelinePin
import io.rebble.pebblekit2.common.model.TimelineResult
import si.inova.kotlinova.core.time.TimeProvider
import java.util.UUID
import kotlin.time.toKotlinInstant

@Inject
@ContributesBinding(AppScope::class)
class NotificationPinSenderImpl(
   private val sender: PebbleSender,
   private val timeProvider: TimeProvider,
) : NotificationPinSender {
   override suspend fun sendNotification(title: String, body: String): TimelineResult {
      return sender.insertTimelinePin(
         WATCHAPP_UUID,
         TimelinePin(
            id = "catapult-notification-${UUID.randomUUID()}",
            startTime = timeProvider.currentInstant().toKotlinInstant(),
            // Always non-expiring: official notification pins must remain visible in the
            // timeline as a persistent trace.
            duration = null,
            layout = TimelineLayout(
               type = TimelineLayoutType.GENERIC_NOTIFICATION,
               title = title,
               body = body,
            ),
         ),
      )
   }
}
