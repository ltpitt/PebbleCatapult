package com.matejdro.catapult.tasker.ui.screens.interactive

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class InteractiveScreensTest {
   @Test
   fun `new list configuration uses location examples`() {
      listTitle(null) shouldBe "Choose a location"
      listItems(null) shouldBe
         """[{"id":"home","value":"Home"},{"id":"work","value":"Work"},{"id":"other","value":"Other"}]"""
      listTimeout(null) shouldBe 60_000L
   }

   @Test
   fun `existing list configuration preserves saved values`() {
      listTitle("Choose a door") shouldBe "Choose a door"
      listItems("""[{"id":"front","value":"Front door"}]""") shouldBe
         """[{"id":"front","value":"Front door"}]"""
      listTimeout(15_000L) shouldBe 15_000L
   }
}
