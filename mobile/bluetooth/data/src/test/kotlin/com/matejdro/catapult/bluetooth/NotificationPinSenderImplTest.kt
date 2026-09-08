package com.matejdro.catapult.bluetooth

import com.matejdro.pebble.bluetooth.common.test.FakePebbleSender
import io.kotest.matchers.shouldBe
import io.rebble.pebblekit2.common.model.TimelineLayout
import io.rebble.pebblekit2.common.model.TimelineLayoutType
import io.rebble.pebblekit2.common.model.TimelineResult
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import si.inova.kotlinova.core.test.TestScopeWithDispatcherProvider
import si.inova.kotlinova.core.test.time.virtualTimeProvider

class NotificationPinSenderImplTest {
   private val scope = TestScopeWithDispatcherProvider()
   private val pebbleSender = FakePebbleSender(scope.virtualTimeProvider())
   private val notificationPinSender = NotificationPinSenderImpl(pebbleSender, scope.virtualTimeProvider())

   @Test
   fun `Insert a persistent generic notification timeline pin`() = scope.runTest {
      notificationPinSender.sendNotification("Door", "Front door opened") shouldBe TimelineResult.Success

      val pin = pebbleSender.insertedPins.single()
      pin.layout shouldBe TimelineLayout(
         type = TimelineLayoutType.GENERIC_NOTIFICATION,
         title = "Door",
         body = "Front door opened",
      )
      pin.duration shouldBe null
   }

   @Test
   fun `Propagate transport failures unchanged`() = scope.runTest {
      pebbleSender.timelineResult = TimelineResult.FailedNoPebbleApp

      notificationPinSender.sendNotification("Door", "Front door opened") shouldBe
         TimelineResult.FailedNoPebbleApp
   }
}
