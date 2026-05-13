package com.example.appbanmypham.ui.admin

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.example.appbanmypham.data.CloudinaryHelper          // ← THÊM import này
import com.example.appbanmypham.data.local.AppDatabase
import com.example.appbanmypham.model.Product
import com.example.appbanmypham.ui.theme.*
import com.google.firebase.firestore.FirebaseFirestore
// ĐÃ XOÁ: import com.google.firebase.storage.FirebaseStorage
// ĐÃ XOÁ: import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class ManageProductActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppBanMyPhamTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = BackgroundPrimary) {
                    ManageProductScreen(onBack = { finish() })
                }
            }
        }
    }
}

// ── Model ────────────────────────────────────────────────────────────────────
data class ProductItem(
    val id: String = "",
    val name: String = "",
    val price: Double = 0.0,
    val stock: Int = 0,
    val description: String = "",
    val brandId: String = "",
    val brandName: String = "",
    val imageUrl: String = "",
    val category: String = ""
)

data class BrandItem(val id: String = "", val name: String = "")

// ── Screen ───────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageProductScreen(onBack: () -> Unit = {}) {
    val context = LocalContext.current
    val db = remember { FirebaseFirestore.getInstance() }
    // ĐÃ XOÁ: val storage = remember { FirebaseStorage.getInstance() }
    val scope = rememberCoroutineScope()

    var products    by remember { mutableStateOf(listOf<ProductItem>()) }
    var brands      by remember { mutableStateOf(listOf<BrandItem>()) }
    var isLoading   by remember { mutableStateOf(true) }
    var showDialog  by remember { mutableStateOf(false) }
    var editTarget  by remember { mutableStateOf<ProductItem?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var errorMsg    by remember { mutableStateOf<String?>(null) }
    var isSaving    by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        db.collection("brands").addSnapshotListener { snap, _ ->
            brands = snap?.documents?.map {
                BrandItem(id = it.id, name = it.getString("name") ?: "")
            } ?: emptyList()
        }
        db.collection("products").addSnapshotListener { snap, _ ->
            products = snap?.documents?.map { doc ->
                ProductItem(
                    id          = doc.id,
                    name        = doc.getString("name") ?: "",
                    price       = doc.getDouble("price") ?: 0.0,
                    stock       = (doc.getLong("stock") ?: 0L).toInt(),
                    description = doc.getString("description") ?: "",
                    brandId     = doc.getString("brandId") ?: "",
                    brandName   = doc.getString("brandName") ?: "",
                    imageUrl    = doc.getString("imageUrl") ?: "",
                    category    = doc.getString("category") ?: ""
                )
            } ?: emptyList()
            isLoading = false
        }
    }

    val filtered = products.filter {
        it.name.contains(searchQuery, ignoreCase = true) ||
                it.brandName.contains(searchQuery, ignoreCase = true) ||
                it.category.contains(searchQuery, ignoreCase = true)
    }

    // Hiện thông báo lỗi nếu có
    errorMsg?.let { msg ->
        LaunchedEffect(msg) {
            kotlinx.coroutines.delay(3000)
            errorMsg = null
        }
        Snackbar(
            modifier = Modifier.padding(16.dp),
            containerColor = Color(0xFFE57373)
        ) { Text(msg, color = Color.White) }
    }

    Scaffold(
        containerColor = BackgroundPrimary,
        floatingActionButton = {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(brush = AppGradients.mintHorizontal)
                    .clickable { editTarget = null; showDialog = true },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Add, contentDescription = "Thêm", tint = Color.White)
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
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
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.25f))
                        .align(Alignment.TopStart)
                ) {
                    Icon(Icons.Default.ArrowBack, contentDescription = null, tint = Color.White)
                }
                Column(modifier = Modifier.align(Alignment.BottomStart)) {
                    Text("QUẢN LÝ", color = Color.White.copy(0.75f), fontSize = 11.sp, letterSpacing = 2.sp)
                    Text("Sản phẩm", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    Text("${products.size} sản phẩm", color = Color.White.copy(0.75f), fontSize = 13.sp)
                }
            }

            // ── Search ──
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(y = (-20).dp)
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .background(BackgroundPrimary)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    placeholder = { Text("Tìm kiếm sản phẩm...", color = Color(0xFFAAD8CE)) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = MintGreen) },
                    shape = RoundedCornerShape(14.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = MintGreen,
                        unfocusedBorderColor = Color(0xFFB2E8DA),
                        focusedTextColor     = Color(0xFF1A4A40),
                        unfocusedTextColor   = Color(0xFF1A4A40),
                        cursorColor          = MintGreen
                    )
                )
            }

            if (isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MintGreen)
                }
            } else if (filtered.isEmpty()) {
                Box(
                    Modifier.fillMaxSize().offset(y = (-20).dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🌿", fontSize = 48.sp)
                        Spacer(Modifier.height(8.dp))
                        Text("Chưa có sản phẩm nào", color = Color(0xFF8ACABA), fontSize = 15.sp)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().offset(y = (-20).dp),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filtered, key = { it.id }) { product ->
                        ProductCard(
                            product  = product,
                            onEdit   = { editTarget = it; showDialog = true },
                            onDelete = {
                                scope.launch {
                                    // Xoá ảnh trên Cloudinary
                                    it.imageUrl.takeIf { url -> url.isNotEmpty() }?.let { url ->
                                        CloudinaryHelper.getPublicIdFromUrl(url)?.let { pid ->
                                            CloudinaryHelper.deleteImage(pid)
                                        }
                                    }
                                    // Xoá Firestore
                                    db.collection("products").document(it.id).delete()
                                    // Xoá Room
                                    withContext(Dispatchers.IO) {
                                        AppDatabase.getInstance(context).productDao().deleteById(it.id)
                                    }
                                }
                            }
                        )
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }
    }

    // Dialog thêm / sửa
    if (showDialog) {
        ProductDialog(
            existing  = editTarget,
            brands    = brands,
            isSaving  = isSaving,
            onDismiss = { if (!isSaving) showDialog = false },
            onSave    = { item, imageUri ->
                scope.launch {
                    isSaving = true
                    try {
                        saveProduct(db, item, imageUri, context)  // ← KHÔNG còn tham số storage
                        showDialog = false
                    } catch (e: Exception) {
                        errorMsg = "Lỗi: ${e.message}"
                    } finally {
                        isSaving = false
                    }
                }
            }
        )
    }
}

// ── Product Card ─────────────────────────────────────────────────────────────
@Composable
private fun ProductCard(
    product: ProductItem,
    onEdit: (ProductItem) -> Unit,
    onDelete: (ProductItem) -> Unit
) {
    var showConfirm by remember { mutableStateOf(false) }

    Card(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(18.dp),
        colors    = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFEAF9F5)),
                contentAlignment = Alignment.Center
            ) {
                if (product.imageUrl.isNotEmpty()) {
                    AsyncImage(
                        model            = product.imageUrl,
                        contentDescription = null,
                        contentScale     = ContentScale.Crop,
                        modifier         = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp))
                    )
                } else {
                    Text("🌿", fontSize = 28.sp)
                }
            }

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(product.name, color = Color(0xFF1A4A40), fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1)
                Spacer(Modifier.height(2.dp))
                Text(product.brandName, color = MintGreen, fontSize = 11.sp)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${"%,.0f".format(product.price)}đ",
                        color = Color(0xFF1A4A40),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (product.stock > 0) Color(0xFFEAF9F5) else Color(0xFFFFF0F0))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            "Kho: ${product.stock}",
                            color = if (product.stock > 0) MintGreen else Color(0xFFE57373),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(
                    onClick = { onEdit(product) },
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFFEAF9F5))
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, tint = MintGreen, modifier = Modifier.size(18.dp))
                }
                IconButton(
                    onClick = { showConfirm = true },
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFFFFF0F0))
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFE57373), modifier = Modifier.size(18.dp))
                }
            }
        }
    }

    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            containerColor   = Color.White,
            title  = { Text("Xoá sản phẩm", color = Color(0xFF1A4A40), fontWeight = FontWeight.Bold) },
            text   = { Text("Bạn có chắc muốn xoá \"${product.name}\"?", color = Color(0xFF4A7A70)) },
            confirmButton = {
                TextButton(onClick = { showConfirm = false; onDelete(product) }) {
                    Text("Xoá", color = Color(0xFFE57373), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirm = false }) {
                    Text("Huỷ", color = MintGreen)
                }
            }
        )
    }
}

// ── Product Dialog ────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProductDialog(
    existing: ProductItem?,
    brands: List<BrandItem>,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onSave: (ProductItem, Uri?) -> Unit
) {
    var name          by remember { mutableStateOf(existing?.name ?: "") }
    var price         by remember { mutableStateOf(existing?.price?.toString() ?: "") }
    var stock         by remember { mutableStateOf(existing?.stock?.toString() ?: "") }
    var description   by remember { mutableStateOf(existing?.description ?: "") }
    var category      by remember { mutableStateOf(existing?.category ?: "") }
    var selectedBrand by remember { mutableStateOf(brands.find { it.id == existing?.brandId }) }
    var imageUri      by remember { mutableStateOf<Uri?>(null) }
    var brandExpanded by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { imageUri = it }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape  = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp)
            ) {
                // Title
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(brush = AppGradients.mintHorizontal),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (existing == null) Icons.Default.Add else Icons.Default.Edit,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        if (existing == null) "Thêm sản phẩm" else "Sửa sản phẩm",
                        color = Color(0xFF1A4A40),
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    )
                }

                Spacer(Modifier.height(20.dp))

                // Image picker
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFFEAF9F5))
                        .border(1.dp, Color(0xFFB2E8DA), RoundedCornerShape(14.dp))
                        .clickable(enabled = !isSaving) { launcher.launch("image/*") },
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        imageUri != null -> AsyncImage(
                            model            = imageUri,
                            contentDescription = null,
                            contentScale     = ContentScale.Crop,
                            modifier         = Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp))
                        )
                        existing?.imageUrl?.isNotEmpty() == true -> AsyncImage(
                            model            = existing.imageUrl,
                            contentDescription = null,
                            contentScale     = ContentScale.Crop,
                            modifier         = Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp))
                        )
                        else -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, tint = MintGreen, modifier = Modifier.size(32.dp))
                            Spacer(Modifier.height(4.dp))
                            Text("Chọn ảnh sản phẩm", color = MintGreen, fontSize = 12.sp)
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                DialogLabel("Tên sản phẩm *")
                DialogTextField(value = name, onValueChange = { name = it }, placeholder = "Nhập tên sản phẩm")

                Spacer(Modifier.height(10.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(Modifier.weight(1f)) {
                        DialogLabel("Giá (đ) *")
                        DialogTextField(value = price, onValueChange = { price = it }, placeholder = "0", keyboardType = KeyboardType.Number)
                    }
                    Column(Modifier.weight(1f)) {
                        DialogLabel("Tồn kho *")
                        DialogTextField(value = stock, onValueChange = { stock = it }, placeholder = "0", keyboardType = KeyboardType.Number)
                    }
                }

                Spacer(Modifier.height(10.dp))

                DialogLabel("Danh mục")
                DialogTextField(value = category, onValueChange = { category = it }, placeholder = "VD: Kem dưỡng, Son môi...")

                Spacer(Modifier.height(10.dp))

                DialogLabel("Thương hiệu")
                ExposedDropdownMenuBox(expanded = brandExpanded, onExpandedChange = { brandExpanded = it }) {
                    OutlinedTextField(
                        value       = selectedBrand?.name ?: "Chọn thương hiệu",
                        onValueChange = {},
                        readOnly    = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = brandExpanded) },
                        modifier    = Modifier.fillMaxWidth().menuAnchor(),
                        shape       = RoundedCornerShape(12.dp),
                        colors      = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor   = MintGreen,
                            unfocusedBorderColor = Color(0xFFB2E8DA),
                            focusedTextColor     = Color(0xFF1A4A40),
                            unfocusedTextColor   = Color(0xFF1A4A40)
                        )
                    )
                    ExposedDropdownMenu(
                        expanded          = brandExpanded,
                        onDismissRequest  = { brandExpanded = false },
                        modifier          = Modifier.background(Color.White)
                    ) {
                        brands.forEach { brand ->
                            DropdownMenuItem(
                                text    = { Text(brand.name, color = Color(0xFF1A4A40)) },
                                onClick = { selectedBrand = brand; brandExpanded = false }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                DialogLabel("Mô tả")
                OutlinedTextField(
                    value         = description,
                    onValueChange = { description = it },
                    modifier      = Modifier.fillMaxWidth().height(90.dp),
                    placeholder   = { Text("Mô tả sản phẩm...", color = Color(0xFFAAD8CE)) },
                    shape         = RoundedCornerShape(12.dp),
                    maxLines      = 4,
                    colors        = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = MintGreen,
                        unfocusedBorderColor = Color(0xFFB2E8DA),
                        focusedTextColor     = Color(0xFF1A4A40),
                        unfocusedTextColor   = Color(0xFF1A4A40),
                        cursorColor          = MintGreen
                    )
                )

                Spacer(Modifier.height(20.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick  = onDismiss,
                        enabled  = !isSaving,
                        modifier = Modifier.weight(1f),
                        shape    = RoundedCornerShape(12.dp),
                        border   = ButtonDefaults.outlinedButtonBorder.copy(width = 1.dp),
                        colors   = ButtonDefaults.outlinedButtonColors(contentColor = MintGreen)
                    ) { Text("Huỷ") }

                    val isValid = name.isNotBlank() && price.isNotBlank() && stock.isNotBlank()
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                brush = if (isValid && !isSaving) AppGradients.mintHorizontal
                                else androidx.compose.ui.graphics.Brush.horizontalGradient(
                                    listOf(Color(0xFFB2E8DA), Color(0xFFD0F2EC))
                                )
                            )
                            .clickable(enabled = isValid && !isSaving) {
                                onSave(
                                    ProductItem(
                                        id          = existing?.id ?: "",
                                        name        = name.trim(),
                                        price       = price.toDoubleOrNull() ?: 0.0,
                                        stock       = stock.toIntOrNull() ?: 0,
                                        description = description.trim(),
                                        brandId     = selectedBrand?.id ?: "",
                                        brandName   = selectedBrand?.name ?: "",
                                        imageUrl    = existing?.imageUrl ?: "",
                                        category    = category.trim()
                                    ),
                                    imageUri
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color    = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("Lưu", color = Color.White, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DialogLabel(text: String) {
    Text(
        text     = text,
        color    = MintGreen,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(bottom = 5.dp)
    )
}

@Composable
private fun DialogTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    OutlinedTextField(
        value         = value,
        onValueChange = onValueChange,
        modifier      = Modifier.fillMaxWidth(),
        placeholder   = { Text(placeholder, color = Color(0xFFAAD8CE)) },
        shape         = RoundedCornerShape(12.dp),
        singleLine    = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        colors        = OutlinedTextFieldDefaults.colors(
            focusedBorderColor   = MintGreen,
            unfocusedBorderColor = Color(0xFFB2E8DA),
            focusedTextColor     = Color(0xFF1A4A40),
            unfocusedTextColor   = Color(0xFF1A4A40),
            cursorColor          = MintGreen
        )
    )
}

// ── Save Logic (dùng Cloudinary thay Firebase Storage) ───────────────────────
private suspend fun saveProduct(
    db: FirebaseFirestore,
    // ĐÃ XOÁ: storage: FirebaseStorage,
    item: ProductItem,
    imageUri: Uri?,
    context: android.content.Context
) {
    var finalImageUrl = item.imageUrl

    if (imageUri != null) {
        // Xoá ảnh cũ trên Cloudinary nếu đang edit
        if (item.imageUrl.isNotEmpty()) {
            CloudinaryHelper.getPublicIdFromUrl(item.imageUrl)?.let { pid ->
                CloudinaryHelper.deleteImage(pid)
            }
        }
        // Upload ảnh mới lên Cloudinary
        finalImageUrl = CloudinaryHelper.uploadImage(context, imageUri)
    }

    val data = hashMapOf(
        "name"        to item.name,
        "price"       to item.price,
        "stock"       to item.stock,
        "description" to item.description,
        "brandId"     to item.brandId,
        "brandName"   to item.brandName,
        "imageUrl"    to finalImageUrl,
        "category"    to item.category,
        "updatedAt"   to System.currentTimeMillis()
    )

    if (item.id.isEmpty()) {
        data["createdAt"] = System.currentTimeMillis()
        val docRef = db.collection("products").add(data).await()
        withContext(Dispatchers.IO) {
            AppDatabase.getInstance(context).productDao().insert(
                Product(
                    id          = docRef.id,
                    name        = item.name,
                    price       = item.price,
                    stock       = item.stock,
                    description = item.description,
                    brandId     = item.brandId,
                    brandName   = item.brandName,
                    imageUrl    = finalImageUrl,
                    category    = item.category
                )
            )
        }
    } else {
        db.collection("products").document(item.id).update(data as Map<String, Any>).await()
        withContext(Dispatchers.IO) {
            AppDatabase.getInstance(context).productDao().update(
                Product(
                    id          = item.id,
                    name        = item.name,
                    price       = item.price,
                    stock       = item.stock,
                    description = item.description,
                    brandId     = item.brandId,
                    brandName   = item.brandName,
                    imageUrl    = finalImageUrl,
                    category    = item.category
                )
            )
        }
    }
}