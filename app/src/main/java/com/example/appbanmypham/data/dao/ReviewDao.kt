package com.example.appbanmypham.data.dao

import androidx.room.*
import com.example.appbanmypham.model.ReviewEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReviewDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(review: ReviewEntity)

    /** Tất cả review của 1 sản phẩm, không ẩn, mới nhất trước */
    @Query("SELECT * FROM reviews WHERE productId = :productId AND isHidden = 0 ORDER BY createdAt DESC")
    fun getVisibleReviewsByProduct(productId: String): Flow<List<ReviewEntity>>

    /** Tất cả review của 1 sản phẩm (admin xem đủ) */
    @Query("SELECT * FROM reviews WHERE productId = :productId ORDER BY createdAt DESC")
    fun getAllReviewsByProduct(productId: String): Flow<List<ReviewEntity>>

    /** Tất cả review trong app — admin quản lý */
    @Query("SELECT * FROM reviews ORDER BY createdAt DESC")
    fun getAllReviews(): Flow<List<ReviewEntity>>

    /** Kiểm tra user đã review sản phẩm này từ đơn hàng cụ thể chưa */
    @Query("SELECT COUNT(*) FROM reviews WHERE productId = :productId AND userId = :userId AND orderId = :orderId")
    suspend fun hasReviewed(productId: String, userId: String, orderId: String): Int

    /** Lấy review của user cho 1 sản phẩm */
    @Query("SELECT * FROM reviews WHERE productId = :productId AND userId = :userId LIMIT 1")
    suspend fun getUserReviewForProduct(productId: String, userId: String): ReviewEntity?

    /** Ẩn / hiện review — admin */
    @Query("UPDATE reviews SET isHidden = :hidden WHERE id = :reviewId")
    suspend fun setHidden(reviewId: String, hidden: Boolean)

    /** Xoá review */
    @Query("DELETE FROM reviews WHERE id = :reviewId")
    suspend fun delete(reviewId: String)

    /** Điểm trung bình của sản phẩm (chỉ review visible) */
    @Query("SELECT AVG(rating) FROM reviews WHERE productId = :productId AND isHidden = 0")
    suspend fun avgRating(productId: String): Float?

    /** Đồng bộ từ Firestore — xoá hết rồi insert lại */
    @Query("DELETE FROM reviews WHERE productId = :productId")
    suspend fun deleteByProduct(productId: String)
}