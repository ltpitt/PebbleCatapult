package com.matejdro.catapult.tasker

sealed interface InteractiveTaskerResult {
   data class Selection(
      val id: String,
      val value: String,
   ) : InteractiveTaskerResult

   data class Confirmation(
      val accepted: Boolean,
   ) : InteractiveTaskerResult

   data class Cancelled(
      val reason: String,
   ) : InteractiveTaskerResult

   data class TimedOut(
      val reason: String,
   ) : InteractiveTaskerResult

   data class Failed(
      val reason: String,
   ) : InteractiveTaskerResult
}
