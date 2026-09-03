package com.matejdro.catapult.actionlist.ui.util

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class MaxStringSizeBytesInputTransformationTest {
   private val transformation = MaxStringSizeBytesInputTransformation(maxBytes = 10)

   @Test
   fun `Return unchanged ASCII string that fits within limit`() {
      transformation.trim("Hello") shouldBe "Hello"
   }

   @Test
   fun `Truncate ASCII string that exceeds byte limit`() {
      transformation.trim("Hello World!") shouldBe "Hello Worl"
   }

   @Test
   fun `Return empty string when input is empty`() {
      transformation.trim("") shouldBe ""
   }

   @Test
   fun `Truncate multi-byte characters at byte boundary`() {
      // Each emoji is 4 bytes in UTF-8, so only 2 fit in a 10-byte limit.
      transformation.trim("\uD83D\uDE00\uD83D\uDE01\uD83D\uDE02") shouldBe "\uD83D\uDE00\uD83D\uDE01"
   }

   @Test
   fun `Handle 2-byte characters correctly`() {
      // 'é' (U+00E9) is 2 bytes in UTF-8, so max 10 bytes = 5 characters
      transformation.trim("ééééééé") shouldBe "ééééé"
   }

   @Test
   fun `Return exact fit string unchanged`() {
      // Exactly 10 ASCII bytes
      transformation.trim("1234567890") shouldBe "1234567890"
   }

   @Test
   fun `Handle mix of single and multi-byte characters`() {
      // "AB" = 2 bytes, "é" = 2 bytes each → "ABéééé" = 2 + 8 = 10 bytes exactly
      transformation.trim("ABéééé") shouldBe "ABéééé"
   }

   @Test
   fun `Drop a multi-byte character that does not fit at the boundary`() {
      // "ABCDEFGHI" = 9 bytes, "é" = 2 bytes → 11 bytes total, over limit
      transformation.trim("ABCDEFGHIé") shouldBe "ABCDEFGHI"
   }
}
