package com.example.appbanmypham.ui.cart

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.appbanmypham.ui.checkout.CheckoutActivity  // ✅ import CheckoutActivity
import com.example.appbanmypham.ui.theme.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class CartActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppBanMyPhamTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = BackgroundPrimary) {
                    CartScreen(
                        onBack = { finish() },
                        onCheckout = { items, totalPrice ->  // ✅ Fix: truyền items & total
                            val intent = Intent(this, CheckoutActivity::class.java).apply {
                                putExtra("total_price",     totalPrice)
                                putExtra("item_ids",        items.map { it.productId }.toTypedArray())
                                putExtra("item_names",      items.map { it.name }.toTypedArray())
                                putExtra("item_brands",     items.map { it.brandName }.toTypedArray())
                                putExtra("item_images",     items.map { it.imageUrl }.toTypedArray())
                                putExtra("item_prices",     items.map { it.price }.toDoubleArray())
                                putExtra("item_quantities", items.map { it.quantity }.toIntArray())
                            }
                            startActivity(intent)
                        }
                    )
                }
            }
        }
    }
}

data class CartItem(
    val productId: String = "",
    val name: String = "",
    val price: Double = 0.0,
    val imageUrl: String = "",
    val brandName: String = "",
    var quantity: Int = 1
)

@Composable
fun CartScreen(
    onBack: () -> Unit = {},
    onCheckout: (List<CartItem>, Double) -> Unit = { _, _ -> }  // ✅ Fix: thêm params
) {
    val context = LocalContext.current
    val db      = remember { FirebaseFirestore.getInstance() }
    val auth    = remember { FirebaseAuth.getInstance() }
    val uid     = auth.currentUser?.uid ?: ""

    var items            by remember { mutableStateOf(listOf<CartItem>()) }
    var isLoading        by remember { mutableStateOf(true) }
    var showClearConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(uid) {
        if (uid.isEmpty()) { isLoading = false; return@LaunchedEffect }
        db.collection("carts").document(uid).collection("items")
            .addSnapshotListener { snap, _ ->
                items = snap?.documents?.map { doc ->
                    CartItem(
                        productId = doc.getString("productId") ?: doc.id,
                        name      = doc.getString("name")      ?: "",
                        price     = doc.getDouble("price")     ?: 0.0,
                        imageUrl  = doc.getString("imageUrl")  ?: "",
                        brandName = doc.getString("brandName") ?: "",
                        quantity  = (doc.getLong("quantity")   ?: 1L).toInt()
                    )
                } ?: emptyList()
                isLoading = false
            }
    }

    val totalPrice = items.sumOf { it.price * it.quantity }
    val totalItems = items.sumOf { it.quantity }

    fun updateQty(item: CartItem, delta: Int) {
        val newQty = item.quantity + delta
        val ref = db.collection("carts").document(uid)
            .collection("items").document(item.productId)
        if (newQty <= 0) ref.delete() else ref.update("quantity", newQty)
    }

    fun removeItem(item: CartItem) {
        db.collection("carts").document(uid)
            .collection("items").document(item.productId).delete()
    }

    fun clearCart() { items.forEach { removeItem(it) } }

    Scaffold(
        containerColor = BackgroundPrimary,
        bottomBar = {
            if (items.isNotEmpty()) {
                CheckoutBar(
                    totalPrice = totalPrice,
                    totalItems = totalItems,
                    onCheckout = { onCheckout(items, totalPrice) }  // ✅ Fix: truyền đúng data
                )
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // ── Header ──
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .background(brush = AppGradients.mintHorizontal)
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(36.dp).clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.25f))
                        .align(Alignment.TopStart)
                ) {
                    Icon(Icons.Default.ArrowBack, contentDescription = null, tint = Color.White)
                }

                if (items.isNotEmpty()) {
                    IconButton(
                        onClick = { showClearConfirm = true },
                        modifier = Modifier
                            .size(36.dp).clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.2f))
                            .align(Alignment.TopEnd)
                    ) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = "Xoá tất cả", tint = Color.White)
                    }
                }

                Column(modifier = Modifier.align(Alignment.BottomStart)) {
                    Text("GIỎ HÀNG", color = Color.White.copy(0.75f), fontSize = 11.sp, letterSpacing = 2.sp)
                    Text("của bạn", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    if (totalItems > 0) {
                        Text("$totalItems sản phẩm", color = Color.White.copy(0.75f), fontSize = 13.sp)
                    }
                }
            }

            // ── Content ──
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(y = (-20).dp)
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .background(BackgroundPrimary)
            ) {
                when {
                    isLoading -> Box(
                        modifier = Modifier.fillMaxWidth().padding(top = 80.dp),
                        contentAlignment = Alignment.Center
                    ) { CircularProgressIndicator(color = MintGreen) }

                    items.isEmpty() -> Column(
                        modifier = Modifier.fillMaxWidth().padding(top = 80.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("🛒", fontSize = 56.sp)
                        Spacer(Modifier.height(12.dp))
                        Text("Giỏ hàng trống", color = Color(0xFF8ACABA), fontSize = 17.sp, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(6.dp))
                        Text("Hãy thêm sản phẩm yêu thích!", color = Color(0xFFAAD8CE), fontSize = 13.sp)
                        Spacer(Modifier.height(24.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(brush = AppGradients.mintHorizontal)
                                .clickable { onBack() }
                                .padding(horizontal = 28.dp, vertical = 12.dp)
                        ) {
                            Text("Tiếp tục mua sắm", color = Color.White, fontWeight = FontWeight.Medium)
                        }
                    }

                    else -> LazyColumn(
                        contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 120.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(items, key = { it.productId }) { item ->
                            CartItemCard(
                                item       = item,
                                onIncrease = { updateQty(item, +1) },
                                onDecrease = { updateQty(item, -1) },
                                onRemove   = { removeItem(item) }
                            )
                        }
                        item {
                            Spacer(Modifier.height(8.dp))
                            PriceSummaryCard(items = items, totalPrice = totalPrice)
                        }
                    }
                }
            }
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            containerColor   = Color.White,
            title  = { Text("Xoá giỏ hàng", color = Color(0xFF1A4A40), fontWeight = FontWeight.Bold) },
            text   = { Text("Bạn có chắc muốn xoá tất cả ${items.size} sản phẩm?", color = Color(0xFF4A7A70)) },
            confirmButton = {
                TextButton(onClick = { clearCart(); showClearConfirm = false }) {
                    Text("Xoá tất cả", color = Color(0xFFE57373), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text("Huỷ", color = MintGreen)
                }
            }
        )
    }
}

// ── Cart Item Card ────────────────────────────────────────────────────────────
@Composable
private fun CartItemCard(
    item: CartItem,
    onIncrease: () -> Unit,
    onDecrease: () -> Unit,
    onRemove: () -> Unit
) {
    var showRemoveConfirm by remember { mutableStateOf(false) }

    Card(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(18.dp),
        colors    = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(76.dp).clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFEAF9F5)),
                contentAlignment = Alignment.Center
            ) {
                if (item.imageUrl.isNotEmpty()) {
                    AsyncImage(
                        model            = item.imageUrl,
                        contentDescription = null,
                        contentScale     = ContentScale.Crop,
                        modifier         = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp))
                    )
                } else { Text("🌿", fontSize = 30.sp) }
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(item.brandName, color = MintGreen, fontSize = 10.sp, fontWeight = FontWeight.Medium)
                        Text(
                            item.name,
                            color      = Color(0xFF1A4A40),
                            fontWeight = FontWeight.SemiBold,
                            fontSize   = 13.sp,
                            maxLines   = 2,
                            overflow   = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(onClick = { showRemoveConfirm = true }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Xoá", tint = Color(0xFFAAD8CE), modifier = Modifier.size(16.dp))
                    }
                }

                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${"%,.0f".format(item.price * item.quantity)}đ",
                        color = Color(0xFF1A4A40), fontWeight = FontWeight.Bold, fontSize = 14.sp
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clip(RoundedCornerShape(10.dp))
                            .border(1.dp, Color(0xFFB2E8DA), RoundedCornerShape(10.dp))
                    ) {
                        Box(
                            modifier = Modifier.size(32.dp).clickable { onDecrease() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                if (item.quantity == 1) Icons.Default.DeleteOutline else Icons.Default.Remove,
                                contentDescription = null,
                                tint = if (item.quantity == 1) Color(0xFFE57373) else MintGreen,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Text(
                            "${item.quantity}",
                            color = Color(0xFF1A4A40), fontWeight = FontWeight.Bold, fontSize = 14.sp,
                            modifier = Modifier.padding(horizontal = 10.dp)
                        )
                        Box(
                            modifier = Modifier.size(32.dp).clickable { onIncrease() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, tint = MintGreen, modifier = Modifier.size(16.dp))
                        }
                    }
                }

                if (item.quantity > 1) {
                    Text(
                        "${"%,.0f".format(item.price)}đ / sản phẩm",
                        color = Color(0xFFAAD8CE), fontSize = 10.sp,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }
    }

    if (showRemoveConfirm) {
        AlertDialog(
            onDismissRequest = { showRemoveConfirm = false },
            containerColor   = Color.White,
            title  = { Text("Xoá sản phẩm", color = Color(0xFF1A4A40), fontWeight = FontWeight.Bold) },
            text   = { Text("Xoá \"${item.name}\" khỏi giỏ hàng?", color = Color(0xFF4A7A70)) },
            confirmButton = {
                TextButton(onClick = { onRemove(); showRemoveConfirm = false }) {
                    Text("Xoá", color = Color(0xFFE57373), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveConfirm = false }) {
                    Text("Huỷ", color = MintGreen)
                }
            }
        )
    }
}

// ── Price Summary ─────────────────────────────────────────────────────────────
@Composable
private fun PriceSummaryCard(items: List<CartItem>, totalPrice: Double) {
    Card(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(18.dp),
        colors    = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Tổng đơn hàng", color = MintGreen, fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.sp)
            Spacer(Modifier.height(12.dp))
            items.forEach { item ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "${item.name} x${item.quantity}",
                        color = Color(0xFF4A7A70), fontSize = 12.sp,
                        modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "${"%,.0f".format(item.price * item.quantity)}đ",
                        color = Color(0xFF1A4A40), fontSize = 12.sp, fontWeight = FontWeight.Medium
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Divider(color = Color(0xFFEAF9F5), thickness = 1.dp)
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Tổng cộng", color = Color(0xFF1A4A40), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text("${"%,.0f".format(totalPrice)}đ", color = MintGreen, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
        }
    }
}

// ── Checkout Bar ──────────────────────────────────────────────────────────────
@Composable
private fun CheckoutBar(
    totalPrice: Double,
    totalItems: Int,
    onCheckout: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("$totalItems sản phẩm", color = Color(0xFF8ACABA), fontSize = 11.sp)
                Text("${"%,.0f".format(totalPrice)}đ", color = Color(0xFF1A4A40), fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
            Box(
                modifier = Modifier
                    .height(48.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(brush = AppGradients.mintHorizontal)
                    .clickable { onCheckout() }
                    .padding(horizontal = 28.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.ShoppingCartCheckout, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Đặt hàng", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                }
            }
        }
    }
}