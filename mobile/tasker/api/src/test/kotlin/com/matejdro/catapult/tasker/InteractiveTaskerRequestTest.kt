package com.matejdro.catapult.tasker

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test

class InteractiveTaskerRequestTest {
   @Test
   fun `serializes list items as JSON without losing delimiters`() {
      val items = listOf(InteractiveTaskerRequest.Item("a=b", "line 1\nline 2"))

      InteractiveTaskerItems.decode(InteractiveTaskerItems.encode(items)) shouldBe items
   }

   @Test
   fun `rejects malformed list item JSON and blank IDs`() {
      listOf("", "not json", "[{}]", """[{"id":" ","value":"x"}]""").forEach { serializedItems ->
         org.junit.jupiter.api.assertThrows<TaskerInvalidInputException> {
            InteractiveTaskerItems.decode(serializedItems)
         }
      }
   }

   @Test
   fun `preserves list item order and values`() {
      val items = listOf(
         InteractiveTaskerRequest.Item("first", "First item"),
         InteractiveTaskerRequest.Item("second", "Second item"),
      )
      val request = InteractiveTaskerRequest.List("Choose an item", items)

      request.title shouldBe "Choose an item"
      request.items shouldBe items
   }

   @Test
   fun `does not reflect mutations to caller provided items`() {
      val items = mutableListOf(InteractiveTaskerRequest.Item("first", "First item"))
      val request = InteractiveTaskerRequest.List("Choose an item", items)

      items += InteractiveTaskerRequest.Item("second", "Second item")

      request.items shouldBe listOf(InteractiveTaskerRequest.Item("first", "First item"))
   }

   @Test
   fun `does not allow mutation through mutable list cast`() {
      val request = InteractiveTaskerRequest.List(
         "Choose an item",
         listOf(InteractiveTaskerRequest.Item("first", "First item")),
      )
      val javaItems = request.items as java.util.List<InteractiveTaskerRequest.Item>

      org.junit.jupiter.api.assertThrows<UnsupportedOperationException> {
         javaItems.add(InteractiveTaskerRequest.Item("second", "Second item"))
      }
   }

   @Test
   fun `represents every result state`() {
      val results = listOf<InteractiveTaskerResult>(
         InteractiveTaskerResult.Selection("id", "value"),
         InteractiveTaskerResult.Confirmation(true),
         InteractiveTaskerResult.Cancelled("user cancelled"),
         InteractiveTaskerResult.TimedOut("timed out"),
         InteractiveTaskerResult.Failed("failed"),
      )

      results[0].shouldBeInstanceOf<InteractiveTaskerResult.Selection>()
      results[1].shouldBeInstanceOf<InteractiveTaskerResult.Confirmation>()
      results[2].shouldBeInstanceOf<InteractiveTaskerResult.Cancelled>()
      results[3].shouldBeInstanceOf<InteractiveTaskerResult.TimedOut>()
      results[4].shouldBeInstanceOf<InteractiveTaskerResult.Failed>()
   }
}
