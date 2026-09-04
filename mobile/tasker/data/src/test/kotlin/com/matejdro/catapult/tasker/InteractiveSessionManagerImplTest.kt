package com.matejdro.catapult.tasker

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds

class InteractiveSessionManagerImplTest {
   @Test
   fun `await result sends request through registered bridge`() = runTest {
      val manager = newManager()
      var sent: Pair<UInt, InteractiveTaskerRequest>? = null
      manager.registerSender(InteractiveRequestSender { sessionId, request ->
         sent = sessionId to request
      })
      val request = InteractiveTaskerRequest.Confirmation("Confirm", "Proceed?")
      val result = async { manager.awaitResult(request) }
      runCurrent()

      sent shouldBe (1u to request)
      manager.acceptResult("default", 1u, InteractiveTaskerResult.Confirmation(true))
      result.await() shouldBe InteractiveTaskerResult.Confirmation(true)
   }

   @Test
   fun `response from another watch does not complete active session`() = runTest {
      val manager = newManager()
      val result = async {
         manager.awaitResult(InteractiveTaskerRequest.Confirmation("Confirm", "Proceed?"))
      }
      runCurrent()

      manager.acceptResult("other-watch", 1u, InteractiveTaskerResult.Confirmation(true))
      runCurrent()
      result.isCompleted shouldBe false

      manager.acceptResult("default", 1u, InteractiveTaskerResult.Confirmation(true))
      result.await() shouldBe InteractiveTaskerResult.Confirmation(true)
   }

   @Test
   fun `matching list selection completes active session`() = runTest {
      val manager = newManager()
      val request = InteractiveTaskerRequest.List(
         "Locations",
         listOf(InteractiveTaskerRequest.Item("home", "Home")),
      )
      val result = async { manager.awaitResult(request) }
      runCurrent()

      manager.acceptResult("default", 1u, InteractiveTaskerResult.Selection("home", "Home"))

      result.await() shouldBe InteractiveTaskerResult.Selection("home", "Home")
   }

   @Test
   fun `unknown list selection id does not complete active session`() = runTest {
      val manager = newManager()
      val request = InteractiveTaskerRequest.List(
         "Locations",
         listOf(InteractiveTaskerRequest.Item("home", "Home")),
      )
      val result = async { manager.awaitResult(request) }
      runCurrent()

      manager.acceptResult("default", 1u, InteractiveTaskerResult.Selection("work", "Work"))
      runCurrent()
      result.isCompleted shouldBe false

      manager.acceptResult("default", 1u, InteractiveTaskerResult.Selection("home", "Home"))
      result.await() shouldBe InteractiveTaskerResult.Selection("home", "Home")
   }

   @Test
   fun `mismatched list selection value does not complete active session`() = runTest {
      val manager = newManager()
      val request = InteractiveTaskerRequest.List(
         "Locations",
         listOf(InteractiveTaskerRequest.Item("home", "Home")),
      )
      val result = async { manager.awaitResult(request) }
      runCurrent()

      manager.acceptResult("default", 1u, InteractiveTaskerResult.Selection("home", "Office"))
      runCurrent()
      result.isCompleted shouldBe false

      manager.acceptResult("default", 1u, InteractiveTaskerResult.Selection("home", "Home"))
      result.await() shouldBe InteractiveTaskerResult.Selection("home", "Home")
   }

   @Test
   fun `confirmation result does not complete active list session`() = runTest {
      val manager = newManager()
      val result = async {
         manager.awaitResult(
            InteractiveTaskerRequest.List(
               "Locations",
               listOf(InteractiveTaskerRequest.Item("home", "Home")),
            ),
         )
      }
      runCurrent()

      manager.acceptResult("default", 1u, InteractiveTaskerResult.Confirmation(true))
      runCurrent()
      result.isCompleted shouldBe false

      manager.acceptResult("default", 1u, InteractiveTaskerResult.Selection("home", "Home"))
      result.await() shouldBe InteractiveTaskerResult.Selection("home", "Home")
   }

   @Test
   fun `selection result does not complete active confirmation session`() = runTest {
      val manager = newManager()
      val result = async {
         manager.awaitResult(InteractiveTaskerRequest.Confirmation("Confirm", "Proceed?"))
      }
      runCurrent()

      manager.acceptResult("default", 1u, InteractiveTaskerResult.Selection("home", "Home"))
      runCurrent()
      result.isCompleted shouldBe false

      manager.acceptResult("default", 1u, InteractiveTaskerResult.Confirmation(true))
      result.await() shouldBe InteractiveTaskerResult.Confirmation(true)
   }

   @Test
   fun `timeout returns timed out result`() = runTest {
      val manager = newManager()
      val result = async {
         manager.awaitResult(InteractiveTaskerRequest.Confirmation("Confirm", "Proceed?"))
      }
      advanceTimeBy(1_001)

      result.await().shouldBeInstanceOf<InteractiveTaskerResult.TimedOut>().reason shouldBe "Interactive session timed out"
   }

   @Test
   fun `cancellation completes active session`() = runTest {
      val manager = newManager()
      val result = async {
         manager.awaitResult(InteractiveTaskerRequest.Confirmation("Confirm", "Proceed?"))
      }
      runCurrent()

      manager.cancelActive("user cancelled")

      result.await() shouldBe InteractiveTaskerResult.Cancelled("user cancelled")
   }

   @Test
   fun `remote cancellation completes active sessions for either request type`() = runTest {
      val manager = newManager()
      val listResult = async {
         manager.awaitResult(
            InteractiveTaskerRequest.List(
               "Locations",
               listOf(InteractiveTaskerRequest.Item("home", "Home")),
            ),
         )
      }
      runCurrent()

      manager.acceptResult("default", 1u, InteractiveTaskerResult.Cancelled("remote cancelled"))

      listResult.await() shouldBe InteractiveTaskerResult.Cancelled("remote cancelled")

      val confirmationResult = async {
         manager.awaitResult(InteractiveTaskerRequest.Confirmation("Confirm", "Proceed?"))
      }
      runCurrent()

      manager.acceptResult("default", 2u, InteractiveTaskerResult.Cancelled("remote cancelled"))

      confirmationResult.await() shouldBe InteractiveTaskerResult.Cancelled("remote cancelled")
   }

   @Test
   fun `remote failure completes active sessions for either request type`() = runTest {
      val manager = newManager()
      val listResult = async {
         manager.awaitResult(
            InteractiveTaskerRequest.List(
               "Locations",
               listOf(InteractiveTaskerRequest.Item("home", "Home")),
            ),
         )
      }
      runCurrent()

      manager.acceptResult("default", 1u, InteractiveTaskerResult.Failed("remote failed"))

      listResult.await() shouldBe InteractiveTaskerResult.Failed("remote failed")

      val confirmationResult = async {
         manager.awaitResult(InteractiveTaskerRequest.Confirmation("Confirm", "Proceed?"))
      }
      runCurrent()

      manager.acceptResult("default", 2u, InteractiveTaskerResult.Failed("remote failed"))

      confirmationResult.await() shouldBe InteractiveTaskerResult.Failed("remote failed")
   }

   @Test
   fun `caller cancellation clears active session for a later request`() = runTest {
      val manager = newManager()
      var cancelSent: Pair<UInt, String>? = null
      manager.registerSender("default", object : InteractiveRequestSender {
         override suspend fun send(sessionId: UInt, request: InteractiveTaskerRequest) = Unit
         override suspend fun cancel(sessionId: UInt, reason: String) {
            cancelSent = sessionId to reason
         }
      })
      val cancelled = async {
         manager.awaitResult(InteractiveTaskerRequest.Confirmation("Confirm", "Proceed?"))
      }
      runCurrent()

      cancelled.cancel(CancellationException("caller cancelled"))
      cancelled.join()
      cancelSent shouldBe (1u to "Interactive session cancelled")

      val next = async {
         manager.awaitResult(InteractiveTaskerRequest.Confirmation("Next", "Proceed?"))
      }
      runCurrent()
      manager.acceptResult("default", 2u, InteractiveTaskerResult.Confirmation(true))

      next.await() shouldBe InteractiveTaskerResult.Confirmation(true)
   }

   @Test
   fun `stale response does not complete active session`() = runTest {
      val manager = newManager()
      val result = async {
         manager.awaitResult(InteractiveTaskerRequest.Confirmation("Confirm", "Proceed?"))
      }
      runCurrent()

      manager.acceptResult("default", 2u, InteractiveTaskerResult.Confirmation(true))
      runCurrent()
      result.isCompleted shouldBe false

      manager.acceptResult("default", 1u, InteractiveTaskerResult.Confirmation(true))
      result.await() shouldBe InteractiveTaskerResult.Confirmation(true)
   }

   @Test
   fun `duplicate response is ignored`() = runTest {
      val manager = newManager()
      val result = async {
         manager.awaitResult(InteractiveTaskerRequest.Confirmation("Confirm", "Proceed?"))
      }
      runCurrent()

      manager.acceptResult("default", 1u, InteractiveTaskerResult.Confirmation(true))
      manager.acceptResult("default", 1u, InteractiveTaskerResult.Confirmation(false))

      result.await() shouldBe InteractiveTaskerResult.Confirmation(true)
   }

   @Test
   fun `second active request fails`() = runTest {
      val manager = newManager()
      val first = async {
         manager.awaitResult(InteractiveTaskerRequest.Confirmation("First", "Proceed?"))
      }
      runCurrent()

      manager.awaitResult(InteractiveTaskerRequest.Confirmation("Second", "Proceed?")) shouldBe
         InteractiveTaskerResult.Failed("Another interactive session is active")

      manager.cancelActive("done")
      first.await()
   }

   @Test
   fun `unavailable connection returns failure`() = runTest {
      InteractiveSessionManagerImpl(timeout = 1.seconds).awaitResult(
         InteractiveTaskerRequest.Confirmation("Confirm", "Proceed?")
      ) shouldBe InteractiveTaskerResult.Failed("Watch connection is unavailable")
   }

   @Test
   fun `sender failure returns generic failure`() = runTest {
      val manager = InteractiveSessionManagerImpl(timeout = 1.seconds)
      manager.registerSender(InteractiveRequestSender { _, _ -> error("send failed") })

      manager.awaitResult(InteractiveTaskerRequest.Confirmation("Confirm", "Proceed?")) shouldBe
         InteractiveTaskerResult.Failed("send failed")
   }

   private fun newManager() = InteractiveSessionManagerImpl(timeout = 1.seconds).also {
      it.registerSender(InteractiveRequestSender { _, _ -> })
   }
}