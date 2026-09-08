package com.matejdro.catapult.bluetooth

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.getSystemService
import com.matejdro.catapult.common.NotificationsKeys
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import java.util.concurrent.atomic.AtomicInteger

@Inject
@ContributesBinding(AppScope::class)
class WatchNotificationSenderImpl(
   private val context: Context,
) : WatchNotificationSender {
   @SuppressLint("MissingPermission") // Guarded by the areNotificationsEnabled() check below.
   override fun sendNotification(title: String, body: String): NotificationSendResult {
      val manager = NotificationManagerCompat.from(context)
      if (!manager.areNotificationsEnabled()) {
         return NotificationSendResult.MISSING_PERMISSION
      }

      ensureChannel()

      val notification = NotificationCompat.Builder(context, NotificationsKeys.CHANNEL_ID_WATCH_NOTIFICATION)
         .setContentTitle(title)
         .setContentText(body)
         .setStyle(NotificationCompat.BigTextStyle().bigText(body))
         .setSmallIcon(android.R.drawable.ic_dialog_info)
         .setPriority(NotificationCompat.PRIORITY_DEFAULT)
         .setAutoCancel(true)
         .build()

      // A unique id per post keeps every notification in the watch's history instead of
      // replacing the previous one.
      manager.notify(nextNotificationId.getAndIncrement(), notification)
      return NotificationSendResult.SUCCESS
   }

   private fun ensureChannel() {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
         val manager = context.getSystemService<NotificationManager>()!!
         manager.createNotificationChannel(
            NotificationChannel(
               NotificationsKeys.CHANNEL_ID_WATCH_NOTIFICATION,
               WATCH_NOTIFICATION_CHANNEL_NAME,
               NotificationManager.IMPORTANCE_DEFAULT,
            ),
         )
      }
   }

   private companion object {
      const val WATCH_NOTIFICATION_CHANNEL_NAME = "Watch notifications"
      val nextNotificationId = AtomicInteger(NotificationsKeys.NOTIFICATION_ID_WATCH_NOTIFICATION_BASE)
   }
}
