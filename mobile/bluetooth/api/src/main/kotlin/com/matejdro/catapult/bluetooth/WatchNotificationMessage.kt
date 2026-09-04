package com.matejdro.catapult.bluetooth

import io.rebble.pebblekit2.common.model.PebbleDictionary
import io.rebble.pebblekit2.common.model.PebbleDictionaryItem

sealed interface WatchNotificationMessage {
   data class Show(
      val title: String,
      val body: String,
      val vibration: Vibration,
      val durationMs: Long,
   ) : WatchNotificationMessage {
      init {
         require(title.utf8Length() <= MAX_TITLE_BYTES) { "Notification title is too long" }
         require(body.utf8Length() <= MAX_BODY_BYTES) { "Notification body is too long" }
         require(durationMs in 0..MAX_DURATION_MS) { "Notification duration is out of range" }
      }

      fun toPacket(maxPayloadBytes: Int): PebbleDictionary {
         require(maxPayloadBytes > 0) { "Payload limit must be positive" }
         val packet = mapOf(
            0u to PebbleDictionaryItem.UInt32(PACKET_SHOW_NOTIFICATION),
            2u to PebbleDictionaryItem.Text(title),
            7u to PebbleDictionaryItem.Text(body),
            6u to PebbleDictionaryItem.UInt8(vibration.wireValue),
            8u to PebbleDictionaryItem.UInt32(durationMs.toUInt()),
         )
         val encoded = 1 + packet.values.sumOf { 7 + it.encodedPayloadSize() }
         require(encoded < maxPayloadBytes) { "Notification packet exceeds watch buffer ($encoded >= $maxPayloadBytes)" }
         return packet
      }
   }

   enum class Vibration(val wireValue: UByte) {
      NONE(0u),
      SHORT(1u),
      DOUBLE(2u),
   }

   companion object {
      const val PACKET_SHOW_NOTIFICATION = 11u
      const val MAX_TITLE_BYTES = 64
      const val MAX_BODY_BYTES = 128
      const val MAX_DURATION_MS = 300_000L
   }
}

private fun String.utf8Length() = toByteArray(Charsets.UTF_8).size

private fun PebbleDictionaryItem.encodedPayloadSize(): Int = when (this) {
   is PebbleDictionaryItem.Text -> value.utf8Length() + 1
   is PebbleDictionaryItem.Bytes -> value.size
   is PebbleDictionaryItem.UInt8, is PebbleDictionaryItem.Int8 -> 1
   is PebbleDictionaryItem.UInt16, is PebbleDictionaryItem.Int16 -> 2
   is PebbleDictionaryItem.UInt32, is PebbleDictionaryItem.Int32 -> 4
}
