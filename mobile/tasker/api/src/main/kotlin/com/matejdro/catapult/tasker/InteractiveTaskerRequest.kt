package com.matejdro.catapult.tasker

sealed interface InteractiveTaskerRequest {
   data class List(
      val title: String,
      val items: kotlin.collections.List<Item>,
   ) : InteractiveTaskerRequest

   data class Confirmation(
      val title: String,
      val message: String,
   ) : InteractiveTaskerRequest

   data class Item(
      val id: String,
      val value: String,
   )
}
