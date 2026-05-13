package com.example.appbanmypham.model

data class Order(
    val id          : String         = "",
    val userId      : String         = "",
    val items       : List<OrderItem> = emptyList(),
    val totalPrice  : Double         = 0.0,
    val address     : String         = "",
    val phoneNumber : String         = "",
    val receiverName: String         = "",
    val status      : String         = "pending",  // pending, confirmed, shipping, done, cancelled
    val paymentMethod: String        = "COD",
    val createdAt   : Long           = System.currentTimeMillis()
)

data class OrderItem(
    val productId  : String = "",
    val name       : String = "",
    val brandName  : String = "",
    val imageUrl   : String = "",
    val price      : Double = 0.0,
    val quantity   : Int    = 0
)