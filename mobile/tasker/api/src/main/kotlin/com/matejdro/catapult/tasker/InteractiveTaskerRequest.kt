package com.matejdro.catapult.tasker

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
         ): List = List(title, items.toList())
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
