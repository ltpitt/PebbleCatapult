package com.matejdro.catapult.tasker

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class TaskerResultMappingTest {
   @Test
   fun `notification success maps success status`() {
      InteractiveTaskerResult.Success.toTaskerBundle().getString("%catapult_status") shouldBe "success"
   }
   @Test
   fun `selection maps result variables and success status`() {
      val bundle = InteractiveTaskerResult.Selection("home", "Home").toTaskerBundle()
      bundle.getString("%catapult_status") shouldBe "success"
      bundle.getString("%catapult_result_id") shouldBe "home"
      bundle.getString("%catapult_result_value") shouldBe "Home"
      bundle.getString("%err") shouldBe null
   }

   @Test
   fun `failure maps status and Tasker error`() {
      val bundle = InteractiveTaskerResult.TimedOut("expired").toTaskerBundle()
      bundle.getString("%catapult_status") shouldBe "timeout"
      bundle.getString("%err") shouldBe "1"
      bundle.getString("%errmsg") shouldBe "expired"
   }

   @Test
   fun `rejected confirmation is a failure`() {
      val bundle = InteractiveTaskerResult.Confirmation(false).toTaskerBundle()
      bundle.getString("%catapult_status") shouldBe "failed"
      bundle.getString("%err") shouldBe "1"
   }
}
