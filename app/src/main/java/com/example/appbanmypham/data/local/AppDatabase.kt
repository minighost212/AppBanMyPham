package com.example.appbanmypham.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.appbanmypham.data.dao.CartDao
import com.example.appbanmypham.data.dao.OrderDao
import com.example.appbanmypham.data.dao.ProductDao
import com.example.appbanmypham.data.dao.ReviewDao
import com.example.appbanmypham.model.CartItemEntity
import com.example.appbanmypham.model.OrderEntity
import com.example.appbanmypham.model.Product
import com.example.appbanmypham.model.ReviewEntity

@Database(
    entities     = [
        Product::class,
        OrderEntity::class,
        CartItemEntity::class,
        ReviewEntity::class
    ],
    version      = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun productDao(): ProductDao
    abstract fun cartDao()   : CartDao
    abstract fun orderDao()  : OrderDao
    abstract fun reviewDao() : ReviewDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "appbanmypham.db"
                )
                    .fallbackToDestructiveMigration()
                    .build().also { INSTANCE = it }
            }
        }
    }
}