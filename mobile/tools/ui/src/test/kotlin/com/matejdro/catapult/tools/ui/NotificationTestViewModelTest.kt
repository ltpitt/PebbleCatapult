package com.matejdro.catapult.tools.ui

import com.matejdro.catapult.bluetooth.FakeWatchNotificationSender
import com.matejdro.catapult.bluetooth.NotificationSendResult
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import si.inova.kotlinova.core.outcome.CoroutineResourceManager
import si.inova.kotlinova.core.outcome.Outcome
import si.inova.kotlinova.core.reporting.ErrorReporter
import si.inova.kotlinova.core.test.TestScopeWithDispatcherProvider

class NotificationTestViewModelTest {
   private val scope = TestScopeWithDispatcherProvider()
   private val sender = FakeWatchNotificationSender()
   private val viewModel = NotificationTestViewModel(
      CoroutineResourceManager(scope, ErrorReporter {}),
      {},
      sender,
   )

   @Test
   fun `Starts pre-filled with placeholder title and body`() = scope.runTest {
      viewModel.title.value shouldBe "Test notification"
      viewModel.body.value shouldBe "Sent from the Catapult notification test tool"
   }

   @Test
   fun `Sends title and body entered by the user`() = scope.runTest {
      viewModel.setTitle("Door")
      viewModel.setBody("Front door opened")

      viewModel.send()
      advanceUntilIdle()

      sender.sentNotifications shouldBe listOf(FakeWatchNotificationSender.SentNotification("Door", "Front door opened"))
      viewModel.sendResult.value shouldBe Outcome.Success(NotificationSendResult.SUCCESS)
   }

   @Test
   fun `Surfaces failure results from the sender`() = scope.runTest {
      sender.result = NotificationSendResult.MISSING_PERMISSION

      viewModel.send()
      advanceUntilIdle()

      viewModel.sendResult.value shouldBe Outcome.Success(NotificationSendResult.MISSING_PERMISSION)
   }

   @Test
   fun `Resets send result`() = scope.runTest {
      viewModel.send()
      advanceUntilIdle()

      viewModel.resetSendResult()

      viewModel.sendResult.value shouldBe Outcome.Success(null)
   }

   @Test
   fun `Reports unexpected failures as an error outcome`() = scope.runTest {
      sender.failure = IllegalStateException("boom")

      viewModel.send()
      advanceUntilIdle()

      viewModel.sendResult.value.shouldBeInstanceOf<Outcome.Error<NotificationSendResult?>>()
   }
}
