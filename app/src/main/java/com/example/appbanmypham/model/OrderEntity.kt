package com.example.appbanmypham.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "orders")
data class OrderEntity(
    @PrimaryKey val id : String,
    val userId         : String = "",
    val totalPrice     : Double = 0.0,
    val address        : String = "",
    val phoneNumber    : String = "",
    val receiverName   : String = "",
    val status         : String = "pending",
    val paymentMethod  : String = "COD",
    val itemsJson      : String = "",
    val createdAt      : Long   = System.currentTimeMillis()
)