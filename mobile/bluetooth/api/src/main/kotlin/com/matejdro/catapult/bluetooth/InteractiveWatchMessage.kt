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
         items.forEach { item ->
            validateItem(id = item.id, value = item.value)
         }
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
         validateListSelection(
            selectedItemId = selectedItemId,
            selectedItemValue = selectedItemValue,
         )
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
         validateItem(
            id = id,
            value = value,
         )
      }
   }

   fun packets(maxPayloadBytes: Int): List<PebbleDictionary> =
      if (this is ShowList) {
         require(maxPayloadBytes > 0) { "Payload limit must be positive" }
         val total = maxOf(1, items.size)
         items.ifEmpty { listOf(null) }.mapIndexed { index, item ->
            packet(
               packetId = PACKET_SHOW_LIST,
               limit = maxPayloadBytes,
               sequence = index,
               total = total,
            ) {
               put(KEY_TITLE, PebbleDictionaryItem.Text(title))
               put(KEY_ITEM_COUNT, PebbleDictionaryItem.UInt8(items.size.toUByte()))
               item?.let { listItem ->
                  put(KEY_ITEM_ID, PebbleDictionaryItem.Text(listItem.id))
                  put(KEY_ITEM_VALUE, PebbleDictionaryItem.Text(listItem.value))
               }
            }
         }
      } else {
         listOf(toPacket(maxPayloadBytes))
      }

   fun toPacket(maxPayloadBytes: Int): PebbleDictionary = when (this) {
      is ShowList -> packets(maxPayloadBytes).single()
      is ListChunk -> error("List chunks are decoded representations and cannot be encoded")
      is ShowConfirmation -> packet(
         packetId = PACKET_SHOW_CONFIRMATION,
         limit = maxPayloadBytes,
         sequence = FIRST_SEQUENCE_INDEX,
         total = SINGLE_CHUNK_TOTAL,
      ) {
         put(KEY_TITLE, PebbleDictionaryItem.Text(title))
         put(KEY_ITEM_VALUE, PebbleDictionaryItem.Text(message))
      }
      is Cancel -> packet(
         packetId = PACKET_CANCEL,
         limit = maxPayloadBytes,
         sequence = FIRST_SEQUENCE_INDEX,
         total = SINGLE_CHUNK_TOTAL,
      ) {
         put(KEY_REASON, PebbleDictionaryItem.Text(reason))
      }
      is ListSelection -> {
         validateListSelection(
            selectedItemId = selectedItemId,
            selectedItemValue = selectedItemValue,
         )
         packet(
            packetId = PACKET_LIST_SELECTION,
            limit = maxPayloadBytes,
            sequence = FIRST_SEQUENCE_INDEX,
            total = SINGLE_CHUNK_TOTAL,
         ) {
            put(KEY_ITEM_ID, PebbleDictionaryItem.Text(selectedItemId))
            put(KEY_ITEM_VALUE, PebbleDictionaryItem.Text(selectedItemValue))
         }
      }
      is ConfirmationResult -> packet(
         packetId = PACKET_CONFIRMATION_RESULT,
         limit = maxPayloadBytes,
         sequence = FIRST_SEQUENCE_INDEX,
         total = SINGLE_CHUNK_TOTAL,
      ) {
         put(KEY_ITEM_ID, PebbleDictionaryItem.UInt8(if (accepted) FLAG_TRUE else FLAG_FALSE))
      }
      is CancelOrError -> packet(
         packetId = PACKET_CANCEL_OR_ERROR,
         limit = maxPayloadBytes,
         sequence = FIRST_SEQUENCE_INDEX,
         total = SINGLE_CHUNK_TOTAL,
      ) {
         error?.let { errorMessage -> put(KEY_REASON, PebbleDictionaryItem.Text(errorMessage)) }
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
         val packet = requireValue<PebbleDictionaryItem.UInt32>(
            data = data,
            key = KEY_PACKET_ID,
            message = "Missing packet ID",
         ).value
         val metadata = decodeMetadata(data)
         return if (packet == PACKET_SHOW_LIST) {
            decodeShowList(data = data, metadata = metadata)
         } else {
            decodeSimplePacket(
               packet = packet,
               data = data,
               metadata = metadata,
            )
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
         require(
            sessionId == chunk.sessionId &&
               title == chunk.title &&
               total == expectedTotal &&
               itemCount == chunk.itemCount.toInt(),
         ) {
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

private fun validateListSelection(selectedItemId: String, selectedItemValue: String) {
   require(selectedItemId.isNotBlank()) { "Selected item id must not be blank" }
   require(selectedItemId.utf8Length() <= InteractiveWatchMessage.MAX_ITEM_ID_BYTES) {
      "Selected item id is too long"
   }
   require(selectedItemValue.utf8Length() <= InteractiveWatchMessage.MAX_ITEM_VALUE_BYTES) {
      "Selected item value is too long"
   }
}

private fun validateItem(id: String, value: String) {
   require(id.isNotBlank()) { "Item id must not be blank" }
   require(id.utf8Length() <= InteractiveWatchMessage.MAX_ITEM_ID_BYTES) { "Item id is too long" }
   require(value.utf8Length() <= InteractiveWatchMessage.MAX_ITEM_VALUE_BYTES) {
      "Item value is too long"
   }
}

private data class DecodedMessageMetadata(
   val sessionId: UInt,
   val sequence: UInt,
   val totalChunks: UShort,
   val terminal: Boolean,
)

private fun decodeMetadata(data: PebbleDictionary): DecodedMessageMetadata {
   val sequence = requireValue<PebbleDictionaryItem.UInt32>(
      data = data,
      key = KEY_SEQUENCE,
      message = "Missing chunk sequence",
   ).value
   val totalChunks = requireValue<PebbleDictionaryItem.UInt16>(
      data = data,
      key = KEY_TOTAL,
      message = "Missing chunk count",
   ).value
   require(totalChunks > FIRST_TOTAL_CHUNKS.toUShort()) { "Chunk count must be positive" }
   require(sequence.toLong() < totalChunks.toLong()) { "Chunk sequence is out of range" }
   val terminalMarker = requireValue<PebbleDictionaryItem.UInt8>(
      data = data,
      key = KEY_TERMINAL,
      message = "Missing terminal marker",
   ).value
   require(terminalMarker == FLAG_FALSE || terminalMarker == FLAG_TRUE) { "Invalid terminal marker" }
   val terminal = terminalMarker == FLAG_TRUE
   require(terminal == (sequence.toLong() == totalChunks.toLong() - LAST_CHUNK_OFFSET)) {
      "Invalid terminal marker for chunk"
   }

   return DecodedMessageMetadata(
      sessionId = requireValue<PebbleDictionaryItem.UInt32>(
         data = data,
         key = KEY_SESSION_ID,
         message = "Missing session ID",
      ).value,
      sequence = sequence,
      totalChunks = totalChunks,
      terminal = terminal,
   )
}

private fun decodeShowList(
   data: PebbleDictionary,
   metadata: DecodedMessageMetadata,
): InteractiveWatchMessage.ListChunk {
   val itemCount = requireValue<PebbleDictionaryItem.UInt8>(
      data = data,
      key = KEY_ITEM_COUNT,
      message = "Missing item count",
   ).value
   require(itemCount.toInt() <= InteractiveWatchMessage.MAX_ITEMS) { "Too many list items" }
   val id = optionalText(data = data, key = KEY_ITEM_ID)
   val value = optionalText(data = data, key = KEY_ITEM_VALUE)
   require((id == null) == (value == null)) { "Incomplete list item" }
   require(itemCount.toInt() == 0 || (id != null && value != null)) { "Missing list item" }
   require(itemCount.toInt() == 0 || metadata.totalChunks.toInt() == itemCount.toInt()) {
      "List item count does not match chunks"
   }
   require(
      itemCount.toInt() > 0 ||
         (
            metadata.sequence == FIRST_SEQUENCE_INDEX.toUInt() &&
               metadata.totalChunks == SINGLE_CHUNK_TOTAL.toUShort() &&
               id == null
            ),
   ) {
      "Invalid empty list chunk"
   }
   val title = requireText(data = data, key = KEY_TITLE)
   require(title.utf8Length() <= InteractiveWatchMessage.MAX_TITLE_BYTES) { "List title is too long" }

   return InteractiveWatchMessage.ListChunk(
      sessionId = metadata.sessionId,
      title = title,
      sequence = metadata.sequence,
      totalChunks = metadata.totalChunks,
      itemCount = itemCount,
      item = if (id != null && value != null) {
         InteractiveWatchMessage.Item(
            id = id,
            value = value,
         )
      } else {
         null
      },
      terminal = metadata.terminal,
   )
}

private fun decodeSimplePacket(
   packet: UInt,
   data: PebbleDictionary,
   metadata: DecodedMessageMetadata,
): InteractiveWatchMessage {
   fun requireTerminal(messageType: String) {
      require(metadata.totalChunks == SINGLE_CHUNK_TOTAL.toUShort() && metadata.terminal) {
         "$messageType must be terminal"
      }
   }

   return when (packet) {
      InteractiveWatchMessage.PACKET_SHOW_CONFIRMATION -> {
         requireTerminal(messageType = "Confirmation")
         InteractiveWatchMessage.ShowConfirmation(
            sessionId = metadata.sessionId,
            title = requireText(data = data, key = KEY_TITLE),
            message = requireText(data = data, key = KEY_ITEM_VALUE),
         )
      }
      InteractiveWatchMessage.PACKET_CANCEL -> {
         requireTerminal(messageType = "Cancel")
         InteractiveWatchMessage.Cancel(
            sessionId = metadata.sessionId,
            reason = requireText(
               data = data,
               key = KEY_REASON,
               message = "Missing cancel reason",
            ),
         )
      }
      InteractiveWatchMessage.PACKET_LIST_SELECTION -> {
         requireTerminal(messageType = "Selection")
         InteractiveWatchMessage.ListSelection(
            sessionId = metadata.sessionId,
            selectedItemId = requireText(
               data = data,
               key = KEY_ITEM_ID,
               message = "Missing selected item id",
            ),
            selectedItemValue = requireText(
               data = data,
               key = KEY_ITEM_VALUE,
               message = "Missing selected item value",
            ),
         )
      }
      InteractiveWatchMessage.PACKET_CONFIRMATION_RESULT -> {
         requireTerminal(messageType = "Result")
         val result = requireValue<PebbleDictionaryItem.UInt8>(
            data = data,
            key = KEY_ITEM_ID,
            message = "Missing confirmation result",
         ).value
         require(result == FLAG_FALSE || result == FLAG_TRUE) { "Invalid confirmation result" }
         InteractiveWatchMessage.ConfirmationResult(
            sessionId = metadata.sessionId,
            accepted = result == FLAG_TRUE,
         )
      }
      InteractiveWatchMessage.PACKET_CANCEL_OR_ERROR -> {
         requireTerminal(messageType = "Cancel")
         InteractiveWatchMessage.CancelOrError(
            sessionId = metadata.sessionId,
            error = optionalText(data = data, key = KEY_REASON),
         )
      }
      else -> throw IllegalArgumentException("Unknown interactive packet $packet")
   }
}

private inline fun <reified T : PebbleDictionaryItem> requireValue(
   data: PebbleDictionary,
   key: UInt,
   message: String,
): T = data[key] as? T ?: throw IllegalArgumentException(message)

private fun requireText(
   data: PebbleDictionary,
   key: UInt,
   message: String = "Missing key $key",
): String = optionalText(data = data, key = key) ?: throw IllegalArgumentException(message)

private fun optionalText(data: PebbleDictionary, key: UInt): String? =
   (data[key] as? PebbleDictionaryItem.Text)?.value

private fun InteractiveWatchMessage.packet(
   packetId: UInt,
   limit: Int,
   sequence: Int,
   total: Int,
   fields: MutableMap<UInt, PebbleDictionaryItem>.() -> Unit = {},
): PebbleDictionary {
   val result = mutableMapOf<UInt, PebbleDictionaryItem>(
      KEY_PACKET_ID to PebbleDictionaryItem.UInt32(packetId),
      KEY_SESSION_ID to PebbleDictionaryItem.UInt32(sessionId),
      KEY_SEQUENCE to PebbleDictionaryItem.UInt32(sequence.toUInt()),
      KEY_TOTAL to PebbleDictionaryItem.UInt16(total.toUShort()),
      KEY_TERMINAL to PebbleDictionaryItem.UInt8(
         if (sequence == total - LAST_CHUNK_OFFSET) FLAG_TRUE else FLAG_FALSE,
      ),
   ).apply(fields)
   val estimated = PACKET_BASE_SIZE + result.values.sumOf { ENTRY_OVERHEAD_BYTES + it.encodedPayloadSize() }
   require(estimated < limit) { "Interactive packet exceeds watch buffer ($estimated >= $limit)" }
   return result
}

private fun PebbleDictionaryItem.encodedPayloadSize(): Int = when (this) {
   is PebbleDictionaryItem.Text -> value.utf8Length() + TEXT_TERMINATOR_BYTES
   is PebbleDictionaryItem.Bytes -> value.size
   is PebbleDictionaryItem.UInt8, is PebbleDictionaryItem.Int8 -> UBYTE_SIZE_BYTES
   is PebbleDictionaryItem.UInt16, is PebbleDictionaryItem.Int16 -> USHORT_SIZE_BYTES
   is PebbleDictionaryItem.UInt32, is PebbleDictionaryItem.Int32 -> UINT_SIZE_BYTES
}

private const val KEY_PACKET_ID = 0u
private const val KEY_SESSION_ID = 1u
private const val KEY_TITLE = 2u
private const val KEY_SEQUENCE = 3u
private const val KEY_TOTAL = 4u
private const val KEY_TERMINAL = 5u
private const val KEY_ITEM_COUNT = 6u
private const val KEY_ITEM_VALUE = 7u
private const val KEY_ITEM_ID = 8u
private const val KEY_REASON = 9u

private const val FIRST_SEQUENCE_INDEX = 0
private const val SINGLE_CHUNK_TOTAL = 1
private const val FIRST_TOTAL_CHUNKS = 0
private const val LAST_CHUNK_OFFSET = 1
private const val PACKET_BASE_SIZE = 1
private const val ENTRY_OVERHEAD_BYTES = 7
private const val TEXT_TERMINATOR_BYTES = 1
private const val UBYTE_SIZE_BYTES = 1
private const val USHORT_SIZE_BYTES = 2
private const val UINT_SIZE_BYTES = 4
private val FLAG_FALSE = 0.toUByte()
private val FLAG_TRUE = 1.toUByte()
