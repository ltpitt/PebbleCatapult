package com.matejdro.catapult.tasker.ui

import com.matejdro.catapult.tasker.ui.screens.notification.NotificationScreenKey
import si.inova.kotlinova.navigation.screenkeys.ScreenKey

class NotificationActivity : TaskerConfigurationActivity() {
   override fun getInitialHistory(): List<ScreenKey> = listOf(NotificationScreenKey)
}
