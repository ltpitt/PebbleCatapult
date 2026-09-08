package com.matejdro.catapult.bluetooth

import io.rebble.pebblekit2.common.model.TimelineResult

/**
 * Sends a persistent (non-expiring) official Pebble timeline notification pin, using the
 * generic notification layout, containing just a title and a body.
 *
 * Shared between the Tasker `SEND_NOTIFICATION` action and the in-app notification test tool
 * so both exercise the exact same official PebbleKit Android 2 code path.
 */
interface NotificationPinSender {
   suspend fun sendNotification(title: String, body: String): TimelineResult
}
