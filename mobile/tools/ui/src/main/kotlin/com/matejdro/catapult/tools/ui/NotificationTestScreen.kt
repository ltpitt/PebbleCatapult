package com.matejdro.catapult.tools.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.matejdro.catapult.navigation.keys.NotificationTestScreenKey
import com.matejdro.catapult.ui.components.ErrorAlertDialog
import com.matejdro.catapult.ui.debugging.FullScreenPreviews
import com.matejdro.catapult.ui.debugging.PreviewTheme
import io.rebble.pebblekit2.common.model.TimelineResult
import si.inova.kotlinova.core.outcome.Outcome
import si.inova.kotlinova.navigation.di.ContributesScreenBinding
import si.inova.kotlinova.navigation.instructions.goBack
import si.inova.kotlinova.navigation.navigator.Navigator
import si.inova.kotlinova.navigation.screens.InjectNavigationScreen
import si.inova.kotlinova.navigation.screens.Screen

@InjectNavigationScreen
@ContributesScreenBinding
class NotificationTestScreen(
   private val navigator: Navigator,
   private val viewModel: NotificationTestViewModel,
) : Screen<NotificationTestScreenKey>() {
   @Composable
   override fun Content(key: NotificationTestScreenKey) {
      val title = viewModel.title.collectAsStateWithLifecycle().value
      val body = viewModel.body.collectAsStateWithLifecycle().value
      val sendResult = viewModel.sendResult.collectAsStateWithLifecycle().value

      NotificationTestScreenContent(
         title = title,
         body = body,
         sendResult = sendResult,
         goBack = navigator::goBack,
         setTitle = viewModel::setTitle,
         setBody = viewModel::setBody,
         send = viewModel::send,
      )
   }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotificationTestScreenContent(
   title: String,
   body: String,
   sendResult: Outcome<TimelineResult?>?,
   goBack: () -> Unit,
   setTitle: (String) -> Unit,
   setBody: (String) -> Unit,
   send: () -> Unit,
) {
   Scaffold(
      topBar = {
         TopAppBar(
            title = { Text(stringResource(R.string.notification_test)) },
            navigationIcon = {
               IconButton(onClick = goBack) {
                  Icon(
                     imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                     contentDescription = stringResource(R.string.back),
                  )
               }
            },
         )
      },
   ) { padding ->
      Column(
         Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(16.dp),
         verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
         Text(stringResource(R.string.notification_test_explanation))

         OutlinedTextField(
            value = title,
            onValueChange = setTitle,
            label = { Text(stringResource(R.string.notification_test_title)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
         )
         OutlinedTextField(
            value = body,
            onValueChange = setBody,
            label = { Text(stringResource(R.string.notification_test_body)) },
            modifier = Modifier.fillMaxWidth(),
         )
         Button(
            onClick = send,
            enabled = title.isNotBlank() && sendResult !is Outcome.Progress,
            modifier = Modifier.fillMaxWidth(),
         ) {
            Text(stringResource(R.string.notification_test_send))
         }

         SendResult(sendResult)
         ErrorAlertDialog(sendResult)
      }
   }
}

@Composable
private fun SendResult(sendResult: Outcome<TimelineResult?>?) {
   when (sendResult) {
      null -> Unit

      is Outcome.Progress -> CircularProgressIndicator()

      is Outcome.Error -> Unit // Surfaced via ErrorAlertDialog instead.

      is Outcome.Success -> {
         val result = sendResult.data ?: return
         val message = when (result) {
            TimelineResult.Success -> stringResource(R.string.notification_test_success)
            TimelineResult.FailedNoPebbleApp -> stringResource(R.string.notification_test_no_pebble_app)
            TimelineResult.FailedNoPermissions -> stringResource(R.string.notification_test_no_permissions)
            TimelineResult.FailedUnsupportedAction -> stringResource(R.string.notification_test_unsupported)
            TimelineResult.FailedUnknownPin -> stringResource(R.string.notification_test_unknown_pin)
            is TimelineResult.Unknown -> stringResource(
               R.string.notification_test_error,
               result.message.orEmpty(),
            )
         }
         val color = if (result == TimelineResult.Success) {
            MaterialTheme.colorScheme.primary
         } else {
            MaterialTheme.colorScheme.error
         }
         Text(message, color = color)
      }
   }
}

@FullScreenPreviews
@Composable
internal fun NotificationTestScreenPreview() {
   PreviewTheme {
      NotificationTestScreenContent(
         title = "Test notification",
         body = "Sent from the Catapult notification test tool",
         sendResult = Outcome.Success(TimelineResult.Success),
         goBack = {},
         setTitle = {},
         setBody = {},
      ) {}
   }
}
