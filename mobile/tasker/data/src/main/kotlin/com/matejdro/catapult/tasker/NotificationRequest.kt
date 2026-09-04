package com.matejdro.catapult.tasker

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
)
