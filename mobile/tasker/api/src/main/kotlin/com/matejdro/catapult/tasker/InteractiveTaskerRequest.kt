package com.matejdro.catapult.tasker

import java.util.Collections
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

sealed interface InteractiveTaskerRequest {
   @ConsistentCopyVisibility
   data class List private constructor(
      val title: String,
      val items: kotlin.collections.List<Item>,
   ) : InteractiveTaskerRequest {
      companion object {
         operator fun invoke(
            title: String,
            items: kotlin.collections.List<Item>,
         ): List = List(title, Collections.unmodifiableList(items.toList()))
      }
   }

   data class Confirmation(
      val title: String,
      val message: String,
   ) : InteractiveTaskerRequest

   @Serializable
   data class Item(
      val id: String,
      val value: String,
   )
}

object InteractiveTaskerItems {
   fun encode(items: List<InteractiveTaskerRequest.Item>): String =
      Json.encodeToString(items)

   fun decode(serialized: String): List<InteractiveTaskerRequest.Item> {
      try {
         return Json.decodeFromString<List<InteractiveTaskerRequest.Item>>(serialized).also { items ->
            if (items.isEmpty() || items.any { it.id.isBlank() }) throw IllegalArgumentException()
         }
      } catch (exception: Exception) {
         throw TaskerInvalidInputException(
            "Items must be a JSON array of objects with non-blank id and value",
         )
      }
   }
}
