package com.matejdro.catapult.bluetooth

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.rebble.pebblekit2.common.model.PebbleDictionaryItem
import org.junit.jupiter.api.Test

class InteractiveWatchMessageTest {
   @Test
   fun `list packets preserve order and terminal marker`() {
      val packets = InteractiveWatchMessage.ShowList(
         42u,
         "Places",
         listOf(
            InteractiveWatchMessage.Item("home", "Home"),
            InteractiveWatchMessage.Item("work", "Work"),
         ),
      ).packets(256)

      packets.map { ((it[3u] ?: error("Missing chunk sequence")) as PebbleDictionaryItem.UInt32).value } shouldBe
         listOf(0u, 1u)
      packets.map { ((it[5u] ?: error("Missing terminal marker")) as PebbleDictionaryItem.UInt8).value } shouldBe
         listOf(0u.toUByte(), 1u.toUByte())
      packets.map { ((it[6u] ?: error("Missing item count")) as PebbleDictionaryItem.UInt8).value } shouldBe
         listOf(2u.toUByte(), 2u.toUByte())
   }

   @Test
   fun `empty list emits one terminal packet`() {
      val packet = InteractiveWatchMessage.ShowList(42u, "Places", emptyList()).packets(256).single()
      ((packet[5u] ?: error("Missing terminal marker")) as PebbleDictionaryItem.UInt8).value shouldBe 1u.toUByte()
      ((packet[6u] ?: error("Missing item count")) as PebbleDictionaryItem.UInt8).value shouldBe 0u.toUByte()
      (InteractiveWatchMessage.decode(packet) as InteractiveWatchMessage.ListChunk).item shouldBe null
   }

   @Test
   fun `oversized utf8 values are rejected without truncation`() {
      shouldThrow<IllegalArgumentException> {
         InteractiveWatchMessage.ShowConfirmation(1u, "é".repeat(65), "ok")
      }
   }

   @Test
   fun `decoding rejects oversized list titles`() {
      val packet = InteractiveWatchMessage.ShowList(1u, "ok", listOf(InteractiveWatchMessage.Item("id", "value")))
         .packets(256)
         .single()
         .toMutableMap()
      packet[2u] = PebbleDictionaryItem.Text("é".repeat(33))

      shouldThrow<IllegalArgumentException> { InteractiveWatchMessage.decode(packet) }
   }

   @Test
   fun `packet size includes complete dictionary encoding`() {
      shouldThrow<IllegalArgumentException> {
         InteractiveWatchMessage.ShowConfirmation(1u, "a", "b").toPacket(69)
      }
   }

   @Test
   fun `incomplete and duplicate chunks are deterministic`() {
      val request = InteractiveWatchMessage.ShowList(
         7u,
         "x",
         listOf(
            InteractiveWatchMessage.Item("a", "A"),
            InteractiveWatchMessage.Item("b", "B"),
         ),
      )
      val chunks = request
         .packets(256)
         .map(InteractiveWatchMessage::decode)
         .filterIsInstance<InteractiveWatchMessage.ListChunk>()
      val assembler = InteractiveListAssembler()

      assembler.accept(chunks[1]) shouldBe null
      assembler.accept(chunks[1]) shouldBe null
      assembler.accept(chunks[0])?.items?.map { it.id } shouldBe listOf("a", "b")
   }

   @Test
   fun `conflicting duplicate chunk is rejected`() {
      val request = InteractiveWatchMessage.ShowList(
         7u,
         "x",
         listOf(
            InteractiveWatchMessage.Item("a", "A"),
         ),
      )
      val chunk = InteractiveWatchMessage.decode(
         request.packets(256).single(),
      )
         as InteractiveWatchMessage.ListChunk
      val conflicting = chunk.copy(item = InteractiveWatchMessage.Item("other", "A"))
      val assembler = InteractiveListAssembler()
      assembler.accept(chunk)
      shouldThrow<IllegalArgumentException> { assembler.accept(conflicting) }
   }

   @Test
   fun `selection carries selected item id and value`() {
      val selection = InteractiveWatchMessage.ListSelection(42u, "home", "Home")
      val decoded = InteractiveWatchMessage.decode(selection.toPacket(256))
      decoded shouldBe selection
   }

   @Test
   fun `selection requires one terminal chunk`() {
      val packet = InteractiveWatchMessage.ListSelection(42u, "home", "Home")
         .toPacket(256)
         .toMutableMap()
         .apply { put(4u, PebbleDictionaryItem.UInt16(2u)) }
      shouldThrow<IllegalArgumentException> { InteractiveWatchMessage.decode(packet) }
   }

   @Test
   fun `selection validates id and value byte limits`() {
      shouldThrow<IllegalArgumentException> {
         InteractiveWatchMessage.ListSelection(42u, " ", "Home")
      }
      shouldThrow<IllegalArgumentException> {
         InteractiveWatchMessage.ListSelection(42u, "é".repeat(17), "Home")
      }
      shouldThrow<IllegalArgumentException> {
         InteractiveWatchMessage.ListSelection(42u, "home", "é".repeat(33))
      }
   }

   @Test
   fun `selection rejects missing and wrong typed fields`() {
      val packet = InteractiveWatchMessage.ListSelection(42u, "home", "Home").toPacket(256)
      shouldThrow<IllegalArgumentException> {
         InteractiveWatchMessage.decode(packet - 8u)
      }
      shouldThrow<IllegalArgumentException> {
         InteractiveWatchMessage.decode(
            packet.toMutableMap().apply {
               put(8u, PebbleDictionaryItem.UInt8(1u))
            },
         )
      }
   }

   @Test
   fun `decoding rejects chunk sequence at total`() {
      val packet = InteractiveWatchMessage.ShowList(42u, "Places", emptyList()).packets(256).single()
         .toMutableMap()
         .apply {
            put(3u, PebbleDictionaryItem.UInt32(1u))
            put(5u, PebbleDictionaryItem.UInt8(0u))
         }
      shouldThrow<IllegalArgumentException> { InteractiveWatchMessage.decode(packet) }
   }

   @Test
   fun `decoding rejects invalid confirmation result`() {
      val packet = InteractiveWatchMessage.ConfirmationResult(42u, true).toPacket(256)
         .toMutableMap()
         .apply { put(8u, PebbleDictionaryItem.UInt8(2u)) }
      shouldThrow<IllegalArgumentException> { InteractiveWatchMessage.decode(packet) }
   }

   @Test
   fun `cancel carries a reason`() {
      val cancel = InteractiveWatchMessage.Cancel(42u, "user cancelled")
      InteractiveWatchMessage.decode(cancel.toPacket(256)) shouldBe cancel
   }

   @Test
   fun `invalid terminal marker is rejected`() {
      val packet = InteractiveWatchMessage.ShowList(42u, "x", emptyList()).packets(256).single()
         .toMutableMap()
         .apply { put(5u, PebbleDictionaryItem.UInt8(2u)) }
      shouldThrow<IllegalArgumentException> { InteractiveWatchMessage.decode(packet) }
   }
}
