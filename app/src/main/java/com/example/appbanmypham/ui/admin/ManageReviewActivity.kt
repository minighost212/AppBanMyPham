package com.example.appbanmypham.ui.admin

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.appbanmypham.data.local.AppDatabase
import com.example.appbanmypham.model.ReviewEntity
import com.example.appbanmypham.ui.review.StarRow
import com.example.appbanmypham.ui.theme.*
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class ManageReviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppBanMyPhamTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = BackgroundPrimary) {
                    ManageReviewScreen(onBack = { finish() })
                }
            }
        }
    }
}

private data class ReviewTab(val key: String?, val label: String)

private val REVIEW_TABS = listOf(
    ReviewTab(null,     "Tất cả"),
    ReviewTab("show",   "Đang hiện"),
    ReviewTab("hidden", "Đã ẩn"),
    ReviewTab("5",      "⭐ 5 sao"),
    ReviewTab("4",      "⭐ 4 sao"),
    ReviewTab("3",      "⭐ 3 sao"),
    ReviewTab("low",    "⭐ 1-2 sao"),
)

@Composable
fun ManageReviewScreen(onBack: () -> Unit = {}) {
    val context = LocalContext.current
    val db      = remember { FirebaseFirestore.getInstance() }
    val dao     = remember { AppDatabase.getInstance(context).reviewDao() }

    val reviews   by dao.getAllReviews().collectAsStateWithLifecycle(initialValue = emptyList())
    var isSyncing by remember { mutableStateOf(true) }

    DisposableEffect(Unit) {
        val reg = db.collection("reviews")
            .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .addSnapshotListener { snap, _ ->
                CoroutineScope(Dispatchers.IO).launch {
                    snap?.documents?.forEach { doc ->
                        dao.insert(ReviewEntity(
                            id        = doc.id,
                            productId = doc.getString("productId") ?: "",
                            orderId   = doc.getString("orderId")   ?: "",
                            userId    = doc.getString("userId")    ?: "",
                            userName  = doc.getString("userName")  ?: "Ẩn danh",
                            rating    = (doc.getLong("rating")     ?: 5L).toInt(),
                            comment   = doc.getString("comment")   ?: "",
                            createdAt = doc.getLong("createdAt")   ?: System.currentTimeMillis(),
                            isHidden  = doc.getBoolean("isHidden") ?: false
                        ))
                    }
                    isSyncing = false
                }
            }
        onDispose { reg.remove() }
    }

    var filterKey    by remember { mutableStateOf<String?>(null) }
    var searchQuery  by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<ReviewEntity?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }
    var snackMsg by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(snackMsg) {
        snackMsg?.let { snackbarHostState.showSnackbar(it); snackMsg = null }
    }

    val filtered = remember(reviews, filterKey, searchQuery) {
        reviews.filter { r ->
            val matchFilter = when (filterKey) {
                "show"   -> !r.isHidden
                "hidden" -> r.isHidden
                "5"      -> r.rating == 5
                "4"      -> r.rating == 4
                "3"      -> r.rating == 3
                "low"    -> r.rating <= 2
                else     -> true
            }
            val matchSearch = searchQuery.isBlank() ||
                    r.userName.contains(searchQuery, ignoreCase = true) ||
                    r.comment.contains(searchQuery, ignoreCase = true)
            matchFilter && matchSearch
        }
    }

    deleteTarget?.let { review ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            containerColor   = Color.White,
            shape            = RoundedCornerShape(20.dp),
            title = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text("🗑️", fontSize = 36.sp)
                    Spacer(Modifier.height(6.dp))
                    Text("Xoá đánh giá?", fontWeight = FontWeight.Bold, color = Color(0xFF1A4A40))
                }
            },
            text = { Text("Đánh giá của \"${review.userName}\" sẽ bị xoá vĩnh viễn.", color = Color(0xFF5A8A80), fontSize = 13.sp) },
            confirmButton = {
                Button(
                    onClick = {
                        db.collection("reviews").document(review.id).delete()
                        CoroutineScope(Dispatchers.IO).launch { dao.delete(review.id) }
                        snackMsg = "🗑️ Đã xoá đánh giá"; deleteTarget = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE57373)),
                    shape  = RoundedCornerShape(12.dp)
                ) { Text("Xoá", color = Color.White, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Huỷ", color = MintGreen) } }
        )
    }

    Scaffold(
        containerColor = BackgroundPrimary,
        snackbarHost   = { SnackbarHost(snackbarHostState) },
        topBar = {
            Column {
                Box(
                    modifier = Modifier.fillMaxWidth().background(brush = AppGradients.mintHorizontal)
                        .statusBarsPadding().padding(horizontal = 4.dp, vertical = 8.dp)
                ) {
                    IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White)
                    }
                    Column(modifier = Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Quản lý Đánh giá", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        if (reviews.isNotEmpty())
                            Text("${reviews.size} đánh giá  •  Ẩn: ${reviews.count { it.isHidden }}", color = Color.White.copy(0.75f), fontSize = 11.sp)
                    }
                    if (isSyncing) CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp,
                        modifier = Modifier.size(18.dp).align(Alignment.CenterEnd).padding(end = 16.dp))
                }
                Box(modifier = Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 16.dp, vertical = 10.dp)) {
                    OutlinedTextField(
                        value = searchQuery, onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Tìm theo tên, nội dung...", color = Color(0xFFAAD8CE)) },
                        leadingIcon = { Icon(Icons.Default.Search, null, tint = MintGreen) },
                        trailingIcon = if (searchQuery.isNotEmpty()) {{ IconButton(onClick = { searchQuery = "" }) { Icon(Icons.Default.Close, null, tint = Color(0xFFAAD8CE)) } }} else null,
                        singleLine = true, shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MintGreen, unfocusedBorderColor = Color(0xFFCCEEE6), cursorColor = MintGreen)
                    )
                }
                LazyRow(modifier = Modifier.fillMaxWidth().background(Color.White),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(REVIEW_TABS) { tab ->
                        val isSelected = tab.key == filterKey
                        val count = when (tab.key) {
                            null -> reviews.size; "show" -> reviews.count { !it.isHidden }
                            "hidden" -> reviews.count { it.isHidden }; "5" -> reviews.count { it.rating == 5 }
                            "4" -> reviews.count { it.rating == 4 }; "3" -> reviews.count { it.rating == 3 }
                            "low" -> reviews.count { it.rating <= 2 }; else -> 0
                        }
                        val interactionSource = remember { MutableInteractionSource() }
                        Box(modifier = Modifier.clip(RoundedCornerShape(20.dp))
                            .background(if (isSelected) AppGradients.mintHorizontal else androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(Color(0xFFEAF9F5), Color(0xFFEAF9F5))))
                            .clickable(indication = null, interactionSource = interactionSource) { filterKey = tab.key }
                            .padding(horizontal = 12.dp, vertical = 7.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                Text(tab.label, color = if (isSelected) Color.White else MintGreen, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal)
                                if (count > 0) Box(modifier = Modifier.size(18.dp).clip(CircleShape)
                                    .background(if (isSelected) Color.White.copy(0.3f) else MintGreen.copy(0.15f)), contentAlignment = Alignment.Center) {
                                    Text("$count", color = if (isSelected) Color.White else MintGreen, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    ) { padding ->
        when {
            isSyncing && reviews.isEmpty() -> Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) { CircularProgressIndicator(color = MintGreen); Spacer(Modifier.height(10.dp)); Text("Đang tải đánh giá...", color = Color(0xFF8ACABA), fontSize = 13.sp) }
            }
            filtered.isEmpty() -> Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("💬", fontSize = 48.sp); Spacer(Modifier.height(8.dp)); Text("Không có đánh giá nào", color = Color(0xFF8ACABA), fontSize = 15.sp) }
            }
            else -> LazyColumn(modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(filtered, key = { it.id }) { review ->
                    AdminReviewCard(review = review,
                        onToggleHide = { r ->
                            val newHidden = !r.isHidden
                            db.collection("reviews").document(r.id).update("isHidden", newHidden)
                            CoroutineScope(Dispatchers.IO).launch { dao.setHidden(r.id, newHidden) }
                            snackMsg = if (newHidden) "🙈 Đã ẩn đánh giá" else "👁️ Đã hiện đánh giá"
                        },
                        onDelete = { deleteTarget = it })
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
private fun AdminReviewCard(review: ReviewEntity, onToggleHide: (ReviewEntity) -> Unit, onDelete: (ReviewEntity) -> Unit) {
    val dateStr = remember(review.createdAt) { SimpleDateFormat("dd/MM/yyyy  HH:mm", Locale.getDefault()).format(Date(review.createdAt)) }
    val initial = review.userName.firstOrNull()?.uppercaseChar()?.toString() ?: "U"
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = if (review.isHidden) Color(0xFFFAFAFA) else Color.White),
        elevation = CardDefaults.cardElevation(if (review.isHidden) 0.dp else 2.dp)) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(38.dp).clip(CircleShape).background(if (review.isHidden) androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(Color(0xFFCCCCCC), Color(0xFFCCCCCC))) else AppGradients.mintHorizontal), contentAlignment = Alignment.Center) {
                        Text(initial, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(review.userName, color = if (review.isHidden) Color(0xFFAAAAAA) else Color(0xFF1A4A40), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (review.isHidden) { Spacer(Modifier.width(6.dp)); Box(modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Color(0xFFEEEEEE)).padding(horizontal = 6.dp, vertical = 2.dp)) { Text("Đã ẩn", color = Color(0xFF9E9E9E), fontSize = 10.sp) } }
                        }
                        Text(dateStr, color = Color(0xFFAAD8CE), fontSize = 11.sp)
                    }
                }
                Row {
                    IconButton(onClick = { onToggleHide(review) }, modifier = Modifier.size(34.dp)) {
                        Icon(if (review.isHidden) Icons.Default.Visibility else Icons.Default.VisibilityOff, null, tint = if (review.isHidden) MintGreen else Color(0xFF8ACABA), modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = { onDelete(review) }, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.Default.Delete, null, tint = Color(0xFFE57373), modifier = Modifier.size(18.dp))
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                StarRow(rating = review.rating, size = 16)
                Text("SP: ${review.productId.take(8)}", color = Color(0xFFAAD8CE), fontSize = 10.sp)
            }
            if (review.comment.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(if (review.isHidden) Color(0xFFF5F5F5) else Color(0xFFF8FFFE)).padding(10.dp)) {
                    Text(review.comment, color = if (review.isHidden) Color(0xFFAAAAAA) else Color(0xFF4A7A70), fontSize = 13.sp, lineHeight = 19.sp, maxLines = 4, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Receipt, null, tint = Color(0xFFAAD8CE), modifier = Modifier.size(12.dp))
                Spacer(Modifier.width(4.dp))
                Text("Đơn: #${review.orderId.take(8).uppercase()}", color = Color(0xFFAAD8CE), fontSize = 11.sp)
            }
        }
    }
}