package com.matejdro.catapult.tasker.ui

import com.matejdro.catapult.tasker.ui.screens.interactive.InteractiveConfirmationScreenKey
import si.inova.kotlinova.navigation.screenkeys.ScreenKey

class InteractiveConfirmationActivity : TaskerConfigurationActivity() {
   override fun getInitialHistory(): List<ScreenKey> = listOf(InteractiveConfirmationScreenKey)
}
