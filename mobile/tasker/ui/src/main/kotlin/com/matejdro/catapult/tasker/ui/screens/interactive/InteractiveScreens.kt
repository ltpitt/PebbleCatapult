package com.matejdro.catapult.tasker.ui.screens.interactive

import android.os.Bundle
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.matejdro.catapult.tasker.BundleKeys
import com.matejdro.catapult.tasker.TaskerPluginConstants
import com.matejdro.catapult.tasker.TaskerAction
import com.matejdro.catapult.tasker.ui.TaskerConfigurationActivity
import kotlinx.serialization.Serializable
import si.inova.kotlinova.core.activity.requireActivity
import si.inova.kotlinova.navigation.screenkeys.ScreenKey
import si.inova.kotlinova.navigation.screens.InjectNavigationScreen
import si.inova.kotlinova.navigation.screens.Screen

private const val DEFAULT_TIMEOUT_MS = 60_000L

@InjectNavigationScreen
class InteractiveListScreen : Screen<InteractiveListScreenKey>() {
   @Composable override fun Content(key: InteractiveListScreenKey) {
      val activity = LocalContext.current.requireActivity() as TaskerConfigurationActivity
      var title by remember { mutableStateOf(activity.existingData.getString(BundleKeys.TITLE).orEmpty()) }
      var items by remember { mutableStateOf(activity.existingData.getString(BundleKeys.ITEMS).orEmpty()) }
      var timeout by remember {
         mutableStateOf(activity.existingData.getLong(BundleKeys.TIMEOUT_MS, DEFAULT_TIMEOUT_MS).toString())
      }
      Column(Modifier.padding(16.dp)) {
         OutlinedTextField(title, { title = it }, label = { Text("Title") })
         OutlinedTextField(items, { items = it }, label = { Text("Items (JSON array of id/value objects)") })
         OutlinedTextField(timeout, { timeout = it }, label = { Text("Timeout (milliseconds)") })
         Button(
            onClick = {
               activity.saveConfiguration(
                  Bundle().apply {
                     putString(BundleKeys.ACTION, TaskerAction.SHOW_LIST.name)
                     putString(BundleKeys.TITLE, title)
                     putString(BundleKeys.ITEMS, items)
                     putLong(BundleKeys.TIMEOUT_MS, timeout.toLongOrNull() ?: DEFAULT_TIMEOUT_MS)
                     putString(
                        TaskerPluginConstants.VARIABLE_REPLACE_KEYS,
                        "%catapult_status %catapult_result_id %catapult_result_value",
                     )
                  },
                  title,
                  finish = true,
               )
            },
         ) {
            Text("Save")
         }
      }
   }
}

@InjectNavigationScreen
class InteractiveConfirmationScreen : Screen<InteractiveConfirmationScreenKey>() {
   @Composable override fun Content(key: InteractiveConfirmationScreenKey) {
      val activity = LocalContext.current.requireActivity() as TaskerConfigurationActivity
      var title by remember { mutableStateOf(activity.existingData.getString(BundleKeys.TITLE).orEmpty()) }
      var message by remember { mutableStateOf(activity.existingData.getString(BundleKeys.MESSAGE).orEmpty()) }
      var timeout by remember {
         mutableStateOf(activity.existingData.getLong(BundleKeys.TIMEOUT_MS, DEFAULT_TIMEOUT_MS).toString())
      }
      Column(Modifier.padding(16.dp)) {
         OutlinedTextField(title, { title = it }, label = { Text("Title") })
         OutlinedTextField(message, { message = it }, label = { Text("Message") })
         OutlinedTextField(timeout, { timeout = it }, label = { Text("Timeout (milliseconds)") })
         Button(
            onClick = {
               activity.saveConfiguration(
                  Bundle().apply {
                     putString(BundleKeys.ACTION, TaskerAction.SHOW_CONFIRMATION.name)
                     putString(BundleKeys.TITLE, title)
                     putString(BundleKeys.MESSAGE, message)
                     putLong(BundleKeys.TIMEOUT_MS, timeout.toLongOrNull() ?: DEFAULT_TIMEOUT_MS)
                     putString(TaskerPluginConstants.VARIABLE_REPLACE_KEYS, "%catapult_status")
                  },
                  title,
                  finish = true,
               )
            },
         ) {
            Text("Save")
         }
      }
   }
}

@Serializable data object InteractiveListScreenKey : ScreenKey()

@Serializable data object InteractiveConfirmationScreenKey : ScreenKey()
