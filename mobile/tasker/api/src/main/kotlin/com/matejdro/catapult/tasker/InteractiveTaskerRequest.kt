package com.matejdro.catapult.tasker

import java.util.Collections

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

   data class Item(
      val id: String,
      val value: String,
   )
}
