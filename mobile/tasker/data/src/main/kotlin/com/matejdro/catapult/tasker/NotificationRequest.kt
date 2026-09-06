package com.matejdro.catapult.tasker

import android.os.Bundle
import logcat.logcat

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
         val vibrationValue = bundle.getString(BundleKeys.NOTIFICATION_VIBRATION)
         val vibration = when (vibrationValue?.lowercase()) {
            null, "none" -> VibrationStyle.NONE
            "short" -> VibrationStyle.SHORT
            "double" -> VibrationStyle.DOUBLE
            else -> throw TaskerInvalidInputException("Unknown vibration style: '$vibrationValue'")
         }
         val request = NotificationRequest(
            title = bundle.getString(BundleKeys.TITLE).orEmpty(),
            body = bundle.getString(BundleKeys.MESSAGE).orEmpty(),
            vibration = vibration,
            durationMs = bundle.getLong(BundleKeys.NOTIFICATION_DURATION_MS, DEFAULT_NOTIFICATION_DURATION_MS),
         )
         logcat {
            "Parsed Tasker notification bundle: durationMs=${request.durationMs}"
         }
         return request
      }
   }
}

private const val DEFAULT_NOTIFICATION_DURATION_MS = 10_000L
