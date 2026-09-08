package com.matejdro.catapult.tools.ui

import com.matejdro.catapult.bluetooth.NotificationPinSender
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.rebble.pebblekit2.common.model.TimelineResult
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import si.inova.kotlinova.core.outcome.CoroutineResourceManager
import si.inova.kotlinova.core.outcome.Outcome
import si.inova.kotlinova.core.reporting.ErrorReporter
import si.inova.kotlinova.core.test.TestScopeWithDispatcherProvider

class NotificationTestViewModelTest {
   private val scope = TestScopeWithDispatcherProvider()
   private val sender = FakeNotificationPinSender()
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

      sender.sentNotifications shouldBe listOf(FakeNotificationPinSender.SentNotification("Door", "Front door opened"))
      viewModel.sendResult.value shouldBe Outcome.Success(TimelineResult.Success)
   }

   @Test
   fun `Surfaces failure results from the sender`() = scope.runTest {
      sender.result = TimelineResult.FailedNoPebbleApp

      viewModel.send()
      advanceUntilIdle()

      viewModel.sendResult.value shouldBe Outcome.Success(TimelineResult.FailedNoPebbleApp)
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

      viewModel.sendResult.value.shouldBeInstanceOf<Outcome.Error<TimelineResult?>>()
   }

   private class FakeNotificationPinSender : NotificationPinSender {
      data class SentNotification(val title: String, val body: String)

      val sentNotifications = mutableListOf<SentNotification>()
      var result: TimelineResult = TimelineResult.Success
      var failure: Throwable? = null

      override suspend fun sendNotification(title: String, body: String): TimelineResult {
         failure?.let { throw it }
         sentNotifications += SentNotification(title, body)
         return result
      }
   }
}
