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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.appbanmypham.model.Product
import com.example.appbanmypham.ui.auth.LoginActivity
import com.example.appbanmypham.ui.cart.CartActivity
import com.example.appbanmypham.ui.order.OrderScreen          // ← import OrderScreen
import com.example.appbanmypham.ui.theme.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class ProductActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppBanMyPhamTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = BackgroundPrimary) {
                    ProductScreen(
                        onGoCart   = { startActivity(Intent(this, CartActivity::class.java)) },
                        onGoLogin  = { startActivity(Intent(this, LoginActivity::class.java)) },
                        onGoDetail = { product ->
                            val intent = Intent(this, ProductDetailsActivity::class.java)
                            intent.putExtra("product_id", product.id)
                            startActivity(intent)
                        }
                    )
                }
            }
        }
    }
}

enum class BottomTab { HOME, ORDERS, ACCOUNT }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductScreen(
    onGoCart   : () -> Unit = {},
    onGoLogin  : () -> Unit = {},
    onGoDetail : (Product) -> Unit = {}
) {
    val db   = remember { FirebaseFirestore.getInstance() }
    val auth = remember { FirebaseAuth.getInstance() }

    var currentUser by remember { mutableStateOf(auth.currentUser) }
    var userRole    by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { fa ->
            currentUser = fa.currentUser
            if (fa.currentUser == null) userRole = null
        }
        auth.addAuthStateListener(listener)
        onDispose { auth.removeAuthStateListener(listener) }
    }

    LaunchedEffect(currentUser?.uid) {
        val uid = currentUser?.uid ?: run { userRole = null; return@LaunchedEffect }
        db.collection("users").document(uid).get()
            .addOnSuccessListener { doc ->
                userRole = when (doc.getLong("role")?.toInt()) {
                    1    -> "admin"
                    else -> "user"
                }
            }
            .addOnFailureListener { userRole = "user" }
    }

    val isLoggedIn = currentUser != null

    var products    by remember { mutableStateOf(listOf<Product>()) }
    var categories  by remember { mutableStateOf(listOf<String>()) }
    var isLoading   by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedCat by remember { mutableStateOf("Tất cả") }
    var cartCount   by remember { mutableStateOf(0) }
    var isGridView  by remember { mutableStateOf(true) }
    var showSearch  by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf(BottomTab.HOME) }

    var showAccountDialog by remember { mutableStateOf(false) }
    var showLogoutConfirm by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        var registration: ListenerRegistration? = null
        registration = db.collection("products")
            .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .addSnapshotListener { snap, error ->
                if (error != null) return@addSnapshotListener
                val now  = System.currentTimeMillis()
                val list = snap?.documents?.mapNotNull { doc ->
                    runCatching {
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
                    }.getOrNull()
                } ?: emptyList()
                products   = list
                categories = listOf("Tất cả") +
                        list.map { it.category }.filter { it.isNotEmpty() }.distinct()
                isLoading  = false
            }
        onDispose { registration?.remove() }
    }

    DisposableEffect(currentUser?.uid) {
        val uid = currentUser?.uid ?: run {
            cartCount = 0
            return@DisposableEffect onDispose {}
        }
        val registration = db.collection("carts").document(uid).collection("items")
            .addSnapshotListener { snap, _ -> cartCount = snap?.size() ?: 0 }
        onDispose { registration.remove() }
    }

    val filtered = products.filter { p ->
        val matchCat    = selectedCat == "Tất cả" || p.category == selectedCat
        val matchSearch = searchQuery.isBlank() ||
                p.name.contains(searchQuery, ignoreCase = true) ||
                p.brandName.contains(searchQuery, ignoreCase = true)
        matchCat && matchSearch
    }

    // ── Account Dialog ────────────────────────────────────────────────────────
    if (showAccountDialog && isLoggedIn) {
        AlertDialog(
            onDismissRequest = { showAccountDialog = false },
            containerColor   = Color.White,
            shape            = RoundedCornerShape(20.dp),
            title = {
                Text("Tài khoản", fontWeight = FontWeight.Bold, color = Color(0xFF1A4A40), fontSize = 18.sp)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(brush = AppGradients.mintHorizontal)
                            .align(Alignment.CenterHorizontally),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = (currentUser?.displayName?.firstOrNull()?.toString()
                                ?: currentUser?.email?.firstOrNull()?.toString()
                                ?: "U").uppercase(),
                            color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    AccountInfoRow("Tên",   currentUser?.displayName ?: "Chưa cập nhật")
                    AccountInfoRow("Email", currentUser?.email       ?: "Chưa cập nhật")
                    AccountInfoRow("Role",  if (userRole == "admin") "Quản trị viên" else "Người dùng")
                    HorizontalDivider(color = Color(0xFFEAF9F5))
                    TextButton(
                        onClick = { showAccountDialog = false; showLogoutConfirm = true },
                        colors  = ButtonDefaults.textButtonColors(contentColor = Color(0xFFE57373))
                    ) {
                        Icon(Icons.Default.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Đăng xuất", fontWeight = FontWeight.SemiBold)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAccountDialog = false }) {
                    Text("Đóng", color = MintGreen, fontWeight = FontWeight.SemiBold)
                }
            }
        )
    }

    // ── Logout Confirm Dialog ─────────────────────────────────────────────────
    if (showLogoutConfirm) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirm = false },
            containerColor   = Color.White,
            shape            = RoundedCornerShape(20.dp),
            title = { Text("Đăng xuất?", fontWeight = FontWeight.Bold, color = Color(0xFF1A4A40)) },
            text  = { Text("Bạn có chắc muốn đăng xuất?", color = Color(0xFF5A8A80)) },
            confirmButton = {
                Button(
                    onClick = {
                        showLogoutConfirm = false
                        auth.signOut()
                        selectedTab = BottomTab.HOME
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE57373))
                ) { Text("Đăng xuất", color = Color.White) }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutConfirm = false }) {
                    Text("Hủy", color = MintGreen)
                }
            }
        )
    }

    // ── Scaffold ──────────────────────────────────────────────────────────────
    Scaffold(
        containerColor = BackgroundPrimary,
        topBar = {
            // ── TopBar chỉ hiện khi ở tab HOME (Orders & Account tự quản lý header) ──
            if (selectedTab == BottomTab.HOME) {
                Column {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(brush = AppGradients.mintHorizontal)
                            .statusBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        if (!showSearch) {
                            Row(
                                modifier          = Modifier.align(Alignment.CenterStart),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color.White.copy(alpha = 0.3f)),
                                    contentAlignment = Alignment.Center
                                ) { Text("🌿", fontSize = 16.sp) }
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Text(
                                        "LUMIÈRE",
                                        color         = Color.White,
                                        fontSize      = 14.sp,
                                        fontWeight    = FontWeight.Bold,
                                        letterSpacing = 1.5.sp
                                    )
                                    Text(
                                        "Beauty Store",
                                        color     = Color.White.copy(0.75f),
                                        fontSize  = 9.sp,
                                        fontStyle = FontStyle.Italic
                                    )
                                }
                            }
                        } else {
                            OutlinedTextField(
                                value         = searchQuery,
                                onValueChange = { searchQuery = it },
                                modifier      = Modifier
                                    .fillMaxWidth()
                                    .padding(end = 50.dp),
                                placeholder   = { Text("Tìm kiếm...", color = Color.White.copy(0.7f)) },
                                singleLine    = true,
                                colors        = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor   = Color.White,
                                    unfocusedBorderColor = Color.White.copy(0.5f),
                                    focusedTextColor     = Color.White,
                                    unfocusedTextColor   = Color.White,
                                    cursorColor          = Color.White
                                ),
                                shape = RoundedCornerShape(12.dp)
                            )
                        }

                        Row(
                            modifier              = Modifier.align(Alignment.CenterEnd),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            IconButton(onClick = {
                                showSearch = !showSearch
                                if (!showSearch) searchQuery = ""
                            }) {
                                Icon(
                                    if (showSearch) Icons.Default.Close else Icons.Default.Search,
                                    contentDescription = null, tint = Color.White
                                )
                            }
                            Box {
                                IconButton(onClick = {
                                    if (isLoggedIn) onGoCart() else onGoLogin()
                                }) {
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
                            if (isLoggedIn) {
                                IconButton(onClick = { showAccountDialog = true }) {
                                    Icon(Icons.Default.AccountCircle, contentDescription = "Tài khoản", tint = Color.White)
                                }
                            } else {
                                IconButton(onClick = onGoLogin) {
                                    Icon(Icons.Default.Login, contentDescription = "Đăng nhập", tint = Color.White)
                                }
                            }
                        }
                    }

                    if (categories.size > 1) {
                        LazyRow(
                            modifier              = Modifier
                                .background(Color.White)
                                .padding(vertical = 10.dp),
                            contentPadding        = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(categories) { cat ->
                                val isSelected = cat == selectedCat
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(
                                            brush = if (isSelected) AppGradients.mintHorizontal
                                            else androidx.compose.ui.graphics.Brush.horizontalGradient(
                                                listOf(Color(0xFFEAF9F5), Color(0xFFEAF9F5))
                                            )
                                        )
                                        .clickable { selectedCat = cat }
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        cat,
                                        color      = if (isSelected) Color.White else MintGreen,
                                        fontSize   = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }
                }
            }
            // Orders & Account tab tự quản lý header của mình → không render gì ở đây
        },
        bottomBar = {
            NavigationBar(containerColor = Color.White, tonalElevation = 8.dp) {
                BottomNavItem(
                    icon     = Icons.Default.Home,
                    label    = "Trang chủ",
                    selected = selectedTab == BottomTab.HOME,
                    onClick  = { selectedTab = BottomTab.HOME }
                )
                BottomNavItem(
                    icon     = Icons.Default.Receipt,
                    label    = "Đơn hàng",
                    selected = selectedTab == BottomTab.ORDERS,
                    onClick  = { if (isLoggedIn) selectedTab = BottomTab.ORDERS else onGoLogin() }
                )
                BottomNavItem(
                    icon     = Icons.Default.Person,
                    label    = "Tài khoản",
                    selected = selectedTab == BottomTab.ACCOUNT,
                    onClick  = { if (isLoggedIn) selectedTab = BottomTab.ACCOUNT else onGoLogin() }
                )
            }
        }
    ) { padding ->
        when (selectedTab) {
            BottomTab.HOME -> HomeTabContent(
                padding      = padding,
                isLoading    = isLoading,
                filtered     = filtered,
                isGridView   = isGridView,
                onToggleView = { isGridView = it },
                db           = db,
                auth         = auth,
                isLoggedIn   = isLoggedIn,
                onGoLogin    = onGoLogin,
                onGoDetail   = onGoDetail
            )


            BottomTab.ORDERS -> Box(modifier = Modifier.padding(padding)) {
                OrderScreen(
                    onBack = { selectedTab = BottomTab.HOME }  // nút back → về Home
                )
            }

            BottomTab.ACCOUNT -> AccountTabContent(
                padding  = padding,
                auth     = auth,
                userRole = userRole,
                onLogout = { showLogoutConfirm = true }
            )
        }
    }
}

// ── Bottom Nav Item ───────────────────────────────────────────────────────────
@Composable
private fun RowScope.BottomNavItem(
    icon     : ImageVector,
    label    : String,
    selected : Boolean,
    onClick  : () -> Unit
) {
    NavigationBarItem(
        selected = selected,
        onClick  = onClick,
        icon     = { Icon(icon, contentDescription = label, modifier = Modifier.size(24.dp)) },
        label    = {
            Text(label, fontSize = 11.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
        },
        colors = NavigationBarItemDefaults.colors(
            selectedIconColor   = MintGreen,
            selectedTextColor   = MintGreen,
            unselectedIconColor = Color(0xFFAAD8CE),
            unselectedTextColor = Color(0xFFAAD8CE),
            indicatorColor      = Color(0xFFEAF9F5)
        )
    )
}

// ── HOME Tab ──────────────────────────────────────────────────────────────────
@Composable
private fun HomeTabContent(
    padding      : PaddingValues,
    isLoading    : Boolean,
    filtered     : List<Product>,
    isGridView   : Boolean,
    onToggleView : (Boolean) -> Unit,
    db           : FirebaseFirestore,
    auth         : FirebaseAuth,
    isLoggedIn   : Boolean,
    onGoLogin    : () -> Unit,
    onGoDetail   : (Product) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(padding)) {
        Row(
            modifier              = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Text("${filtered.size} sản phẩm", color = Color(0xFF8ACABA), fontSize = 13.sp)
            Row(modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Color(0xFFEAF9F5))) {
                IconButton(
                    onClick  = { onToggleView(true) },
                    modifier = Modifier.size(36.dp).clip(RoundedCornerShape(8.dp))
                        .background(if (isGridView) MintGreen else Color.Transparent)
                ) {
                    Icon(Icons.Default.GridView, null,
                        tint = if (isGridView) Color.White else MintGreen, modifier = Modifier.size(18.dp))
                }
                IconButton(
                    onClick  = { onToggleView(false) },
                    modifier = Modifier.size(36.dp).clip(RoundedCornerShape(8.dp))
                        .background(if (!isGridView) MintGreen else Color.Transparent)
                ) {
                    Icon(Icons.Default.ViewList, null,
                        tint = if (!isGridView) Color.White else MintGreen, modifier = Modifier.size(18.dp))
                }
            }
        }

        when {
            isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MintGreen)
            }
            filtered.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🔍", fontSize = 48.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("Không tìm thấy sản phẩm", color = Color(0xFF8ACABA), fontSize = 15.sp)
                }
            }
            isGridView -> LazyVerticalGrid(
                columns               = GridCells.Fixed(2),
                modifier              = Modifier.fillMaxSize(),
                contentPadding        = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement   = Arrangement.spacedBy(10.dp)
            ) {
                items(filtered, key = { it.id }) { product ->
                    ProductGridCard(
                        product     = product,
                        isLoggedIn  = isLoggedIn,
                        onAddToCart = { if (isLoggedIn) addToCart(db, auth, it) else onGoLogin() },
                        onCardClick = { onGoDetail(product) }
                    )
                }
                item { Spacer(Modifier.height(16.dp)) }
                item { Spacer(Modifier.height(16.dp)) }
            }
            else -> androidx.compose.foundation.lazy.LazyColumn(
                modifier            = Modifier.fillMaxSize(),
                contentPadding      = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filtered, key = { it.id }) { product ->
                    ProductListCard(
                        product     = product,
                        isLoggedIn  = isLoggedIn,
                        onAddToCart = { if (isLoggedIn) addToCart(db, auth, it) else onGoLogin() },
                        onCardClick = { onGoDetail(product) }
                    )
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}

// ── ACCOUNT Tab ───────────────────────────────────────────────────────────────
@Composable
private fun AccountTabContent(
    padding  : PaddingValues,
    auth     : FirebaseAuth,
    userRole : String?,
    onLogout : () -> Unit
) {
    val user = auth.currentUser
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(32.dp))
        Box(
            modifier = Modifier
                .size(90.dp)
                .clip(CircleShape)
                .background(brush = AppGradients.mintHorizontal),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = (user?.displayName?.firstOrNull()?.toString()
                    ?: user?.email?.firstOrNull()?.toString() ?: "U").uppercase(),
                color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            user?.displayName ?: "Người dùng",
            color = Color(0xFF1A4A40), fontSize = 20.sp, fontWeight = FontWeight.Bold
        )
        Text(user?.email ?: "", color = Color(0xFF8ACABA), fontSize = 13.sp)
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(if (userRole == "admin") Color(0xFFEAF9F5) else Color(0xFFF0F0F0))
                .padding(horizontal = 14.dp, vertical = 4.dp)
        ) {
            Text(
                if (userRole == "admin") "⭐ Quản trị viên" else "👤 Người dùng",
                color      = if (userRole == "admin") MintGreen else Color(0xFF888888),
                fontSize   = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
        Spacer(Modifier.height(24.dp))
        Card(
            modifier  = Modifier.fillMaxWidth(),
            shape     = RoundedCornerShape(16.dp),
            colors    = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(2.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    "Thông tin tài khoản",
                    color      = MintGreen,
                    fontSize   = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.5.sp
                )
                AccountInfoRow("Họ và tên",   user?.displayName                  ?: "Chưa cập nhật")
                HorizontalDivider(color = Color(0xFFEAF9F5), thickness = 0.5.dp)
                AccountInfoRow("Email",        user?.email                       ?: "Chưa cập nhật")
                HorizontalDivider(color = Color(0xFFEAF9F5), thickness = 0.5.dp)
                AccountInfoRow("ID tài khoản", user?.uid?.take(14)?.plus("...") ?: "-")
            }
        }
        Spacer(Modifier.height(24.dp))
        Button(
            onClick  = onLogout,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape    = RoundedCornerShape(14.dp),
            colors   = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFECEC))
        ) {
            Icon(Icons.Default.Logout, null, tint = Color(0xFFE57373), modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Đăng xuất", color = Color(0xFFE57373), fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        }
    }
}

// ── Account Info Row ──────────────────────────────────────────────────────────
@Composable
private fun AccountInfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Color(0xFF8ACABA), fontSize = 13.sp)
        Text(value, color = Color(0xFF1A4A40), fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

// ── Grid Card ─────────────────────────────────────────────────────────────────
@Composable
private fun ProductGridCard(
    product     : Product,
    isLoggedIn  : Boolean,
    onAddToCart : (Product) -> Unit,
    onCardClick : () -> Unit
) {
    val isNew        = remember(product.createdAt) {
        (System.currentTimeMillis() - product.createdAt) < 7 * 24 * 60 * 60 * 1000L
    }
    val isOutOfStock = product.stock == 0

    Card(
        modifier  = Modifier.fillMaxWidth().clickable { onCardClick() },
        shape     = RoundedCornerShape(16.dp),
        colors    = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth().height(150.dp)
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                    .background(Color(0xFFEAF9F5)),
                contentAlignment = Alignment.Center
            ) {
                if (product.imageUrl.isNotEmpty()) {
                    AsyncImage(
                        model              = product.imageUrl,
                        contentDescription = null,
                        contentScale       = ContentScale.Crop,
                        modifier           = Modifier.fillMaxSize(),
                        alpha              = if (isOutOfStock) 0.5f else 1f
                    )
                } else { Text("🌿", fontSize = 40.sp) }

                if (isNew && !isOutOfStock) {
                    Box(
                        modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(brush = AppGradients.mintHorizontal)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text("MỚI", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    }
                }
                if (isOutOfStock) {
                    Box(
                        modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFFE0E0E0))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text("Hết hàng", color = Color(0xFF9E9E9E), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }
                if (product.stock in 1..5) {
                    Box(
                        modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFFFFF3E0))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text("Còn ${product.stock}", color = Color(0xFFE8A44A), fontSize = 9.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }

            Column(modifier = Modifier.padding(10.dp)) {
                Text(product.brandName, color = MintGreen, fontSize = 10.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(2.dp))
                Text(
                    product.name,
                    color      = if (isOutOfStock) Color(0xFFAAAAAA) else Color(0xFF1A4A40),
                    fontWeight = FontWeight.SemiBold,
                    fontSize   = 13.sp,
                    maxLines   = 2,
                    overflow   = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Text(
                        "${"%,.0f".format(product.price)}đ",
                        color      = if (isOutOfStock) Color(0xFFAAAAAA) else Color(0xFF1A4A40),
                        fontWeight = FontWeight.Bold,
                        fontSize   = 13.sp
                    )
                    if (!isOutOfStock) {
                        IconButton(
                            onClick  = { onAddToCart(product) },
                            modifier = Modifier.size(32.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(brush = AppGradients.mintHorizontal)
                        ) {
                            Icon(
                                if (isLoggedIn) Icons.Default.ShoppingCart else Icons.Default.Login,
                                contentDescription = null,
                                tint               = Color.White,
                                modifier           = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── List Card ─────────────────────────────────────────────────────────────────
@Composable
private fun ProductListCard(
    product     : Product,
    isLoggedIn  : Boolean,
    onAddToCart : (Product) -> Unit,
    onCardClick : () -> Unit
) {
    val isNew        = remember(product.createdAt) {
        (System.currentTimeMillis() - product.createdAt) < 7 * 24 * 60 * 60 * 1000L
    }
    val isOutOfStock = product.stock == 0

    Card(
        modifier  = Modifier.fillMaxWidth().clickable { onCardClick() },
        shape     = RoundedCornerShape(16.dp),
        colors    = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFEAF9F5)),
                contentAlignment = Alignment.Center
            ) {
                if (product.imageUrl.isNotEmpty()) {
                    AsyncImage(
                        model              = product.imageUrl,
                        contentDescription = null,
                        contentScale       = ContentScale.Crop,
                        modifier           = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)),
                        alpha              = if (isOutOfStock) 0.5f else 1f
                    )
                } else { Text("🌿", fontSize = 32.sp) }

                if (isNew && !isOutOfStock) {
                    Box(
                        modifier = Modifier.align(Alignment.TopStart)
                            .clip(RoundedCornerShape(bottomEnd = 6.dp))
                            .background(brush = AppGradients.mintHorizontal)
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text("MỚI", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                    }
                }
                if (isOutOfStock) {
                    Box(
                        modifier = Modifier.align(Alignment.TopStart)
                            .clip(RoundedCornerShape(bottomEnd = 6.dp))
                            .background(Color(0xFFE0E0E0))
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text("Hết hàng", color = Color(0xFF9E9E9E), fontSize = 8.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(product.brandName, color = MintGreen, fontSize = 10.sp)
                Text(
                    product.name,
                    color      = if (isOutOfStock) Color(0xFFAAAAAA) else Color(0xFF1A4A40),
                    fontWeight = FontWeight.SemiBold,
                    fontSize   = 14.sp,
                    maxLines   = 2,
                    overflow   = TextOverflow.Ellipsis
                )
                if (product.category.isNotEmpty()) {
                    Spacer(Modifier.height(2.dp))
                    Text(product.category, color = Color(0xFFAAD8CE), fontSize = 11.sp)
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "${"%,.0f".format(product.price)}đ",
                    color      = if (isOutOfStock) Color(0xFFAAAAAA) else Color(0xFF1A4A40),
                    fontWeight = FontWeight.Bold,
                    fontSize   = 14.sp
                )
            }

            if (!isOutOfStock) {
                Box(
                    modifier = Modifier.size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(brush = AppGradients.mintHorizontal)
                        .clickable { onAddToCart(product) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (isLoggedIn) Icons.Default.ShoppingCartCheckout else Icons.Default.Login,
                        contentDescription = null,
                        tint               = Color.White,
                        modifier           = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

// ── Add to Cart ───────────────────────────────────────────────────────────────
private fun addToCart(db: FirebaseFirestore, auth: FirebaseAuth, product: Product) {
    val uid     = auth.currentUser?.uid ?: return
    val cartRef = db.collection("carts").document(uid)
        .collection("items").document(product.id)

    db.runTransaction { transaction ->
        val snap = transaction.get(cartRef)
        val qty  = (snap.getLong("quantity") ?: 0L).toInt()
        transaction.set(
            cartRef, hashMapOf(
                "productId" to product.id,
                "name"      to product.name,
                "price"     to product.price,
                "imageUrl"  to product.imageUrl,
                "brandName" to product.brandName,
                "quantity"  to qty + 1,
                "updatedAt" to System.currentTimeMillis()
            )
        )
    }
}