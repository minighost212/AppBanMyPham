package com.example.appbanmypham.data.dao

import androidx.room.*
import com.example.appbanmypham.model.OrderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface OrderDao {
    @Query("SELECT * FROM orders WHERE userId = :uid ORDER BY createdAt DESC")
    fun getOrdersByUser(uid: String): Flow<List<OrderEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(order: OrderEntity)

    @Query("DELETE FROM orders WHERE id = :orderId")
    suspend fun delete(orderId: String)

    // ← Thêm dòng này để sync trạng thái từ Firestore về Room
    @Query("UPDATE orders SET status = :newStatus WHERE id = :orderId")
    suspend fun updateStatus(orderId: String, newStatus: String)
}