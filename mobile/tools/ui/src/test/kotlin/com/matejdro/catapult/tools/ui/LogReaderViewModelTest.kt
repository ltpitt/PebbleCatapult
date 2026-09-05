package com.matejdro.catapult.tools.ui

import com.matejdro.catapult.logging.FileLoggingController
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import si.inova.kotlinova.core.outcome.Outcome
import si.inova.kotlinova.core.outcome.CoroutineResourceManager
import si.inova.kotlinova.core.test.outcomes.shouldBeSuccessWithData
import si.inova.kotlinova.core.test.outcomes.testCoroutineResourceManager
import si.inova.kotlinova.core.reporting.ErrorReporter
import java.io.File
import java.nio.file.Files
import java.time.LocalDate

class LogReaderViewModelTest {
   private val scope = TestScope()
   private lateinit var logFolder: File
   private lateinit var controller: FakeFileLoggingController
   private lateinit var viewModel: LogReaderViewModel

   @BeforeEach
   fun setUp() {
      logFolder = Files.createTempDirectory("log-reader-view-model-test").toFile()
      controller = FakeFileLoggingController(logFolder)
      viewModel = LogReaderViewModel(
         scope.testCoroutineResourceManager(),
         {},
         controller,
      ) { LocalDate.of(2026, 9, 6) }
   }

   @AfterEach
   fun tearDown() {
      logFolder.deleteRecursively()
   }

   @Test
   fun `loads today's logs`() = scope.runTest {
      logFolder.resolve("log_2026-09-06_08-00-00.txt").writeText("hello")

      viewModel.readTodayLogs()
      awaitResult()

      viewModel.logContent.value shouldBeSuccessWithData "hello"
   }

   @Test
   fun `exposes empty result when no logs exist`() = scope.runTest {
      viewModel.readTodayLogs()
      awaitResult()

      viewModel.logContent.value shouldBeSuccessWithData null
   }

   @Test
   fun `exposes error when flushing logs fails`() = scope.runTest {
      controller.flushFailure = IllegalStateException("flush failed")
      val errorViewModel = LogReaderViewModel(
         CoroutineResourceManager(backgroundScope, ErrorReporter {}),
         {},
         controller,
      ) { LocalDate.of(2026, 9, 6) }

      errorViewModel.readTodayLogs()
      repeat(100) {
         runCurrent()
         if (errorViewModel.logContent.value is Outcome.Error) return@repeat
         Thread.sleep(10)
      }

      errorViewModel.logContent.value.shouldBeInstanceOf<Outcome.Error<String?>>()
   }

   private fun TestScope.awaitResult() {
      repeat(100) {
         runCurrent()
         if (viewModel.logContent.value !is Outcome.Progress) return
         Thread.sleep(10)
      }
   }

   private class FakeFileLoggingController(
      private val folder: File,
   ) : FileLoggingController {
      var flushFailure: Throwable? = null

      override fun flush() {
         flushFailure?.let { throw it }
      }

      override fun getLogFolder(): File = folder

      override fun getDeviceInfo(): String = ""
   }
}
