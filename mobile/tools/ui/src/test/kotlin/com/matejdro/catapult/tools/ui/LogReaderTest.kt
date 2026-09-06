package com.matejdro.catapult.tools.ui

import io.kotest.matchers.shouldBe
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.time.LocalDate
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.Test

class LogReaderTest {
   private val reader = LogReader()

   @Test
   fun `Read valid files for requested date in filename order`() {
      val folder = Files.createTempDirectory("log-reader").toFile()
      try {
         folder.resolve("log_2024-01-02_12-00-00.txt").writeText("second")
         folder.resolve("log_2024-01-02_08-00-00.txt").writeText("first")
         folder.resolve("log_2024-01-03_08-00-00.txt").writeText("other date")

         reader.read(folder, LocalDate.of(2024, 1, 2)) shouldBe "first\nsecond"
      } finally {
         folder.deleteRecursively()
      }
   }

   @Test
   fun `Return null when no files match requested date`() {
      val folder = Files.createTempDirectory("log-reader").toFile()
      try {
         folder.resolve("log_2024-01-03_08-00-00.txt").writeText("other date")

         reader.read(folder, LocalDate.of(2024, 1, 2)) shouldBe null
      } finally {
         folder.deleteRecursively()
      }
   }

   @Test
   fun `Ignore malformed filenames`() {
      val folder = Files.createTempDirectory("log-reader").toFile()
      try {
         folder.resolve("log_2024-01-02_08-00.txt").writeText("missing seconds")
         folder.resolve("log_2024-01-02_08-00-00.log").writeText("wrong extension")
         folder.resolve("log_2024-13-02_08-00-00.txt").writeText("invalid date")
         folder.resolve("notes.txt").writeText("not a log")

         reader.read(folder, LocalDate.of(2024, 1, 2)) shouldBe null
      } finally {
         folder.deleteRecursively()
      }
   }

   @Test
   fun `Skip empty matching files`() {
      val folder = Files.createTempDirectory("log-reader").toFile()
      try {
         folder.resolve("log_2024-01-02_08-00-00.txt").writeText("")
         folder.resolve("log_2024-01-02_09-00-00.txt").writeText("content")

         reader.read(folder, LocalDate.of(2024, 1, 2)) shouldBe "content"
      } finally {
         folder.deleteRecursively()
      }
   }

   @Test
   fun `Return empty string when all matching files are empty`() {
      val folder = Files.createTempDirectory("log-reader").toFile()
      try {
         folder.resolve("log_2024-01-02_08-00-00.txt").writeText("")
         folder.resolve("log_2024-01-02_09-00-00.txt").writeText("")

         reader.read(folder, LocalDate.of(2024, 1, 2)) shouldBe ""
      } finally {
         folder.deleteRecursively()
      }
   }

   @Test
   fun `Decode matching files as UTF-8`() {
      val folder = Files.createTempDirectory("log-reader").toFile()
      try {
         folder.resolve("log_2024-01-02_08-00-00.txt")
            .writeText("Zażółć gęślą jaźń — 日本語 🌍", StandardCharsets.UTF_8)

         reader.read(folder, LocalDate.of(2024, 1, 2)) shouldBe "Zażółć gęślą jaźń — 日本語 🌍"
      } finally {
         folder.deleteRecursively()
      }
   }

   @Test
   fun `Ignore matching directories`() {
      val folder = Files.createTempDirectory("log-reader").toFile()
      try {
         Files.createDirectory(folder.toPath().resolve("log_2024-01-02_08-00-00.txt"))

         reader.read(folder, LocalDate.of(2024, 1, 2)) shouldBe null
      } finally {
         folder.deleteRecursively()
      }
   }

   @Test
   fun `Throw IOException when log folder is a regular file`() {
      val file = Files.createTempFile("log-reader", ".txt").toFile()
      try {
         assertThrows<IOException> {
            reader.read(file, LocalDate.of(2024, 1, 2))
         }
      } finally {
         file.delete()
      }
   }
}
