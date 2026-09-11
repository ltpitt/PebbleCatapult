package com.matejdro.catapult.tasker

/**
 * Names of the local variables Catapult returns to Tasker after an interactive action.
 *
 * These are the single source of truth for both the runtime that fills the result bundle
 * and the configuration screens that declare the variables back to Tasker.
 */
object TaskerResultKeys {
   const val STATUS = "%catapult_status"
   const val RESULT_ID = "%catapult_result_id"
   const val RESULT_VALUE = "%catapult_result_value"
}
