package com.matejdro.catapult.tasker.ui

import com.matejdro.catapult.tasker.ui.screens.interactive.InteractiveListScreenKey
import si.inova.kotlinova.navigation.screenkeys.ScreenKey

class InteractiveListActivity : TaskerConfigurationActivity() {
   override fun getInitialHistory(): List<ScreenKey> = listOf(InteractiveListScreenKey)
}
