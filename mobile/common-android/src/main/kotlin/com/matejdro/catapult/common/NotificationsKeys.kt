package com.matejdro.catapult.common

object NotificationsKeys {
   const val CHANNEL_ID_ERRORS = "ERRORS"
   const val CHANNEL_ID_TASKER_SERVICE = "TASKER_SERVICE"
   const val CHANNEL_ID_WATCH_NOTIFICATION = "WATCH_NOTIFICATION"

   const val NOTIFICATION_ID_ERROR = 1
   const val NOTIFICATION_ID_TASKER_SERVICE = 2

   /** Base id for mirrored watch notifications; each post uses a unique id above this. */
   const val NOTIFICATION_ID_WATCH_NOTIFICATION_BASE = 1000
}
