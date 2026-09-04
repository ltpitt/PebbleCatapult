package com.matejdro.catapult.tasker.ui.screens.interactive

import android.os.Bundle
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.matejdro.catapult.tasker.BundleKeys
import com.matejdro.catapult.tasker.TaskerAction
import com.matejdro.catapult.tasker.ui.TaskerConfigurationActivity
import kotlinx.serialization.Serializable
import si.inova.kotlinova.core.activity.requireActivity
import si.inova.kotlinova.navigation.screenkeys.ScreenKey
import si.inova.kotlinova.navigation.screens.InjectNavigationScreen
import si.inova.kotlinova.navigation.screens.Screen
import androidx.compose.ui.platform.LocalContext

@InjectNavigationScreen
class InteractiveListScreen : Screen<InteractiveListScreenKey>() {
   @Composable override fun Content(key: InteractiveListScreenKey) {
      val activity = LocalContext.current.requireActivity() as TaskerConfigurationActivity
      var title by remember { mutableStateOf(activity.existingData.getString(BundleKeys.TITLE).orEmpty()) }
      var items by remember { mutableStateOf(activity.existingData.getString(BundleKeys.ITEMS).orEmpty()) }
      Column(Modifier.padding(16.dp)) {
         OutlinedTextField(title, { title = it }, label = { Text("Title") })
         OutlinedTextField(items, { items = it }, label = { Text("Items (id=value per line)") })
         Button(onClick = { activity.saveConfiguration(Bundle().apply {
            putString(BundleKeys.ACTION, TaskerAction.SHOW_LIST.name)
            putString(BundleKeys.TITLE, title)
            putString(BundleKeys.ITEMS, items)
         }, title) }) { Text("Save") }
      }
   }
}

@InjectNavigationScreen
class InteractiveConfirmationScreen : Screen<InteractiveConfirmationScreenKey>() {
   @Composable override fun Content(key: InteractiveConfirmationScreenKey) {
      val activity = LocalContext.current.requireActivity() as TaskerConfigurationActivity
      var title by remember { mutableStateOf(activity.existingData.getString(BundleKeys.TITLE).orEmpty()) }
      var message by remember { mutableStateOf(activity.existingData.getString(BundleKeys.MESSAGE).orEmpty()) }
      Column(Modifier.padding(16.dp)) {
         OutlinedTextField(title, { title = it }, label = { Text("Title") })
         OutlinedTextField(message, { message = it }, label = { Text("Message") })
         Button(onClick = { activity.saveConfiguration(Bundle().apply {
            putString(BundleKeys.ACTION, TaskerAction.SHOW_CONFIRMATION.name)
            putString(BundleKeys.TITLE, title)
            putString(BundleKeys.MESSAGE, message)
         }, title) }) { Text("Save") }
      }
   }
}

@Serializable data object InteractiveListScreenKey : ScreenKey()
@Serializable data object InteractiveConfirmationScreenKey : ScreenKey()
