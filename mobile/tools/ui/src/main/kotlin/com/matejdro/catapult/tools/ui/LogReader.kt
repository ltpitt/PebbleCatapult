package com.matejdro.catapult.tools.ui

import java.io.File
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle

class LogReader {
   fun read(logFolder: File, date: LocalDate): String? {
      val contents = logFolder.listFiles()
         ?.asSequence()
         ?.mapNotNull { file ->
            if (!file.isFile) {
               return@mapNotNull null
            }

            val timestamp = LOG_FILENAME.matchEntire(file.name)
               ?.groupValues
               ?.get(1)
               ?.let { parseTimestamp(it) }

            if (timestamp?.toLocalDate() == date) {
               file.name to file.readText(StandardCharsets.UTF_8)
            } else {
               null
            }
         }
         ?.sortedBy { it.first }
         ?.map { it.second }
         ?.toList()
         .orEmpty()

      return if (contents.isEmpty()) {
         null
      } else {
         contents.filter { it.isNotEmpty() }.joinToString("\n")
      }
   }

   private fun parseTimestamp(value: String): LocalDateTime? =
      runCatching { TIMESTAMP_FORMATTER.parse(value, LocalDateTime::from) }.getOrNull()

   private companion object {
      val LOG_FILENAME = Regex("""^log_(\d{4}-\d{2}-\d{2}_\d{2}-\d{2}-\d{2})\.txt$""")
      val TIMESTAMP_FORMATTER = DateTimeFormatter
         .ofPattern("uuuu-MM-dd_HH-mm-ss")
         .withResolverStyle(ResolverStyle.STRICT)
   }
}
