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

      packets.map { (it[3u] as PebbleDictionaryItem.UInt32).value } shouldBe listOf(0u, 1u)
      packets.map { (it[5u] as PebbleDictionaryItem.UInt8).value } shouldBe listOf(0u.toUByte(), 1u.toUByte())
   }

   @Test
   fun `oversized utf8 values are rejected without truncation`() {
      shouldThrow<IllegalArgumentException> {
         InteractiveWatchMessage.ShowConfirmation(1u, "é".repeat(65), "ok")
      }
   }

   @Test
   fun `incomplete and duplicate chunks are deterministic`() {
      val request = InteractiveWatchMessage.ShowList(7u, "x", listOf(
         InteractiveWatchMessage.Item("a", "A"),
         InteractiveWatchMessage.Item("b", "B"),
      ))
      val chunks = request.packets(256).map(InteractiveWatchMessage::decode)
         .filterIsInstance<InteractiveWatchMessage.ListChunk>()
      val assembler = InteractiveListAssembler()

      assembler.accept(chunks[1]) shouldBe null
      assembler.accept(chunks[1]) shouldBe null
      assembler.accept(chunks[0])?.items?.map { it.id } shouldBe listOf("a", "b")
   }

   @Test
   fun `conflicting duplicate chunk is rejected`() {
      val request = InteractiveWatchMessage.ShowList(7u, "x", listOf(
         InteractiveWatchMessage.Item("a", "A"),
      ))
      val chunk = InteractiveWatchMessage.decode(request.packets(256).single())
         as InteractiveWatchMessage.ListChunk
      val conflicting = chunk.copy(item = InteractiveWatchMessage.Item("other", "A"))
      val assembler = InteractiveListAssembler()
      assembler.accept(chunk)
      shouldThrow<IllegalArgumentException> { assembler.accept(conflicting) }
   }
}
