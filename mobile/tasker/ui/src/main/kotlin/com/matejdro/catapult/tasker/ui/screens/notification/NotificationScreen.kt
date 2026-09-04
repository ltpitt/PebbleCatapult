package com.matejdro.catapult.tasker.ui.screens.notification

import android.os.Bundle
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.matejdro.catapult.tasker.BundleKeys
import com.matejdro.catapult.tasker.TaskerAction
import com.matejdro.catapult.tasker.TaskerPluginConstants
import com.matejdro.catapult.tasker.ui.TaskerConfigurationActivity
import kotlinx.serialization.Serializable
import si.inova.kotlinova.core.activity.requireActivity
import si.inova.kotlinova.navigation.screenkeys.ScreenKey
import si.inova.kotlinova.navigation.screens.InjectNavigationScreen
import si.inova.kotlinova.navigation.screens.Screen

private const val DEFAULT_DURATION_MS = 5_000L
private const val MAX_DURATION_MS = 300_000L
private val vibrationOptions = listOf("None", "Short", "Double")

@InjectNavigationScreen
class NotificationScreen : Screen<NotificationScreenKey>() {
   @Composable
   override fun Content(key: NotificationScreenKey) {
      val activity = LocalContext.current.requireActivity() as TaskerConfigurationActivity
      var title by remember { mutableStateOf(activity.existingData.getString(BundleKeys.TITLE).orEmpty()) }
      var body by remember { mutableStateOf(activity.existingData.getString(BundleKeys.MESSAGE).orEmpty()) }
      var vibration by remember {
         mutableStateOf(
            activity.existingData.getString(BundleKeys.NOTIFICATION_VIBRATION)
               ?.replaceFirstChar { it.uppercase() }
               ?.takeIf { it in vibrationOptions } ?: "Short"
         )
      }
      var duration by remember {
         mutableStateOf(activity.existingData.getLong(BundleKeys.NOTIFICATION_DURATION_MS, DEFAULT_DURATION_MS).toString())
      }
      var error by remember { mutableStateOf<String?>(null) }

      fun save() {
         val durationMs = duration.toLongOrNull()
         error = when {
            title.isBlank() -> "Title cannot be blank"
            durationMs == null -> "Duration must be a whole number of milliseconds"
            durationMs < 0 -> "Duration cannot be negative"
            durationMs > MAX_DURATION_MS -> "Duration cannot exceed 300000 milliseconds"
            else -> null
         }
         if (error != null) return

         activity.saveConfiguration(
            Bundle().apply {
               putString(BundleKeys.ACTION, TaskerAction.SEND_NOTIFICATION.name)
               putString(BundleKeys.TITLE, title)
               putString(BundleKeys.MESSAGE, body)
               putString(BundleKeys.NOTIFICATION_VIBRATION, vibration.lowercase())
               putLong(BundleKeys.NOTIFICATION_DURATION_MS, durationMs!!)
               putString(TaskerPluginConstants.VARIABLE_REPLACE_KEYS, "%catapult_status")
            },
            title,
            finish = true
         )
      }

      NotificationScreenContent(
         title = title,
         body = body,
         vibration = vibration,
         duration = duration,
         error = error,
         setTitle = { title = it },
         setBody = { body = it },
         setVibration = { vibration = it },
         setDuration = { duration = it },
         save = ::save,
      )
   }
}

@Composable
private fun NotificationScreenContent(
   title: String,
   body: String,
   vibration: String,
   duration: String,
   error: String?,
   setTitle: (String) -> Unit,
   setBody: (String) -> Unit,
   setVibration: (String) -> Unit,
   setDuration: (String) -> Unit,
   save: () -> Unit,
) {
   var vibrationMenuExpanded by remember { mutableStateOf(false) }
   Column(
      Modifier
         .padding(16.dp)
         .safeDrawingPadding(),
      verticalArrangement = Arrangement.spacedBy(12.dp),
   ) {
      OutlinedTextField(
         value = title,
         onValueChange = setTitle,
         label = { Text("Title") },
         modifier = Modifier.fillMaxWidth(),
         singleLine = true,
         isError = error == "Title cannot be blank",
      )
      OutlinedTextField(
         value = body,
         onValueChange = setBody,
         label = { Text("Body") },
         modifier = Modifier.fillMaxWidth(),
      )
      Column {
         OutlinedTextField(
            value = vibration,
            onValueChange = {},
            label = { Text("Vibration") },
            modifier = Modifier.fillMaxWidth(),
            readOnly = true,
            trailingIcon = {
               androidx.compose.material3.TextButton(onClick = { vibrationMenuExpanded = true }) {
                  Text("Select")
               }
            },
         )
         DropdownMenu(
            expanded = vibrationMenuExpanded,
            onDismissRequest = { vibrationMenuExpanded = false },
         ) {
            vibrationOptions.forEach { option ->
               DropdownMenuItem(
                  text = { Text(option) },
                  onClick = {
                     setVibration(option)
                     vibrationMenuExpanded = false
                  },
               )
            }
         }
      }
      OutlinedTextField(
         value = duration,
         onValueChange = setDuration,
         label = { Text("Duration (milliseconds)") },
         modifier = Modifier.fillMaxWidth(),
         singleLine = true,
         keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
         isError = error != null && error != "Title cannot be blank",
      )
      if (error != null) {
         Text(error, color = MaterialTheme.colorScheme.error)
      }
      Button(onClick = save, modifier = Modifier.fillMaxWidth()) {
         Text("Save")
      }
   }
}

@Serializable
data object NotificationScreenKey : ScreenKey()
