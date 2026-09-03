package com.matejdro.catapult.tasker

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test

class InteractiveTaskerRequestTest {
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
