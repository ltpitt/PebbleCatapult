package com.matejdro.catapult.actionlist.ui.util

import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.foundation.text.input.delete
import androidx.compose.foundation.text.input.placeCursorAtEnd

class MaxStringSizeBytesInputTransformation(private val maxBytes: Int) : InputTransformation {
   override fun TextFieldBuffer.transformInput() {
      val trimmedString = trim(this.toString())

      if (length > trimmedString.length) {
         this.delete(start = trimmedString.length, end = length)
         this.placeCursorAtEnd()
      }
   }

   fun trim(text: String): String {
      if (maxBytes <= 0 || text.isEmpty()) return ""

      var bytes = 0
      var end = 0
      while (end < text.length) {
         val codePoint = text.codePointAt(end)
         val characterBytes = when {
            codePoint <= UTF8_ONE_BYTE_MAX -> UTF8_ONE_BYTE_SIZE
            codePoint <= UTF8_TWO_BYTE_MAX -> UTF8_TWO_BYTE_SIZE
            codePoint <= UTF8_THREE_BYTE_MAX -> UTF8_THREE_BYTE_SIZE
            else -> UTF8_FOUR_BYTE_SIZE
         }
         if (bytes + characterBytes > maxBytes) break
         bytes += characterBytes
         end += Character.charCount(codePoint)
      }

      return text.substring(0, end)
   }
}

private const val UTF8_ONE_BYTE_MAX = 0x7F
private const val UTF8_TWO_BYTE_MAX = 0x7FF
private const val UTF8_THREE_BYTE_MAX = 0xFFFF
private const val UTF8_ONE_BYTE_SIZE = 1
private const val UTF8_TWO_BYTE_SIZE = 2
private const val UTF8_THREE_BYTE_SIZE = 3
private const val UTF8_FOUR_BYTE_SIZE = 4
