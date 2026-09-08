package com.matejdro.catapult.bluetooth

/**
 * Posts a normal Android phone notification containing a title and a body.
 *
 * The Pebble companion app mirrors phone notifications to the watch, so this is how Catapult
 * produces an ordinary Pebble notification: one that pops up, can be read and dismissed, and
 * stays in the watch's notification history - exactly like every other mirrored phone
 * notification.
 *
 * Shared between the Tasker `SEND_NOTIFICATION` action and the in-app notification test tool so
 * both produce the exact same notification.
 */
interface WatchNotificationSender {
   fun sendNotification(title: String, body: String): NotificationSendResult
}

enum class NotificationSendResult {
   /** The notification was posted. The Pebble app mirrors it to the watch. */
   SUCCESS,

   /** Notifications are disabled for the app, so nothing could be posted. */
   MISSING_PERMISSION,
}
