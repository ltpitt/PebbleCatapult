package com.matejdro.catapult.bluetooth

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.rebble.pebblekit2.common.model.PebbleDictionaryItem
import org.junit.jupiter.api.Test

class WatchNotificationMessageTest {
   @Test
   fun `notification packet carries fields`() {
      val packet = WatchNotificationMessage.Show(
         "Door", "Front door opened", WatchNotificationMessage.Vibration.SHORT, 5_000,
      ).toPacket(256)
      (packet[0u] as PebbleDictionaryItem.UInt32).value shouldBe 11u
      (packet[2u] as PebbleDictionaryItem.Text).value shouldBe "Door"
      (packet[7u] as PebbleDictionaryItem.Text).value shouldBe "Front door opened"
      (packet[6u] as PebbleDictionaryItem.UInt8).value shouldBe 1u
      (packet[8u] as PebbleDictionaryItem.UInt32).value shouldBe 5_000u
   }

   @Test
   fun `notification enforces utf8 and duration bounds`() {
      shouldThrow<IllegalArgumentException> {
         WatchNotificationMessage.Show("é".repeat(33), "body", WatchNotificationMessage.Vibration.NONE, 0)
      }
      shouldThrow<IllegalArgumentException> {
         WatchNotificationMessage.Show("title", "body", WatchNotificationMessage.Vibration.NONE, -1)
      }
      shouldThrow<IllegalArgumentException> {
         WatchNotificationMessage.Show("title", "body", WatchNotificationMessage.Vibration.NONE, 300_001)
      }
   }

   @Test
   fun `notification packet must be strictly below buffer`() {
      val message = WatchNotificationMessage.Show("title", "body", WatchNotificationMessage.Vibration.NONE, 0)
      shouldThrow<IllegalArgumentException> { message.toPacket(1) }
   }
}
