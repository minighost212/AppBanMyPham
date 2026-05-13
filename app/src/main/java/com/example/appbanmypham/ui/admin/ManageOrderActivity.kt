package com.example.appbanmypham.ui.admin

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.appbanmypham.ui.theme.*
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.*

class ManageOrderActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppBanMyPhamTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = BackgroundPrimary) {
                    ManageOrderScreen(onBack = { finish() })
                }
            }
        }
    }
}

// ── Data model đọc từ Firestore ───────────────────────────────────────────────
data class AdminOrder(
    val id           : String = "",
    val userId       : String = "",
    val receiverName : String = "",
    val phoneNumber  : String = "",
    val address      : String = "",
    val totalPrice   : Double = 0.0,
    val status       : String = "pending",
    val paymentMethod: String = "COD",
    val createdAt    : Long   = 0L,
    val items        : List<AdminOrderItem> = emptyList()
)

data class AdminOrderItem(
    val productId : String = "",
    val name      : String = "",
    val brandName : String = "",
    val price     : Double = 0.0,
    val quantity  : Int    = 1
)

// ── Trạng thái đơn hàng ───────────────────────────────────────────────────────
data class StatusConfig(
    val key        : String,
    val label      : String,
    val emoji      : String,
    val color      : Color,
    val bgColor    : Color
)

val ALL_STATUSES = listOf(
    StatusConfig("pending",   "Chờ xác nhận", "⏳", Color(0xFFE8A44A), Color(0xFFFFF3E0)),
    StatusConfig("confirmed", "Đã xác nhận",  "✅", Color(0xFF4A90D9), Color(0xFFE8F0FB)),
    StatusConfig("shipping",  "Đang giao",    "🚚", Color(0xFF9B59B6), Color(0xFFF3E8FB)),
    StatusConfig("done",      "Hoàn thành",   "🎉", MintGreen,         Color(0xFFEAF9F5)),
    StatusConfig("cancelled", "Đã hủy",       "❌", Color(0xFFE57373), Color(0xFFFFECEC))
)

fun statusConfig(key: String) = ALL_STATUSES.find { it.key == key }
    ?: StatusConfig(key, key, "📦", Color(0xFF8ACABA), Color(0xFFEAF9F5))

// ── Screen ────────────────────────────────────────────────────────────────────
@Composable
fun ManageOrderScreen(onBack: () -> Unit = {}) {
    val db = remember { FirebaseFirestore.getInstance() }

    var orders        by remember { mutableStateOf(listOf<AdminOrder>()) }
    var isLoading     by remember { mutableStateOf(true) }
    var searchQuery   by remember { mutableStateOf("") }
    var filterStatus  by remember { mutableStateOf<String?>(null) }  // null = tất cả
    var detailOrder   by remember { mutableStateOf<AdminOrder?>(null) }
    var changeTarget  by remember { mutableStateOf<AdminOrder?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }
    var snackMsg by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(snackMsg) {
        snackMsg?.let {
            snackbarHostState.showSnackbar(it)
            snackMsg = null
        }
    }

    // Realtime listener từ Firestore
    DisposableEffect(Unit) {
        var reg: ListenerRegistration? = null
        reg = db.collection("orders")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, err ->
                if (err != null) return@addSnapshotListener
                orders = snap?.documents?.mapNotNull { doc ->
                    runCatching {
                        // Parse items từ List<Map> (Firestore lưu list of map)
                        @Suppress("UNCHECKED_CAST")
                        val rawItems = doc.get("items") as? List<Map<String, Any>> ?: emptyList()
                        val parsedItems = rawItems.map { m ->
                            AdminOrderItem(
                                productId = m["productId"] as? String ?: "",
                                name      = m["name"]      as? String ?: "",
                                brandName = m["brandName"] as? String ?: "",
                                price     = (m["price"] as? Number)?.toDouble() ?: 0.0,
                                quantity  = (m["quantity"] as? Number)?.toInt() ?: 1
                            )
                        }
                        AdminOrder(
                            id            = doc.id,
                            userId        = doc.getString("userId")       ?: "",
                            receiverName  = doc.getString("receiverName") ?: "",
                            phoneNumber   = doc.getString("phoneNumber")  ?: "",
                            address       = doc.getString("address")      ?: "",
                            totalPrice    = doc.getDouble("totalPrice")   ?: 0.0,
                            status        = doc.getString("status")       ?: "pending",
                            paymentMethod = doc.getString("paymentMethod") ?: "COD",
                            createdAt     = doc.getLong("createdAt")      ?: 0L,
                            items         = parsedItems
                        )
                    }.getOrNull()
                } ?: emptyList()
                isLoading = false
            }
        onDispose { reg?.remove() }
    }

    // Lọc
    val filtered = orders.filter { o ->
        val matchStatus = filterStatus == null || o.status == filterStatus
        val matchSearch = searchQuery.isBlank() ||
                o.receiverName.contains(searchQuery, ignoreCase = true) ||
                o.phoneNumber.contains(searchQuery, ignoreCase = true) ||
                o.id.contains(searchQuery, ignoreCase = true)
        matchStatus && matchSearch
    }

    // Thống kê nhanh
    val statsMap = ALL_STATUSES.associate { cfg -> cfg.key to orders.count { it.status == cfg.key } }
    val totalRevenue = orders.filter { it.status == "done" }.sumOf { it.totalPrice }

    Scaffold(
        containerColor = BackgroundPrimary,
        snackbarHost   = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {

            // ── Header ───────────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(brush = AppGradients.mintHorizontal)
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                IconButton(
                    onClick  = onBack,
                    modifier = Modifier
                        .size(36.dp).clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.25f))
                        .align(Alignment.TopStart)
                ) {
                    Icon(Icons.Default.ArrowBack, null, tint = Color.White)
                }

                Column(modifier = Modifier.align(Alignment.BottomStart).padding(top = 48.dp)) {
                    Text("QUẢN LÝ", color = Color.White.copy(0.75f), fontSize = 11.sp, letterSpacing = 2.sp)
                    Text("Đơn hàng", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    Text("${orders.size} đơn  •  Doanh thu: ${"%,.0f".format(totalRevenue)}đ",
                        color = Color.White.copy(0.8f), fontSize = 12.sp)
                }
            }

            // ── Body (rounded top) ───────────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .offset(y = (-20).dp)
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .background(BackgroundPrimary)
            ) {
                Spacer(Modifier.height(12.dp))

                // ── Stat chips ────────────────────────────────────────────────
                LazyRow(
                    contentPadding        = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Chip "Tất cả"
                    item {
                        StatChip(
                            label    = "Tất cả",
                            count    = orders.size,
                            color    = MintGreen,
                            bgColor  = Color(0xFFEAF9F5),
                            selected = filterStatus == null,
                            onClick  = { filterStatus = null }
                        )
                    }
                    items(ALL_STATUSES) { cfg ->
                        StatChip(
                            label    = cfg.label,
                            count    = statsMap[cfg.key] ?: 0,
                            color    = cfg.color,
                            bgColor  = cfg.bgColor,
                            selected = filterStatus == cfg.key,
                            onClick  = { filterStatus = if (filterStatus == cfg.key) null else cfg.key }
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))

                // ── Search bar ─────────────────────────────────────────────────
                OutlinedTextField(
                    value         = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier      = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    placeholder   = { Text("Tìm tên, SĐT, mã đơn...", color = Color(0xFFAAD8CE)) },
                    leadingIcon   = { Icon(Icons.Default.Search, null, tint = MintGreen) },
                    trailingIcon  = {
                        if (searchQuery.isNotEmpty())
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, null, tint = Color(0xFFAAD8CE))
                            }
                    },
                    shape         = RoundedCornerShape(14.dp),
                    singleLine    = true,
                    colors        = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = MintGreen,
                        unfocusedBorderColor = Color(0xFFB2E8DA),
                        focusedTextColor     = Color(0xFF1A4A40),
                        unfocusedTextColor   = Color(0xFF1A4A40),
                        cursorColor          = MintGreen
                    )
                )

                Spacer(Modifier.height(10.dp))

                // ── List ───────────────────────────────────────────────────────
                when {
                    isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MintGreen)
                    }
                    filtered.isEmpty() -> Box(
                        Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("📋", fontSize = 48.sp)
                            Spacer(Modifier.height(8.dp))
                            Text("Không có đơn hàng nào", color = Color(0xFF8ACABA), fontSize = 15.sp)
                        }
                    }
                    else -> LazyColumn(
                        modifier       = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(filtered, key = { it.id }) { order ->
                            AdminOrderCard(
                                order          = order,
                                onViewDetail   = { detailOrder = order },
                                onChangeStatus = { changeTarget = order }
                            )
                        }
                        item { Spacer(Modifier.height(24.dp)) }
                    }
                }
            }
        }
    }

    // ── Dialog chi tiết ───────────────────────────────────────────────────────
    detailOrder?.let { order ->
        OrderDetailDialog(
            order     = order,
            onDismiss = { detailOrder = null },
            onChangeStatus = { changeTarget = order; detailOrder = null }
        )
    }

    // ── Dialog đổi trạng thái ─────────────────────────────────────────────────
    changeTarget?.let { order ->
        ChangeStatusDialog(
            order     = order,
            onDismiss = { changeTarget = null },
            onConfirm = { newStatus ->
                db.collection("orders").document(order.id)
                    .update("status", newStatus)
                    .addOnSuccessListener {
                        val cfg = statusConfig(newStatus)
                        snackMsg = "${cfg.emoji} Đã cập nhật: ${cfg.label}"
                    }
                    .addOnFailureListener { snackMsg = "❌ Cập nhật thất bại" }
                changeTarget = null
            }
        )
    }
}

// ── Stat Chip ─────────────────────────────────────────────────────────────────
@Composable
private fun StatChip(
    label    : String,
    count    : Int,
    color    : Color,
    bgColor  : Color,
    selected : Boolean,
    onClick  : () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) color else bgColor)
            .border(1.dp, if (selected) color else color.copy(0.3f), RoundedCornerShape(20.dp))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                label,
                color      = if (selected) Color.White else color,
                fontSize   = 12.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
            )
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(if (selected) Color.White.copy(0.3f) else color.copy(0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "$count",
                    color      = if (selected) Color.White else color,
                    fontSize   = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// ── Order Card ────────────────────────────────────────────────────────────────
@Composable
private fun AdminOrderCard(
    order          : AdminOrder,
    onViewDetail   : () -> Unit,
    onChangeStatus : () -> Unit
) {
    val cfg = statusConfig(order.status)
    var expanded by remember { mutableStateOf(false) }

    val dateStr = remember(order.createdAt) {
        if (order.createdAt > 0)
            SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(order.createdAt))
        else "--"
    }

    Card(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(18.dp),
        colors    = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {

            // ── Row 1: mã đơn + badge trạng thái ─────────────────────────────
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "Đơn #${order.id.take(8).uppercase()}",
                        color      = Color(0xFF1A4A40),
                        fontWeight = FontWeight.Bold,
                        fontSize   = 14.sp
                    )
                    Text(dateStr, color = Color(0xFFAAD8CE), fontSize = 11.sp)
                }
                StatusBadge(cfg)
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = Color(0xFFEAF9F5))

            // ── Row 2: thông tin người nhận ───────────────────────────────────
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Person, null, tint = MintGreen, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(order.receiverName, color = Color(0xFF1A4A40), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.width(12.dp))
                Icon(Icons.Default.Phone, null, tint = Color(0xFFAAD8CE), modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text(order.phoneNumber, color = Color(0xFF8ACABA), fontSize = 12.sp)
            }

            Spacer(Modifier.height(4.dp))

            Row(verticalAlignment = Alignment.Top) {
                Icon(Icons.Default.LocationOn, null, tint = Color(0xFFAAD8CE), modifier = Modifier.size(14.dp).padding(top = 1.dp))
                Spacer(Modifier.width(4.dp))
                Text(order.address, color = Color(0xFF8ACABA), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            }

            Spacer(Modifier.height(8.dp))

            // ── Row 3: tổng tiền + số sản phẩm ───────────────────────────────
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.ShoppingBag, null, tint = Color(0xFFAAD8CE), modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("${order.items.size} sản phẩm", color = Color(0xFF8ACABA), fontSize = 12.sp)
                }
                Text(
                    "${"%,.0f".format(order.totalPrice)}đ",
                    color      = Color(0xFF1A4A40),
                    fontWeight = FontWeight.Bold,
                    fontSize   = 15.sp
                )
            }

            // ── Expandable: danh sách sản phẩm ───────────────────────────────
            if (order.items.isNotEmpty()) {
                TextButton(
                    onClick = { expanded = !expanded },
                    contentPadding = PaddingValues(horizontal = 0.dp, vertical = 4.dp)
                ) {
                    Icon(
                        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        null, tint = MintGreen, modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        if (expanded) "Ẩn sản phẩm" else "Xem sản phẩm",
                        color = MintGreen, fontSize = 12.sp
                    )
                }
                AnimatedVisibility(visible = expanded, enter = expandVertically(), exit = shrinkVertically()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFFF8FFFE))
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        order.items.forEach { item ->
                            Row(
                                modifier              = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment     = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(item.name, color = Color(0xFF1A4A40), fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    if (item.brandName.isNotEmpty())
                                        Text(item.brandName, color = MintGreen, fontSize = 10.sp)
                                }
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "x${item.quantity}  ${"%,.0f".format(item.price * item.quantity)}đ",
                                    color = Color(0xFF4A7A70), fontSize = 12.sp, fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = Color(0xFFEAF9F5))

            // ── Action buttons ────────────────────────────────────────────────
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Nút xem chi tiết
                OutlinedButton(
                    onClick   = onViewDetail,
                    modifier  = Modifier.weight(1f).height(36.dp),
                    shape     = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    colors    = ButtonDefaults.outlinedButtonColors(contentColor = MintGreen),
                    border    = ButtonDefaults.outlinedButtonBorder.copy(width = 1.dp)
                ) {
                    Icon(Icons.Default.Visibility, null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Chi tiết", fontSize = 12.sp)
                }

                // Nút đổi trạng thái (ẩn nếu đã done/cancelled)
                if (order.status != "done" && order.status != "cancelled") {
                    Box(
                        modifier = Modifier
                            .weight(1f).height(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(brush = AppGradients.mintHorizontal)
                            .clickable { onChangeStatus() },
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.SwapHoriz, null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Cập nhật", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                } else {
                    // Chip nhỏ cho biết trạng thái cuối
                    Box(
                        modifier = Modifier
                            .weight(1f).height(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(cfg.bgColor),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("${cfg.emoji} ${cfg.label}", color = cfg.color, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}

// ── Status Badge ──────────────────────────────────────────────────────────────
@Composable
private fun StatusBadge(cfg: StatusConfig) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(cfg.bgColor)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text("${cfg.emoji} ${cfg.label}", color = cfg.color, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

// ── Order Detail Dialog ───────────────────────────────────────────────────────
@Composable
private fun OrderDetailDialog(
    order          : AdminOrder,
    onDismiss      : () -> Unit,
    onChangeStatus : () -> Unit
) {
    val cfg = statusConfig(order.status)
    val dateStr = remember(order.createdAt) {
        if (order.createdAt > 0)
            SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date(order.createdAt))
        else "--"
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape  = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier.fillMaxWidth()
        ) {
            LazyColumn(
                modifier       = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(24.dp),
            ) {
                // Tiêu đề
                item {
                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Chi tiết đơn hàng", color = Color(0xFF1A4A40), fontWeight = FontWeight.Bold, fontSize = 17.sp)
                            Text("#${order.id.take(8).uppercase()}", color = Color(0xFFAAD8CE), fontSize = 12.sp)
                        }
                        StatusBadge(cfg)
                    }
                    Spacer(Modifier.height(16.dp))
                }

                // Thông tin giao hàng
                item {
                    SectionTitle("Thông tin giao hàng")
                    DetailRow(Icons.Default.Person,     "Người nhận", order.receiverName)
                    DetailRow(Icons.Default.Phone,      "Số điện thoại", order.phoneNumber)
                    DetailRow(Icons.Default.LocationOn, "Địa chỉ", order.address)
                    DetailRow(Icons.Default.Payment,    "Thanh toán", order.paymentMethod)
                    DetailRow(Icons.Default.Schedule,   "Thời gian", dateStr)
                    Spacer(Modifier.height(12.dp))
                }

                // Danh sách sản phẩm
                item {
                    SectionTitle("Sản phẩm (${order.items.size})")
                }
                items(order.items) { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 5.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.Top
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.name, color = Color(0xFF1A4A40), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            if (item.brandName.isNotEmpty())
                                Text(item.brandName, color = MintGreen, fontSize = 11.sp)
                        }
                        Spacer(Modifier.width(8.dp))
                        Column(horizontalAlignment = Alignment.End) {
                            Text("x${item.quantity}", color = Color(0xFF8ACABA), fontSize = 12.sp)
                            Text("${"%,.0f".format(item.price * item.quantity)}đ", color = Color(0xFF1A4A40), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    HorizontalDivider(color = Color(0xFFEAF9F5), thickness = 0.5.dp)
                }

                // Tổng cộng
                item {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Tổng cộng", color = Color(0xFF1A4A40), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text("${"%,.0f".format(order.totalPrice)}đ", color = MintGreen, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                    Spacer(Modifier.height(16.dp))
                }

                // Nút
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick  = onDismiss,
                            modifier = Modifier.weight(1f),
                            shape    = RoundedCornerShape(12.dp),
                            colors   = ButtonDefaults.outlinedButtonColors(contentColor = MintGreen)
                        ) { Text("Đóng") }

                        if (order.status != "done" && order.status != "cancelled") {
                            Box(
                                modifier = Modifier
                                    .weight(1f).height(40.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(brush = AppGradients.mintHorizontal)
                                    .clickable { onChangeStatus() },
                                contentAlignment = Alignment.Center
                            ) {
                                Text("Cập nhật trạng thái", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Change Status Dialog ──────────────────────────────────────────────────────
@Composable
private fun ChangeStatusDialog(
    order     : AdminOrder,
    onDismiss : () -> Unit,
    onConfirm : (String) -> Unit
) {
    // Thứ tự tiến trình: pending → confirmed → shipping → done (hoặc cancelled bất kỳ lúc nào)
    val availableNext = when (order.status) {
        "pending"   -> listOf("confirmed", "cancelled")
        "confirmed" -> listOf("shipping",  "cancelled")
        "shipping"  -> listOf("done",      "cancelled")
        else        -> emptyList()
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape  = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(24.dp)) {

                // Tiêu đề
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp).clip(RoundedCornerShape(10.dp))
                            .background(brush = AppGradients.mintHorizontal),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.SwapHoriz, null, tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("Cập nhật trạng thái", color = Color(0xFF1A4A40), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("Đơn #${order.id.take(8).uppercase()}", color = Color(0xFFAAD8CE), fontSize = 12.sp)
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Trạng thái hiện tại
                val curCfg = statusConfig(order.status)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Hiện tại:", color = Color(0xFF8ACABA), fontSize = 12.sp)
                    Spacer(Modifier.width(8.dp))
                    StatusBadge(curCfg)
                }

                Spacer(Modifier.height(12.dp))

                Text("Chọn trạng thái mới:", color = Color(0xFF8ACABA), fontSize = 12.sp)
                Spacer(Modifier.height(10.dp))

                // Các lựa chọn
                availableNext.forEach { statusKey ->
                    val cfg = statusConfig(statusKey)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(cfg.bgColor)
                            .border(1.dp, cfg.color.copy(0.4f), RoundedCornerShape(14.dp))
                            .clickable { onConfirm(statusKey) }
                            .padding(horizontal = 16.dp, vertical = 14.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(cfg.emoji, fontSize = 20.sp)
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(cfg.label, color = cfg.color, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Text(
                                    when (statusKey) {
                                        "confirmed" -> "Xác nhận và chuẩn bị hàng"
                                        "shipping"  -> "Giao cho đơn vị vận chuyển"
                                        "done"      -> "Khách đã nhận hàng thành công"
                                        "cancelled" -> "Hủy đơn hàng này"
                                        else        -> ""
                                    },
                                    color    = cfg.color.copy(0.7f),
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text("Huỷ", color = Color(0xFF8ACABA))
                }
            }
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────
@Composable
private fun SectionTitle(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
        Box(modifier = Modifier.width(4.dp).height(16.dp).clip(RoundedCornerShape(2.dp)).background(MintGreen))
        Spacer(Modifier.width(8.dp))
        Text(text, color = Color(0xFF1A4A40), fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun DetailRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String) {
    Row(
        modifier              = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.Top
    ) {
        Row(modifier = Modifier.weight(0.4f), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = Color(0xFFAAD8CE), modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(5.dp))
            Text(label, color = Color(0xFF8ACABA), fontSize = 12.sp)
        }
        Text(
            value,
            color      = Color(0xFF1A4A40),
            fontSize   = 12.sp,
            fontWeight = FontWeight.Medium,
            textAlign  = TextAlign.End,
            modifier   = Modifier.weight(0.6f)
        )
    }
}