package com.matejdro.catapult.tasker

import android.os.Bundle

enum class VibrationStyle {
   NONE,
   SHORT,
   DOUBLE,
}

data class NotificationRequest(
   val title: String,
   val body: String,
   val vibration: VibrationStyle,
   val durationMs: Long,
) {
   companion object {
      fun fromBundle(bundle: Bundle): NotificationRequest {
         val vibration = when (bundle.getString(BundleKeys.NOTIFICATION_VIBRATION)?.lowercase()) {
            "short" -> VibrationStyle.SHORT
            "double" -> VibrationStyle.DOUBLE
            else -> VibrationStyle.NONE
         }
         return NotificationRequest(
            title = bundle.getString(BundleKeys.TITLE).orEmpty(),
            body = bundle.getString(BundleKeys.MESSAGE).orEmpty(),
            vibration = vibration,
            durationMs = bundle.getLong(BundleKeys.NOTIFICATION_DURATION_MS, 5_000L),
         )
      }
   }
}
