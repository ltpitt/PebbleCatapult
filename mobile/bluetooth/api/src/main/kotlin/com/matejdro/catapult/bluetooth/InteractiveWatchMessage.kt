package com.matejdro.catapult.bluetooth

import io.rebble.pebblekit2.common.model.PebbleDictionary
import io.rebble.pebblekit2.common.model.PebbleDictionaryItem

/**
 * The wire representation of an interactive request or response.
 *
 * List requests are split into one item per packet. [chunk] always returns chunks in
 * sequence order and never truncates a string or an item list.
 */
sealed interface InteractiveWatchMessage {
   val sessionId: UInt

   data class ShowList(
      override val sessionId: UInt,
      val title: String,
      val items: List<Item>,
   ) : InteractiveWatchMessage {
      init {
         require(title.utf8Length() <= MAX_TITLE_BYTES) { "List title is too long" }
         require(items.size <= MAX_ITEMS) { "Too many list items" }
         items.forEach { it.validate() }
      }
   }

   data class ShowConfirmation(
      override val sessionId: UInt,
      val title: String,
      val message: String,
   ) : InteractiveWatchMessage {
      init {
         require(title.utf8Length() <= MAX_TITLE_BYTES) { "Confirmation title is too long" }
         require(message.utf8Length() <= MAX_MESSAGE_BYTES) { "Confirmation message is too long" }
      }
   }

   data class Cancel(
      override val sessionId: UInt,
      val reason: String,
   ) : InteractiveWatchMessage {
      init {
         require(reason.utf8Length() <= MAX_REASON_BYTES) { "Cancel reason is too long" }
      }
   }

   data class ListChunk(
      override val sessionId: UInt,
      val title: String,
      val sequence: UInt,
      val totalChunks: UShort,
      val itemCount: UByte,
      val item: Item?,
      val terminal: Boolean,
   ) : InteractiveWatchMessage

   data class ListSelection(
      override val sessionId: UInt,
      val selectedItemId: String,
      val selectedItemValue: String,
   ) : InteractiveWatchMessage {
      init {
         validate()
      }

      internal fun validate() {
         require(selectedItemId.isNotBlank()) { "Selected item id must not be blank" }
         require(selectedItemId.utf8Length() <= MAX_ITEM_ID_BYTES) { "Selected item id is too long" }
         require(selectedItemValue.utf8Length() <= MAX_ITEM_VALUE_BYTES) { "Selected item value is too long" }
      }
   }

   data class ConfirmationResult(
      override val sessionId: UInt,
      val accepted: Boolean,
   ) : InteractiveWatchMessage

   data class CancelOrError(
      override val sessionId: UInt,
      val error: String? = null,
   ) : InteractiveWatchMessage {
      init {
         require(error == null || error.utf8Length() <= MAX_ERROR_BYTES) { "Error is too long" }
      }
   }

   data class Item(val id: String, val value: String) {
      init {
         validate()
      }

      internal fun validate() {
         require(id.isNotEmpty()) { "Item id must not be empty" }
         require(id.utf8Length() <= MAX_ITEM_ID_BYTES) { "Item id is too long" }
         require(value.utf8Length() <= MAX_ITEM_VALUE_BYTES) { "Item value is too long" }
      }
   }

   fun packets(maxPayloadBytes: Int): List<PebbleDictionary> = when (this) {
      is ShowList -> {
         require(maxPayloadBytes > 0) { "Payload limit must be positive" }
         val total = maxOf(1, items.size)
         (if (items.isEmpty()) listOf(null) else items).mapIndexed { index, item ->
            packet(PACKET_SHOW_LIST, maxPayloadBytes, index, total) {
               put(2u, PebbleDictionaryItem.Text(title))
              put(6u, PebbleDictionaryItem.UInt8(items.size.toUByte()))
              item?.let {
                 put(8u, PebbleDictionaryItem.Text(it.id))
                 put(7u, PebbleDictionaryItem.Text(it.value))
              }
           }
         }
      }
      else -> listOf(toPacket(maxPayloadBytes))
   }

   fun toPacket(maxPayloadBytes: Int): PebbleDictionary = when (this) {
      is ShowList -> packets(maxPayloadBytes).single()
      is ListChunk -> error("List chunks are decoded representations and cannot be encoded")
      is ShowConfirmation -> packet(PACKET_SHOW_CONFIRMATION, maxPayloadBytes, 0, 1) {
         put(2u, PebbleDictionaryItem.Text(title))
         put(7u, PebbleDictionaryItem.Text(message))
      }
      is Cancel -> packet(PACKET_CANCEL, maxPayloadBytes, 0, 1) {
         put(9u, PebbleDictionaryItem.Text(reason))
      }
      is ListSelection -> {
         validate()
         packet(PACKET_LIST_SELECTION, maxPayloadBytes, 0, 1) {
            put(8u, PebbleDictionaryItem.Text(selectedItemId))
            put(7u, PebbleDictionaryItem.Text(selectedItemValue))
         }
      }
      is ConfirmationResult -> packet(PACKET_CONFIRMATION_RESULT, maxPayloadBytes, 0, 1) {
         put(8u, PebbleDictionaryItem.UInt8(if (accepted) 1u else 0u))
      }
      is CancelOrError -> packet(PACKET_CANCEL_OR_ERROR, maxPayloadBytes, 0, 1) {
         error?.let { put(9u, PebbleDictionaryItem.Text(it)) }
      }
   }

   companion object {
      const val PACKET_SHOW_LIST = 5u
      const val PACKET_SHOW_CONFIRMATION = 6u
      const val PACKET_CANCEL = 7u
      const val PACKET_LIST_SELECTION = 8u
      const val PACKET_CONFIRMATION_RESULT = 9u
      const val PACKET_CANCEL_OR_ERROR = 10u

      const val MAX_ITEMS = 32
      const val MAX_TITLE_BYTES = 64
      const val MAX_MESSAGE_BYTES = 128
      const val MAX_ITEM_ID_BYTES = 32
      const val MAX_ITEM_VALUE_BYTES = 64
      const val MAX_ERROR_BYTES = 64
      const val MAX_REASON_BYTES = 64

      fun decode(data: PebbleDictionary): InteractiveWatchMessage {
         val packet = (data[0u] as? PebbleDictionaryItem.UInt32)?.value
            ?: throw IllegalArgumentException("Missing packet ID")
         val session = (data[1u] as? PebbleDictionaryItem.UInt32)?.value
            ?: throw IllegalArgumentException("Missing session ID")
         val sequence = (data[3u] as? PebbleDictionaryItem.UInt32)?.value
            ?: throw IllegalArgumentException("Missing chunk sequence")
         val total = (data[4u] as? PebbleDictionaryItem.UInt16)?.value
            ?: throw IllegalArgumentException("Missing chunk count")
         require(total > 0.toUShort()) { "Chunk count must be positive" }
         require(sequence.toLong() < total.toLong()) { "Chunk sequence is out of range" }
         val terminalMarker = (data[5u] as? PebbleDictionaryItem.UInt8)?.value
            ?: throw IllegalArgumentException("Missing terminal marker")
         require(terminalMarker == 0u.toUByte() || terminalMarker == 1u.toUByte()) {
            "Invalid terminal marker"
         }
         val terminal = terminalMarker == 1u.toUByte()
         require(terminal == (sequence.toLong() == total.toLong() - 1)) {
            "Invalid terminal marker for chunk"
         }
         fun text(key: UInt, required: Boolean = true): String? {
            val value = (data[key] as? PebbleDictionaryItem.Text)?.value
            require(!required || value != null) { "Missing key $key" }
            return value
         }
         return when (packet) {
            PACKET_SHOW_CONFIRMATION -> {
               require(total == 1.toUShort() && terminal) { "Confirmation must be terminal" }
               ShowConfirmation(session, text(2u)!!, text(7u)!!)
            }
            PACKET_SHOW_LIST -> {
               val itemCount = (data[6u] as? PebbleDictionaryItem.UInt8)?.value
                  ?: throw IllegalArgumentException("Missing item count")
               require(itemCount.toInt() <= MAX_ITEMS) { "Too many list items" }
               val id = text(8u, false)
               val value = text(7u, false)
               require((id == null) == (value == null)) { "Incomplete list item" }
               require(itemCount.toInt() == 0 || (id != null && value != null)) {
                  "Missing list item"
               }
               require(itemCount.toInt() == 0 || total.toInt() == itemCount.toInt()) {
                  "List item count does not match chunks"
               }
               require(itemCount.toInt() > 0 || (sequence == 0u && total == 1.toUShort() && id == null)) {
                  "Invalid empty list chunk"
               }
               ListChunk(
                  session, text(2u)!!, sequence, total, itemCount,
                  id?.let { Item(it, value!!) }, terminal,
               )
            }
            PACKET_CANCEL -> {
               require(total == 1.toUShort() && terminal) { "Cancel must be terminal" }
               Cancel(session, text(9u) ?: throw IllegalArgumentException("Missing cancel reason"))
            }
            PACKET_LIST_SELECTION -> {
               require(total == 1.toUShort() && terminal) { "Selection must be terminal" }
               ListSelection(
                  session,
                  text(8u) ?: throw IllegalArgumentException("Missing selected item id"),
                  text(7u) ?: throw IllegalArgumentException("Missing selected item value"),
               )
            }
            PACKET_CONFIRMATION_RESULT -> {
               require(total == 1.toUShort() && terminal) { "Result must be terminal" }
               val result = (data[8u] as? PebbleDictionaryItem.UInt8)?.value
                  ?: throw IllegalArgumentException("Missing confirmation result")
               require(result == 0u.toUByte() || result == 1u.toUByte()) {
                  "Invalid confirmation result"
               }
               ConfirmationResult(
                  session,
                  result == 1u.toUByte(),
               )
            }
            PACKET_CANCEL_OR_ERROR -> {
               require(total == 1.toUShort() && terminal) { "Cancel must be terminal" }
               CancelOrError(session, text(9u, false))
            }
            else -> throw IllegalArgumentException("Unknown interactive packet $packet")
         }
      }
   }
}

class InteractiveListAssembler {
   private var sessionId: UInt? = null
   private var title: String? = null
   private var total: Int? = null
   private var itemCount: Int? = null
   private val chunks = mutableMapOf<Int, InteractiveWatchMessage.Item?>()

   fun accept(chunk: InteractiveWatchMessage.ListChunk): InteractiveWatchMessage.ShowList? {
      val expectedTotal = chunk.totalChunks.toInt()
      require(expectedTotal > 0) { "Chunk count must be positive" }
      require(chunk.sequence.toLong() < expectedTotal) { "Chunk sequence is out of range" }
      require(chunk.terminal == (chunk.sequence.toLong() == expectedTotal.toLong() - 1)) {
         "Invalid terminal marker for chunk"
      }
      require(chunk.itemCount.toInt() <= InteractiveWatchMessage.MAX_ITEMS) { "Too many list items" }
      require(chunk.itemCount.toInt() == 0 || chunk.itemCount.toInt() == expectedTotal) {
         "List item count does not match chunks"
      }
      require(chunk.itemCount.toInt() == 0 || chunk.item != null) {
         "Missing list item"
      }
      require(chunk.itemCount.toInt() > 0 || (expectedTotal == 1 && chunk.sequence == 0u && chunk.item == null)) {
         "Invalid empty list chunk"
      }
      if (sessionId == null) {
         sessionId = chunk.sessionId
         title = chunk.title
         total = expectedTotal
         itemCount = chunk.itemCount.toInt()
      } else {
         require(sessionId == chunk.sessionId && title == chunk.title && total == expectedTotal &&
           itemCount == chunk.itemCount.toInt()) {
            "List chunk metadata does not match"
         }
      }
      val index = chunk.sequence.toInt()
      val previous = chunks[index]
      require(index !in chunks || previous == chunk.item) { "Conflicting duplicate list chunk" }
      chunks[index] = chunk.item
      if (chunks.size != expectedTotal || chunks.keys.any { it !in 0 until expectedTotal }) return null
      return InteractiveWatchMessage.ShowList(
         sessionId!!,
         title!!,
         (0 until expectedTotal).mapNotNull { chunks[it] },
      )
   }
}

private fun String.utf8Length(): Int = toByteArray(Charsets.UTF_8).size

private fun InteractiveWatchMessage.packet(
   packetId: UInt,
   limit: Int,
   sequence: Int,
   total: Int,
   fields: MutableMap<UInt, PebbleDictionaryItem>.() -> Unit = {},
): PebbleDictionary {
   val result = mutableMapOf<UInt, PebbleDictionaryItem>(
      0u to PebbleDictionaryItem.UInt32(packetId),
      1u to PebbleDictionaryItem.UInt32(sessionId),
      3u to PebbleDictionaryItem.UInt32(sequence.toUInt()),
      4u to PebbleDictionaryItem.UInt16(total.toUShort()),
      5u to PebbleDictionaryItem.UInt8(if (sequence == total - 1) 1u else 0u),
   ).apply(fields)
   val estimated = result.values.sumOf {
      when (it) {
         is PebbleDictionaryItem.Text -> it.value.utf8Length() + 2
         else -> 5
      }
   }
   require(estimated < limit) { "Interactive packet exceeds watch buffer ($estimated >= $limit)" }
   return result
}
