package com.matejdro.catapult.bluetooth

class FakeWatchNotificationSender : WatchNotificationSender {
   data class SentNotification(val title: String, val body: String)

   val sentNotifications = mutableListOf<SentNotification>()
   var result: NotificationSendResult = NotificationSendResult.SUCCESS
   var failure: Throwable? = null

   override fun sendNotification(title: String, body: String): NotificationSendResult {
      failure?.let { throw it }
      sentNotifications += SentNotification(title, body)
      return result
   }
}
