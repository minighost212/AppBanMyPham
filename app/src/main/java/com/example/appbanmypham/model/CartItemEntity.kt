package com.example.appbanmypham.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cart_items")
data class CartItemEntity(
    @PrimaryKey val productId: String,
    val name: String = "",
    val price: Double = 0.0,
    val imageUrl: String = "",
    val brandName: String = "",
    val quantity: Int = 1,
    val updatedAt: Long = System.currentTimeMillis()
)
