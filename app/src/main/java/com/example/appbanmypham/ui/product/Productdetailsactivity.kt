package com.example.appbanmypham.ui.product

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.appbanmypham.model.Product
import com.example.appbanmypham.ui.auth.LoginActivity
import com.example.appbanmypham.ui.cart.CartActivity
import com.example.appbanmypham.ui.review.ProductReviewSection   // ← THÊM
import com.example.appbanmypham.ui.theme.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class ProductDetailsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val productId = intent.getStringExtra("product_id") ?: run { finish(); return }

        setContent {
            AppBanMyPhamTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = BackgroundPrimary) {
                    ProductDetailsScreen(
                        productId  = productId,
                        onBack     = { finish() },
                        onGoCart   = { startActivity(Intent(this, CartActivity::class.java)) },
                        onGoLogin  = { startActivity(Intent(this, LoginActivity::class.java)) },
                        onGoDetail = { id ->
                            val intent = Intent(this, ProductDetailsActivity::class.java)
                            intent.putExtra("product_id", id)
                            startActivity(intent)
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductDetailsScreen(
    productId  : String,
    onBack     : () -> Unit = {},
    onGoCart   : () -> Unit = {},
    onGoLogin  : () -> Unit = {},
    onGoDetail : (String) -> Unit = {}
) {
    val db   = remember { FirebaseFirestore.getInstance() }
    val auth = remember { FirebaseAuth.getInstance() }

    var currentUser by remember { mutableStateOf(auth.currentUser) }
    val isLoggedIn = currentUser != null

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { currentUser = it.currentUser }
        auth.addAuthStateListener(listener)
        onDispose { auth.removeAuthStateListener(listener) }
    }

    var product         by remember { mutableStateOf<Product?>(null) }
    var relatedProducts by remember { mutableStateOf(listOf<Product>()) }
    var isLoading       by remember { mutableStateOf(true) }
    var cartCount       by remember { mutableStateOf(0) }
    var addedToCart     by remember { mutableStateOf(false) }
    var showLoginSnack  by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(productId) {
        db.collection("products").document(productId).get()
            .addOnSuccessListener { doc ->
                val now = System.currentTimeMillis()
                product = Product(
                    id          = doc.id,
                    name        = doc.getString("name")        ?: "",
                    price       = doc.getDouble("price")       ?: 0.0,
                    stock       = (doc.getLong("stock")        ?: 0L).toInt(),
                    description = doc.getString("description") ?: "",
                    brandId     = doc.getString("brandId")     ?: "",
                    brandName   = doc.getString("brandName")   ?: "",
                    imageUrl    = doc.getString("imageUrl")    ?: "",
                    category    = doc.getString("category")    ?: "",
                    createdAt   = doc.getLong("createdAt")     ?: now
                )
                isLoading = false
            }
            .addOnFailureListener { isLoading = false }
    }

    LaunchedEffect(product?.brandId) {
        val brandId = product?.brandId ?: return@LaunchedEffect
        if (brandId.isBlank()) return@LaunchedEffect
        db.collection("products")
            .whereEqualTo("brandId", brandId)
            .get()
            .addOnSuccessListener { snap ->
                val now = System.currentTimeMillis()
                relatedProducts = snap.documents
                    .filter { it.id != productId }
                    .map { doc ->
                        Product(
                            id          = doc.id,
                            name        = doc.getString("name")        ?: "",
                            price       = doc.getDouble("price")       ?: 0.0,
                            stock       = (doc.getLong("stock")        ?: 0L).toInt(),
                            description = doc.getString("description") ?: "",
                            brandId     = doc.getString("brandId")     ?: "",
                            brandName   = doc.getString("brandName")   ?: "",
                            imageUrl    = doc.getString("imageUrl")    ?: "",
                            category    = doc.getString("category")    ?: "",
                            createdAt   = doc.getLong("createdAt")     ?: now
                        )
                    }
            }
    }

    LaunchedEffect(currentUser?.uid) {
        val uid = currentUser?.uid ?: run { cartCount = 0; return@LaunchedEffect }
        db.collection("carts").document(uid).collection("items")
            .addSnapshotListener { snap, _ -> cartCount = snap?.size() ?: 0 }
    }

    LaunchedEffect(addedToCart) {
        if (addedToCart) {
            snackbarHostState.showSnackbar("Đã thêm vào giỏ hàng ✓")
            addedToCart = false
        }
    }
    LaunchedEffect(showLoginSnack) {
        if (showLoginSnack) {
            snackbarHostState.showSnackbar("Vui lòng đăng nhập để thêm vào giỏ")
            showLoginSnack = false
        }
    }

    Scaffold(
        snackbarHost   = { SnackbarHost(snackbarHostState) },
        containerColor = BackgroundPrimary,
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(brush = AppGradients.mintHorizontal)
                    .statusBarsPadding()
                    .padding(horizontal = 4.dp, vertical = 8.dp)
            ) {
                IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart)) {
                    Icon(Icons.Default.ArrowBackIosNew, contentDescription = "Quay lại", tint = Color.White)
                }
                Text(
                    "Chi tiết sản phẩm",
                    modifier      = Modifier.align(Alignment.Center),
                    color         = Color.White,
                    fontSize      = 16.sp,
                    fontWeight    = FontWeight.SemiBold,
                    letterSpacing = 0.3.sp
                )
                Box(modifier = Modifier.align(Alignment.CenterEnd)) {
                    IconButton(onClick = { if (isLoggedIn) onGoCart() else onGoLogin() }) {
                        Icon(Icons.Default.ShoppingCart, contentDescription = null, tint = Color.White)
                    }
                    if (isLoggedIn && cartCount > 0) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = (-2).dp, y = 4.dp)
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFFF6B6B)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                if (cartCount > 9) "9+" else cartCount.toString(),
                                color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        },
        bottomBar = {
            product?.let { p ->
                Surface(shadowElevation = 8.dp, color = Color.White) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Giá", color = Color(0xFF8ACABA), fontSize = 11.sp)
                            Text(
                                "${"%,.0f".format(p.price)}đ",
                                color = Color(0xFF1A4A40), fontWeight = FontWeight.Bold, fontSize = 20.sp
                            )
                        }
                        Button(
                            onClick = {
                                if (!isLoggedIn) { showLoginSnack = true; return@Button }
                                if (p.stock == 0) return@Button
                                addToCart(db, auth, p)
                                addedToCart = true
                            },
                            enabled  = p.stock > 0,
                            modifier = Modifier.height(50.dp).weight(1.5f),
                            shape    = RoundedCornerShape(14.dp),
                            colors   = ButtonDefaults.buttonColors(
                                containerColor         = MintGreen,
                                disabledContainerColor = Color(0xFFCCCCCC)
                            )
                        ) {
                            Icon(
                                if (p.stock > 0) Icons.Default.AddShoppingCart else Icons.Default.RemoveShoppingCart,
                                contentDescription = null, modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (p.stock > 0) "Thêm vào giỏ" else "Hết hàng",
                                fontWeight = FontWeight.SemiBold, fontSize = 14.sp
                            )
                        }
                    }
                }
            }
        }
    ) { padding ->
        when {
            isLoading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MintGreen)
            }
            product == null -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("😕", fontSize = 48.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("Không tìm thấy sản phẩm", color = Color(0xFF8ACABA))
                }
            }
            else -> {
                val p            = product!!
                val isNew        = (System.currentTimeMillis() - p.createdAt) < 7 * 24 * 60 * 60 * 1000L
                val isOutOfStock = p.stock == 0

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                ) {
                    // ── Ảnh sản phẩm ──────────────────────────────────────────
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp)
                            .background(Color(0xFFEAF9F5)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (p.imageUrl.isNotEmpty()) {
                            AsyncImage(
                                model              = p.imageUrl,
                                contentDescription = p.name,
                                contentScale       = ContentScale.Fit,
                                modifier           = Modifier.fillMaxSize().padding(16.dp),
                                alpha              = if (isOutOfStock) 0.5f else 1f
                            )
                        } else {
                            Text("🌿", fontSize = 80.sp)
                        }
                        Row(
                            modifier = Modifier.align(Alignment.TopStart).padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (isNew && !isOutOfStock) {
                                Box(modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(brush = AppGradients.mintHorizontal)
                                    .padding(horizontal = 10.dp, vertical = 4.dp)) {
                                    Text("MỚI", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                                }
                            }
                            if (isOutOfStock) {
                                Box(modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFFE0E0E0))
                                    .padding(horizontal = 10.dp, vertical = 4.dp)) {
                                    Text("Hết hàng", color = Color(0xFF9E9E9E), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        if (p.stock in 1..5) {
                            Box(modifier = Modifier.align(Alignment.TopEnd).padding(12.dp)
                                .clip(RoundedCornerShape(8.dp)).background(Color(0xFFFFF3E0))
                                .padding(horizontal = 10.dp, vertical = 4.dp)) {
                                Text("Còn ${p.stock} sản phẩm", color = Color(0xFFE8A44A), fontSize = 11.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }

                    // ── Thông tin chính ─────────────────────────────────────────
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.White)
                            .padding(horizontal = 20.dp, vertical = 16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(Color(0xFFEAF9F5))
                                .padding(horizontal = 12.dp, vertical = 4.dp)) {
                                Text(p.brandName, color = MintGreen, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                            if (p.category.isNotEmpty()) {
                                Text(p.category, color = Color(0xFFAAD8CE), fontSize = 12.sp)
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(
                            p.name,
                            color      = Color(0xFF1A4A40),
                            fontSize   = 20.sp,
                            fontWeight = FontWeight.Bold,
                            lineHeight = 28.sp
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier.size(8.dp).clip(CircleShape)
                                    .background(if (isOutOfStock) Color(0xFFE0E0E0) else Color(0xFF4CAF50))
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                if (isOutOfStock) "Hết hàng" else "Còn hàng (${p.stock} sản phẩm)",
                                color    = if (isOutOfStock) Color(0xFF9E9E9E) else Color(0xFF4CAF50),
                                fontSize = 13.sp
                            )
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    // ── Mô tả sản phẩm ─────────────────────────────────────────
                    if (p.description.isNotBlank()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.White)
                                .padding(horizontal = 20.dp, vertical = 16.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier
                                    .width(4.dp).height(18.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(MintGreen))
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "Mô tả sản phẩm",
                                    color      = Color(0xFF1A4A40),
                                    fontSize   = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(Modifier.height(12.dp))
                            Text(
                                p.description,
                                color      = Color(0xFF4A7A70),
                                fontSize   = 14.sp,
                                lineHeight = 22.sp
                            )
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    // ── ĐÁnh giá sản phẩm ← MỚI ──────────────────────────────
                    ProductReviewSection(productId = p.id)

                    Spacer(Modifier.height(8.dp))

                    // ── Sản phẩm liên quan ─────────────────────────────────────
                    if (relatedProducts.isNotEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.White)
                                .padding(vertical = 16.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 20.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(modifier = Modifier
                                    .width(4.dp).height(18.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(MintGreen))
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "Cùng thương hiệu ${p.brandName}",
                                    color      = Color(0xFF1A4A40),
                                    fontSize   = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(Modifier.height(12.dp))
                            LazyRow(
                                contentPadding        = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(relatedProducts, key = { it.id }) { related ->
                                    RelatedProductCard(
                                        product = related,
                                        onClick = { onGoDetail(related.id) }
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }
}

// ── Related Product Card ──────────────────────────────────────────────────────
@Composable
private fun RelatedProductCard(product: Product, onClick: () -> Unit) {
    val isOutOfStock = product.stock == 0
    Card(
        modifier  = Modifier.width(140.dp).clickable { onClick() },
        shape     = RoundedCornerShape(14.dp),
        colors    = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth().height(120.dp)
                    .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
                    .background(Color(0xFFEAF9F5)),
                contentAlignment = Alignment.Center
            ) {
                if (product.imageUrl.isNotEmpty()) {
                    AsyncImage(
                        model = product.imageUrl, contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                        alpha = if (isOutOfStock) 0.5f else 1f
                    )
                } else { Text("🌿", fontSize = 36.sp) }
                if (isOutOfStock) {
                    Box(modifier = Modifier.align(Alignment.TopStart)
                        .clip(RoundedCornerShape(bottomEnd = 6.dp))
                        .background(Color(0xFFE0E0E0))
                        .padding(horizontal = 4.dp, vertical = 2.dp)) {
                        Text("Hết hàng", color = Color(0xFF9E9E9E), fontSize = 8.sp)
                    }
                }
            }
            Column(modifier = Modifier.padding(8.dp)) {
                Text(
                    product.name,
                    color      = if (isOutOfStock) Color(0xFFAAAAAA) else Color(0xFF1A4A40),
                    fontWeight = FontWeight.SemiBold, fontSize = 12.sp,
                    maxLines   = 2, overflow = TextOverflow.Ellipsis, lineHeight = 16.sp
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "${"%,.0f".format(product.price)}đ",
                    color      = if (isOutOfStock) Color(0xFFAAAAAA) else MintGreen,
                    fontWeight = FontWeight.Bold, fontSize = 12.sp
                )
            }
        }
    }
}

private fun addToCart(db: FirebaseFirestore, auth: FirebaseAuth, product: Product) {
    val uid     = auth.currentUser?.uid ?: return
    val cartRef = db.collection("carts").document(uid).collection("items").document(product.id)
    db.runTransaction { transaction ->
        val snap = transaction.get(cartRef)
        val qty  = (snap.getLong("quantity") ?: 0L).toInt()
        transaction.set(cartRef, hashMapOf(
            "productId" to product.id,
            "name"      to product.name,
            "price"     to product.price,
            "imageUrl"  to product.imageUrl,
            "brandName" to product.brandName,
            "quantity"  to qty + 1,
            "updatedAt" to System.currentTimeMillis()
        ))
    }
}