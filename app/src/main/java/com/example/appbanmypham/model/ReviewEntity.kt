package com.example.appbanmypham.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "reviews")
data class ReviewEntity(
    @PrimaryKey
    val id        : String = "",
    val productId : String = "",
    val orderId   : String = "",
    val userId    : String = "",
    val userName  : String = "",
    val rating    : Int    = 5,        // 1–5 sao
    val comment   : String = "",
    val createdAt : Long   = System.currentTimeMillis(),
    val isHidden  : Boolean = false    // admin ẩn bình luận
)