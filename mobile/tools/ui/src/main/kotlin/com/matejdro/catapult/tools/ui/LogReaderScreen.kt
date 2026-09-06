package com.matejdro.catapult.tools.ui

import android.content.Intent
import android.content.ClipData
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.matejdro.catapult.navigation.keys.LogReaderScreenKey
import com.matejdro.catapult.ui.components.ProgressErrorSuccessScaffold
import androidx.compose.ui.platform.ClipEntry
import kotlinx.coroutines.launch
import si.inova.kotlinova.navigation.di.ContributesScreenBinding
import si.inova.kotlinova.navigation.instructions.goBack
import si.inova.kotlinova.navigation.navigator.Navigator
import si.inova.kotlinova.navigation.screens.InjectNavigationScreen
import si.inova.kotlinova.navigation.screens.Screen

@InjectNavigationScreen
@ContributesScreenBinding
class LogReaderScreen(
   private val navigator: Navigator,
   private val viewModel: LogReaderViewModel,
) : Screen<LogReaderScreenKey>() {
   @Composable
   override fun Content(key: LogReaderScreenKey) {
      val logContent = viewModel.logContent.collectAsStateWithLifecycle().value

      LaunchedEffect(Unit) {
         viewModel.readTodayLogs()
      }

      LogReaderScreenContent(
         logContent = logContent,
         goBack = navigator::goBack,
      )
   }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LogReaderScreenContent(
   logContent: si.inova.kotlinova.core.outcome.Outcome<String?>,
   goBack: () -> Unit,
) {
   val clipboard = LocalClipboard.current
   val context = LocalContext.current
   val snackbarHostState = remember { SnackbarHostState() }
   val scope = rememberCoroutineScope()
   val logsCopiedMessage = stringResource(R.string.logs_copied)
   val shareLogsTitle = stringResource(R.string.share_logs_title)

   Scaffold(
      topBar = {
         TopAppBar(
            title = { Text(stringResource(R.string.today_logs)) },
            navigationIcon = {
               IconButton(onClick = goBack) {
                  Icon(
                     imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                     contentDescription = stringResource(R.string.back),
                  )
               }
            },
            actions = {
               val content = logContent.data
               if (!content.isNullOrEmpty()) {
                  IconButton(
                     onClick = {
                        scope.launch {
                           clipboard.setClipEntry(
                              ClipEntry(ClipData.newPlainText(shareLogsTitle, content))
                           )
                           snackbarHostState.showSnackbar(logsCopiedMessage)
                        }
                     },
                  ) {
                     Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = stringResource(R.string.copy_logs),
                     )
                  }
                  IconButton(
                     onClick = {
                        val intent = Intent(Intent.ACTION_SEND).apply {
                           type = "text/plain"
                           putExtra(Intent.EXTRA_TEXT, content)
                        }
                        context.startActivity(
                           Intent.createChooser(intent, shareLogsTitle)
                        )
                     },
                  ) {
                     Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = stringResource(R.string.share_logs),
                     )
                  }
               }
            },
         )
      },
      snackbarHost = { SnackbarHost(snackbarHostState) },
   ) { padding ->
      ProgressErrorSuccessScaffold(
         outcomeProvider = { logContent },
         errorProgressModifier = Modifier.padding(padding),
      ) { content ->
         if (content.isNullOrEmpty()) {
            Text(
               text = stringResource(R.string.no_logs_today),
               modifier = Modifier
                  .fillMaxSize()
                  .padding(padding)
                  .padding(16.dp),
            )
         } else {
            SelectionContainer {
               Text(
                  text = content,
                  fontFamily = FontFamily.Monospace,
                  modifier = Modifier
                     .fillMaxSize()
                     .verticalScroll(rememberScrollState())
                     .padding(padding)
                     .padding(horizontal = 16.dp, vertical = 12.dp),
               )
            }
         }
      }
   }
}
