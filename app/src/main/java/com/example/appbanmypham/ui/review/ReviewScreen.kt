package com.example.appbanmypham.ui.review

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.appbanmypham.data.local.AppDatabase
import com.example.appbanmypham.model.OrderEntity
import com.example.appbanmypham.model.ReviewEntity
import com.example.appbanmypham.ui.order.parseOrderItems
import com.example.appbanmypham.ui.theme.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

// ─────────────────────────────────────────────────────────────────────────────
// Composable hiển thị danh sách đánh giá + nút viết review (dùng trong
// ProductDetailsScreen bên dưới phần mô tả sản phẩm)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun ProductReviewSection(productId: String) {
    val context = LocalContext.current
    val auth    = remember { FirebaseAuth.getInstance() }
    val db      = remember { FirebaseFirestore.getInstance() }
    val dao     = remember { AppDatabase.getInstance(context).reviewDao() }
    val uid     = auth.currentUser?.uid ?: ""

    // Flow review visible từ Room (đã sync với Firestore bên dưới)
    val reviews by dao.getVisibleReviewsByProduct(productId)
        .collectAsStateWithLifecycle(initialValue = emptyList())

    // Sync Firestore → Room một lần khi mở màn hình
    LaunchedEffect(productId) {
        db.collection("reviews")
            .whereEqualTo("productId", productId)
            .whereEqualTo("isHidden", false)
            .get()
            .addOnSuccessListener { snap ->
                CoroutineScope(Dispatchers.IO).launch {
                    snap.documents.forEach { doc ->
                        val r = ReviewEntity(
                            id        = doc.id,
                            productId = doc.getString("productId") ?: "",
                            orderId   = doc.getString("orderId")   ?: "",
                            userId    = doc.getString("userId")    ?: "",
                            userName  = doc.getString("userName")  ?: "Ẩn danh",
                            rating    = (doc.getLong("rating")     ?: 5L).toInt(),
                            comment   = doc.getString("comment")   ?: "",
                            createdAt = doc.getLong("createdAt")   ?: System.currentTimeMillis(),
                            isHidden  = doc.getBoolean("isHidden") ?: false
                        )
                        dao.insert(r)
                    }
                }
            }
    }

    // Kiểm tra user có đơn hàng "done" chứa sản phẩm này không
    val orderDao       = remember { AppDatabase.getInstance(context).orderDao() }
    val allOrders by orderDao.getOrdersByUser(uid)
        .collectAsStateWithLifecycle(initialValue = emptyList())

    // Đơn hàng done chứa sản phẩm này và chưa review
    var eligibleOrder by remember { mutableStateOf<OrderEntity?>(null) }
    var showWriteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(allOrders, reviews) {
        val reviewedOrderIds = reviews.filter { it.userId == uid }.map { it.orderId }.toSet()
        eligibleOrder = allOrders.firstOrNull { order ->
            order.status == "done" &&
                    order.id !in reviewedOrderIds &&
                    parseOrderItems(order.itemsJson).any { it.productId == productId }
        }
    }

    val avgRating = if (reviews.isEmpty()) 0f
    else reviews.map { it.rating }.average().toFloat()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .width(4.dp).height(18.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MintGreen)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Đánh giá sản phẩm",
                    color = Color(0xFF1A4A40), fontSize = 15.sp, fontWeight = FontWeight.Bold
                )
            }
            if (reviews.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Star, null, tint = Color(0xFFFFB800), modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(3.dp))
                    Text(
                        "%.1f".format(avgRating),
                        color = Color(0xFF1A4A40), fontSize = 14.sp, fontWeight = FontWeight.Bold
                    )
                    Text(" (${reviews.size})", color = Color(0xFF8ACABA), fontSize = 12.sp)
                }
            }
        }

        // Nút viết đánh giá (chỉ hiện nếu đủ điều kiện)
        if (eligibleOrder != null) {
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { showWriteDialog = true },
                modifier = Modifier.fillMaxWidth().height(44.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MintGreen)
            ) {
                Icon(Icons.Default.RateReview, null, tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Viết đánh giá của bạn", color = Color.White, fontWeight = FontWeight.SemiBold)
            }
        }

        Spacer(Modifier.height(12.dp))

        if (reviews.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFF8FFFE))
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("💬", fontSize = 36.sp)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Chưa có đánh giá nào",
                        color = Color(0xFF8ACABA), fontSize = 14.sp
                    )
                    Text(
                        "Hãy là người đầu tiên đánh giá!",
                        color = Color(0xFFAAD8CE), fontSize = 12.sp
                    )
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                reviews.take(5).forEach { review ->
                    ReviewItem(review = review)
                }
                if (reviews.size > 5) {
                    Text(
                        "Xem thêm ${reviews.size - 5} đánh giá...",
                        color = MintGreen, fontSize = 13.sp,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }

    // Dialog viết review
    if (showWriteDialog) {
        WriteReviewDialog(
            productId    = productId,
            orderId      = eligibleOrder!!.id,
            onDismiss    = { showWriteDialog = false },
            onSubmitted  = { showWriteDialog = false }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 1 item review
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun ReviewItem(review: ReviewEntity) {
    val dateStr = remember(review.createdAt) {
        SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(review.createdAt))
    }
    val initial = review.userName.firstOrNull()?.uppercaseChar()?.toString() ?: "U"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFFF8FFFE))
            .padding(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Avatar
        Box(
            modifier = Modifier
                .size(36.dp).clip(CircleShape)
                .background(brush = AppGradients.mintHorizontal),
            contentAlignment = Alignment.Center
        ) {
            Text(initial, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    review.userName,
                    color = Color(0xFF1A4A40), fontSize = 13.sp, fontWeight = FontWeight.SemiBold
                )
                Text(dateStr, color = Color(0xFFAAD8CE), fontSize = 11.sp)
            }
            Spacer(Modifier.height(3.dp))
            StarRow(rating = review.rating, size = 14)
            if (review.comment.isNotBlank()) {
                Spacer(Modifier.height(5.dp))
                Text(
                    review.comment,
                    color = Color(0xFF4A7A70), fontSize = 13.sp, lineHeight = 19.sp
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Hàng sao — dùng chung
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun StarRow(rating: Int, size: Int = 20, interactive: Boolean = false, onRate: (Int) -> Unit = {}) {
    Row {
        (1..5).forEach { i ->
            if (interactive) {
                IconButton(
                    onClick = { onRate(i) },
                    modifier = Modifier.size((size + 8).dp)
                ) {
                    Icon(
                        if (i <= rating) Icons.Default.Star else Icons.Default.StarBorder,
                        contentDescription = null,
                        tint = if (i <= rating) Color(0xFFFFB800) else Color(0xFFDDDDDD),
                        modifier = Modifier.size(size.dp)
                    )
                }
            } else {
                Icon(
                    if (i <= rating) Icons.Default.Star else Icons.Default.StarBorder,
                    contentDescription = null,
                    tint = if (i <= rating) Color(0xFFFFB800) else Color(0xFFDDDDDD),
                    modifier = Modifier.size(size.dp)
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Dialog viết review
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun WriteReviewDialog(
    productId   : String,
    orderId     : String,
    onDismiss   : () -> Unit,
    onSubmitted : () -> Unit
) {
    val context  = LocalContext.current
    val auth     = remember { FirebaseAuth.getInstance() }
    val db       = remember { FirebaseFirestore.getInstance() }
    val dao      = remember { AppDatabase.getInstance(context).reviewDao() }

    var rating    by remember { mutableStateOf(5) }
    var comment   by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMsg  by remember { mutableStateOf("") }

    val user = auth.currentUser

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape  = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Title
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .width(4.dp).height(20.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(MintGreen)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Viết đánh giá",
                        color = Color(0xFF1A4A40), fontSize = 17.sp, fontWeight = FontWeight.Bold
                    )
                }

                // Stars interactive
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        when (rating) {
                            1 -> "😞 Rất tệ"
                            2 -> "😕 Tệ"
                            3 -> "😐 Bình thường"
                            4 -> "😊 Tốt"
                            else -> "🤩 Tuyệt vời!"
                        },
                        color = Color(0xFF1A4A40), fontSize = 15.sp, fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(4.dp))
                    StarRow(rating = rating, size = 32, interactive = true, onRate = { rating = it })
                }

                // Comment
                OutlinedTextField(
                    value         = comment,
                    onValueChange = { comment = it },
                    modifier      = Modifier.fillMaxWidth(),
                    label         = { Text("Nhận xét của bạn (tuỳ chọn)") },
                    minLines      = 3,
                    maxLines      = 5,
                    shape         = RoundedCornerShape(12.dp),
                    colors        = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = MintGreen,
                        unfocusedBorderColor = Color(0xFFCCEEE6),
                        focusedLabelColor    = MintGreen,
                        cursorColor          = MintGreen
                    )
                )

                if (errorMsg.isNotEmpty()) {
                    Text(errorMsg, color = Color(0xFFE57373), fontSize = 12.sp)
                }

                // Buttons
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick  = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape    = RoundedCornerShape(12.dp),
                        colors   = ButtonDefaults.outlinedButtonColors(contentColor = MintGreen)
                    ) { Text("Huỷ") }

                    Button(
                        onClick = {
                            isLoading = true
                            val uid      = user?.uid ?: return@Button
                            val name     = user.displayName?.takeIf { it.isNotBlank() }
                                ?: user.email?.substringBefore("@") ?: "Ẩn danh"
                            val reviewId = UUID.randomUUID().toString()
                            val now      = System.currentTimeMillis()

                            val data = hashMapOf(
                                "productId" to productId,
                                "orderId"   to orderId,
                                "userId"    to uid,
                                "userName"  to name,
                                "rating"    to rating,
                                "comment"   to comment.trim(),
                                "createdAt" to now,
                                "isHidden"  to false
                            )
                            db.collection("reviews").document(reviewId)
                                .set(data)
                                .addOnSuccessListener {
                                    val entity = ReviewEntity(
                                        id        = reviewId,
                                        productId = productId,
                                        orderId   = orderId,
                                        userId    = uid,
                                        userName  = name,
                                        rating    = rating,
                                        comment   = comment.trim(),
                                        createdAt = now,
                                        isHidden  = false
                                    )
                                    CoroutineScope(Dispatchers.IO).launch {
                                        dao.insert(entity)
                                    }
                                    isLoading = false
                                    onSubmitted()
                                }
                                .addOnFailureListener {
                                    isLoading = false
                                    errorMsg  = "Gửi thất bại: ${it.message}"
                                }
                        },
                        enabled  = !isLoading,
                        modifier = Modifier.weight(1f),
                        shape    = RoundedCornerShape(12.dp),
                        colors   = ButtonDefaults.buttonColors(containerColor = MintGreen)
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                color = Color.White,
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Default.Send, null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Gửi", color = Color.White, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}