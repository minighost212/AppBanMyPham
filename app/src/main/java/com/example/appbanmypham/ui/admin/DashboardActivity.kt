package com.example.appbanmypham.ui.admin

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.appbanmypham.ui.auth.LoginActivity
import com.example.appbanmypham.ui.theme.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class DashboardActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppBanMyPhamTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = BackgroundPrimary) {
                    DashboardScreen()
                }
            }
        }
    }
}

data class DashboardStats(
    val totalProducts  : Int    = 0,
    val totalBrands    : Int    = 0,
    val totalOrders    : Int    = 0,
    val pendingOrders  : Int    = 0,
    val shippingOrders : Int    = 0,
    val doneOrders     : Int    = 0,
    val totalRevenue   : Double = 0.0,
    val outOfStock     : Int    = 0,
    val totalReviews   : Int    = 0,   // ← MỚI
    val hiddenReviews  : Int    = 0    // ← MỚI
)

@Composable
fun DashboardScreen() {
    val context = LocalContext.current
    val auth    = remember { FirebaseAuth.getInstance() }
    val db      = remember { FirebaseFirestore.getInstance() }

    val currentUser = auth.currentUser
    var adminName   by remember {
        mutableStateOf(
            currentUser?.displayName?.takeIf { it.isNotBlank() }
                ?: currentUser?.email?.substringBefore("@")
                ?: "Admin"
        )
    }
    val adminInitial = adminName.firstOrNull()?.uppercaseChar()?.toString() ?: "A"

    var stats             by remember { mutableStateOf(DashboardStats()) }
    var showLogoutConfirm by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        val regs = mutableListOf<ListenerRegistration>()

        // Sản phẩm
        regs += db.collection("products").addSnapshotListener { snap, _ ->
            val productCount = snap?.size() ?: 0
            val outOfStock   = snap?.documents?.count { (it.getLong("stock") ?: 0L) == 0L } ?: 0
            stats = stats.copy(totalProducts = productCount, outOfStock = outOfStock)
        }

        // Thương hiệu
        regs += db.collection("brands").addSnapshotListener { snap, _ ->
            stats = stats.copy(totalBrands = snap?.size() ?: 0)
        }

        // Đơn hàng
        regs += db.collection("orders").addSnapshotListener { snap, _ ->
            val docs     = snap?.documents ?: emptyList()
            val pending  = docs.count { it.getString("status") == "pending" }
            val shipping = docs.count { it.getString("status") == "shipping" }
            val done     = docs.count { it.getString("status") == "done" }
            val revenue  = docs
                .filter { it.getString("status") == "done" }
                .sumOf { it.getDouble("totalPrice") ?: 0.0 }
            stats = stats.copy(
                totalOrders    = docs.size,
                pendingOrders  = pending,
                shippingOrders = shipping,
                doneOrders     = done,
                totalRevenue   = revenue
            )
        }

        // Đánh giá ← MỚI
        regs += db.collection("reviews").addSnapshotListener { snap, _ ->
            val docs   = snap?.documents ?: emptyList()
            val hidden = docs.count { it.getBoolean("isHidden") == true }
            stats = stats.copy(totalReviews = docs.size, hiddenReviews = hidden)
        }

        onDispose { regs.forEach { it.remove() } }
    }

    if (showLogoutConfirm) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirm = false },
            containerColor   = Color.White,
            shape            = RoundedCornerShape(20.dp),
            title = { Text("Đăng xuất?", fontWeight = FontWeight.Bold, color = Color(0xFF1A4A40)) },
            text  = { Text("Bạn có chắc muốn thoát khỏi trang quản trị?", color = Color(0xFF5A8A80)) },
            confirmButton = {
                Button(
                    onClick = {
                        showLogoutConfirm = false
                        auth.signOut()
                        context.startActivity(
                            Intent(context, LoginActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            }
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE57373))
                ) { Text("Đăng xuất", color = Color.White) }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutConfirm = false }) { Text("Hủy", color = MintGreen) }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
    ) {
        // ── Header ────────────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(brush = AppGradients.mintHorizontal)
                .padding(horizontal = 20.dp, vertical = 24.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(48.dp).clip(CircleShape).background(Color.White.copy(0.3f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(adminInitial, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Xin chào,", color = Color.White.copy(0.8f), fontSize = 12.sp)
                    Text(adminName, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Text("Quản trị viên", color = Color.White.copy(0.7f), fontSize = 11.sp)
                }
            }
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(40.dp).clip(CircleShape)
                    .background(Color.White.copy(0.2f))
                    .clickable { showLogoutConfirm = true },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Logout, null, tint = Color.White, modifier = Modifier.size(20.dp))
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .offset(y = (-20).dp)
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(BackgroundPrimary)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(20.dp))

            SectionLabel("TỔNG QUAN")
            Spacer(Modifier.height(10.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard(Modifier.weight(1f), "${stats.totalProducts}", "Sản phẩm",
                    Icons.Default.Inventory, Color(0xFFEAF9F5), MintGreen)
                StatCard(Modifier.weight(1f), "${stats.totalBrands}", "Thương hiệu",
                    Icons.Default.Category, Color(0xFFE8F0FB), Color(0xFF6B89CC))
            }
            Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard(Modifier.weight(1f), "${stats.totalOrders}", "Tổng đơn",
                    Icons.Default.Receipt, Color(0xFFF3E8FB), Color(0xFF9B59B6))
                StatCard(Modifier.weight(1f), "${stats.outOfStock}", "Hết hàng",
                    Icons.Default.Warning, Color(0xFFFFECEC), Color(0xFFE57373))
            }
            Spacer(Modifier.height(12.dp))

            // ← MỚI: Review stats row
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard(Modifier.weight(1f), "${stats.totalReviews}", "Đánh giá",
                    Icons.Default.Star, Color(0xFFFFFBE6), Color(0xFFFFB800))
                StatCard(Modifier.weight(1f), "${stats.hiddenReviews}", "Đã ẩn",
                    Icons.Default.VisibilityOff, Color(0xFFF5F5F5), Color(0xFF9E9E9E))
            }
            Spacer(Modifier.height(12.dp))

            RevenueCard(revenue = stats.totalRevenue)

            Spacer(Modifier.height(20.dp))

            // ── Trạng thái đơn hàng ───────────────────────────────────────────
            SectionLabel("TRẠNG THÁI ĐƠN HÀNG")
            Spacer(Modifier.height(10.dp))
            Card(
                modifier  = Modifier.fillMaxWidth(),
                shape     = RoundedCornerShape(18.dp),
                colors    = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OrderStatusRow("⏳", "Chờ xác nhận", stats.pendingOrders,  Color(0xFFE8A44A), Color(0xFFFFF3E0))
                    HorizontalDivider(color = Color(0xFFEAF9F5), thickness = 0.5.dp)
                    OrderStatusRow("🚚", "Đang giao",    stats.shippingOrders, Color(0xFF9B59B6), Color(0xFFF3E8FB))
                    HorizontalDivider(color = Color(0xFFEAF9F5), thickness = 0.5.dp)
                    OrderStatusRow("🎉", "Hoàn thành",   stats.doneOrders,     MintGreen,         Color(0xFFEAF9F5))
                }
            }

            Spacer(Modifier.height(20.dp))

            // ── Menu chức năng ────────────────────────────────────────────────
            SectionLabel("CHỨC NĂNG")
            Spacer(Modifier.height(10.dp))

            DashboardCard(
                title    = "Quản lý Sản phẩm",
                subtitle = "Thêm, sửa, xoá — Kho: ${stats.totalProducts} sản phẩm",
                icon     = Icons.Default.Inventory,
                gradient = AppGradients.mintHorizontal,
                badge    = if (stats.outOfStock > 0) "${stats.outOfStock} hết hàng" else null,
                badgeRed = true,
                onClick  = { context.startActivity(Intent(context, ManageProductActivity::class.java)) }
            )
            Spacer(Modifier.height(12.dp))
            DashboardCard(
                title    = "Quản lý Thương hiệu",
                subtitle = "Đang có ${stats.totalBrands} thương hiệu",
                icon     = Icons.Default.Category,
                gradient = Brush.horizontalGradient(listOf(Color(0xFF6B89CC), Color(0xFF9BB5E8))),
                onClick  = { context.startActivity(Intent(context, ManageBrandActivity::class.java)) }
            )
            Spacer(Modifier.height(12.dp))
            DashboardCard(
                title    = "Quản lý Đơn hàng",
                subtitle = "Xử lý vận chuyển & doanh thu",
                icon     = Icons.Default.Receipt,
                gradient = Brush.horizontalGradient(listOf(Color(0xFF9B59B6), Color(0xFFBB88D4))),
                badge    = if (stats.pendingOrders > 0) "${stats.pendingOrders} chờ duyệt" else null,
                badgeRed = true,
                onClick  = { context.startActivity(Intent(context, ManageOrderActivity::class.java)) }
            )
            Spacer(Modifier.height(12.dp))

            // ← MỚI: Review management card
            DashboardCard(
                title    = "Quản lý Đánh giá",
                subtitle = "${stats.totalReviews} đánh giá  •  ${stats.hiddenReviews} đã ẩn",
                icon     = Icons.Default.RateReview,
                gradient = Brush.horizontalGradient(listOf(Color(0xFFE8A44A), Color(0xFFFFCC70))),
                onClick  = { context.startActivity(Intent(context, ManageReviewActivity::class.java)) }
            )

            Spacer(Modifier.height(32.dp))
        }
    }
}

// ── Stat Card ─────────────────────────────────────────────────────────────────
@Composable
private fun StatCard(modifier: Modifier, value: String, label: String, icon: ImageVector, iconBg: Color, iconTint: Color) {
    Card(
        modifier  = modifier, shape = RoundedCornerShape(16.dp),
        colors    = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(iconBg), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = iconTint, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text(value, color = Color(0xFF1A4A40), fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(label, color = Color(0xFF8ACABA), fontSize = 11.sp)
            }
        }
    }
}

// ── Revenue Card ──────────────────────────────────────────────────────────────
@Composable
private fun RevenueCard(revenue: Double) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(0.dp), colors = CardDefaults.cardColors(containerColor = Color.Transparent)) {
        Box(modifier = Modifier.fillMaxWidth().background(brush = AppGradients.mintHorizontal).padding(horizontal = 20.dp, vertical = 16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("Doanh thu", color = Color.White.copy(0.8f), fontSize = 12.sp)
                    Text("từ đơn hoàn thành", color = Color.White.copy(0.6f), fontSize = 10.sp)
                }
                Text("${"%,.0f".format(revenue)}đ", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
            Icon(Icons.Default.TrendingUp, null, tint = Color.White.copy(0.15f),
                modifier = Modifier.size(80.dp).align(Alignment.CenterEnd).offset(x = 10.dp))
        }
    }
}

// ── Order Status Row ──────────────────────────────────────────────────────────
@Composable
private fun OrderStatusRow(emoji: String, label: String, count: Int, color: Color, bgColor: Color) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(emoji, fontSize = 16.sp)
            Spacer(Modifier.width(10.dp))
            Text(label, color = Color(0xFF4A7A70), fontSize = 13.sp)
        }
        Box(modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(bgColor).padding(horizontal = 12.dp, vertical = 4.dp)) {
            Text("$count đơn", color = color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

// ── Dashboard Card ────────────────────────────────────────────────────────────
@Composable
fun DashboardCard(title: String, subtitle: String, icon: ImageVector, gradient: Brush, badge: String? = null, badgeRed: Boolean = false, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable { onClick() }, shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(3.dp)) {
        Row(modifier = Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)).background(gradient), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = Color.White, modifier = Modifier.size(26.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, color = Color(0xFF1A4A40), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    if (badge != null) {
                        Spacer(Modifier.width(8.dp))
                        Box(modifier = Modifier.clip(RoundedCornerShape(20.dp))
                            .background(if (badgeRed) Color(0xFFFFECEC) else Color(0xFFEAF9F5))
                            .padding(horizontal = 7.dp, vertical = 2.dp)) {
                            Text(badge, color = if (badgeRed) Color(0xFFE57373) else MintGreen,
                                fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                Spacer(Modifier.height(3.dp))
                Text(subtitle, color = Color(0xFF8ACABA), fontSize = 12.sp)
            }
            Icon(Icons.Default.ChevronRight, null, tint = Color(0xFFB2E8DA), modifier = Modifier.size(22.dp))
        }
    }
}

// ── Section Label ─────────────────────────────────────────────────────────────
@Composable
private fun SectionLabel(text: String) {
    Text(text, color = Color(0xFF8ACABA), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
}