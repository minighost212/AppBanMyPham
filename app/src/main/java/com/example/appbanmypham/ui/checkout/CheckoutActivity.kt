package com.example.appbanmypham.ui.checkout

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.appbanmypham.data.local.AppDatabase
import com.example.appbanmypham.model.Order
import com.example.appbanmypham.model.OrderEntity
import com.example.appbanmypham.model.OrderItem
import com.example.appbanmypham.ui.theme.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class CheckoutActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val totalPrice  = intent.getDoubleExtra("total_price", 0.0)
        val ids         = intent.getStringArrayExtra("item_ids")        ?: emptyArray()
        val names       = intent.getStringArrayExtra("item_names")      ?: emptyArray()
        val brands      = intent.getStringArrayExtra("item_brands")     ?: emptyArray()
        val images      = intent.getStringArrayExtra("item_images")     ?: emptyArray()
        val prices      = intent.getDoubleArrayExtra("item_prices")     ?: DoubleArray(0)
        val quantities  = intent.getIntArrayExtra("item_quantities")    ?: IntArray(0)

        val orderItems = ids.mapIndexed { i, id ->
            OrderItem(
                productId = id,
                name      = names.getOrElse(i) { "" },
                brandName = brands.getOrElse(i) { "" },
                imageUrl  = images.getOrElse(i) { "" },
                price     = prices.getOrElse(i) { 0.0 },
                quantity  = quantities.getOrElse(i) { 1 }
            )
        }

        setContent {
            AppBanMyPhamTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = BackgroundPrimary) {
                    CheckoutScreen(
                        orderItems = orderItems,
                        totalPrice = totalPrice,
                        onBack     = { finish() },
                        onSuccess  = {
                            val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return@CheckoutScreen
                            val db  = FirebaseFirestore.getInstance()
                            db.collection("carts").document(uid).collection("items").get()
                                .addOnSuccessListener { snap ->
                                    snap.documents.forEach { it.reference.delete() }
                                }
                            finish()
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun CheckoutScreen(
    orderItems : List<OrderItem>,
    totalPrice : Double,
    onBack     : () -> Unit = {},
    onSuccess  : () -> Unit = {}
) {
    val db      = remember { FirebaseFirestore.getInstance() }
    val auth    = remember { FirebaseAuth.getInstance() }
    val context = LocalContext.current

    var receiverName by remember { mutableStateOf("") }
    var phoneNumber  by remember { mutableStateOf("") }
    var address      by remember { mutableStateOf("") }
    var isLoading    by remember { mutableStateOf(false) }
    var showSuccess  by remember { mutableStateOf(false) }
    var errorMsg     by remember { mutableStateOf("") }

    if (showSuccess) {
        AlertDialog(
            onDismissRequest = {},
            containerColor   = Color.White,
            shape            = RoundedCornerShape(20.dp),
            title = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("🎉", fontSize = 48.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Đặt hàng thành công!",
                        fontWeight = FontWeight.Bold,
                        color      = Color(0xFF1A4A40),
                        fontSize   = 18.sp
                    )
                }
            },
            text = {
                Text(
                    "Đơn hàng đã được ghi nhận.\nChúng tôi sẽ liên hệ sớm nhất!",
                    color    = Color(0xFF5A8A80),
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick  = { onSuccess() },
                    colors   = ButtonDefaults.buttonColors(containerColor = MintGreen),
                    shape    = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Về trang chủ", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    Scaffold(
        containerColor = BackgroundPrimary,
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(brush = AppGradients.mintHorizontal)
                    .statusBarsPadding()
                    .padding(horizontal = 4.dp, vertical = 4.dp)
            ) {
                IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart)) {
                    Icon(Icons.Default.ArrowBack, null, tint = Color.White)
                }
                Text(
                    "Xác nhận đặt hàng",
                    modifier   = Modifier.align(Alignment.Center),
                    color      = Color.White,
                    fontSize   = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // ── Thông tin giao hàng ──────────────────────────────────────────
            Card(
                shape     = RoundedCornerShape(16.dp),
                colors    = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "Thông tin giao hàng",
                        color      = MintGreen,
                        fontSize   = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    OutlinedTextField(
                        value         = receiverName,
                        onValueChange = { receiverName = it },
                        label         = { Text("Họ và tên người nhận") },
                        leadingIcon   = { Icon(Icons.Default.Person, null, tint = MintGreen) },
                        modifier      = Modifier.fillMaxWidth(),
                        shape         = RoundedCornerShape(12.dp),
                        colors        = mintTextFieldColors()
                    )
                    OutlinedTextField(
                        value           = phoneNumber,
                        onValueChange   = { phoneNumber = it },
                        label           = { Text("Số điện thoại") },
                        leadingIcon     = { Icon(Icons.Default.Phone, null, tint = MintGreen) },
                        modifier        = Modifier.fillMaxWidth(),
                        shape           = RoundedCornerShape(12.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        colors          = mintTextFieldColors()
                    )
                    OutlinedTextField(
                        value         = address,
                        onValueChange = { address = it },
                        label         = { Text("Địa chỉ giao hàng") },
                        leadingIcon   = { Icon(Icons.Default.LocationOn, null, tint = MintGreen) },
                        modifier      = Modifier.fillMaxWidth(),
                        minLines      = 2,
                        shape         = RoundedCornerShape(12.dp),
                        colors        = mintTextFieldColors()
                    )
                }
            }

            // ── Thanh toán ───────────────────────────────────────────────────
            Card(
                shape     = RoundedCornerShape(16.dp),
                colors    = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Row(
                    modifier              = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.LocalShipping, null, tint = MintGreen, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("Phương thức thanh toán", color = Color(0xFF8ACABA), fontSize = 12.sp)
                            Text(
                                "Thanh toán khi nhận hàng (COD)",
                                color      = Color(0xFF1A4A40),
                                fontWeight = FontWeight.SemiBold,
                                fontSize   = 14.sp
                            )
                        }
                    }
                    Icon(Icons.Default.CheckCircle, null, tint = MintGreen, modifier = Modifier.size(22.dp))
                }
            }

            // ── Tóm tắt đơn hàng ────────────────────────────────────────────
            Card(
                shape     = RoundedCornerShape(16.dp),
                colors    = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Tóm tắt đơn hàng", color = MintGreen, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    orderItems.forEach { item ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(
                                "${item.name} x${item.quantity}",
                                color    = Color(0xFF1A4A40),
                                fontSize = 13.sp,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                "${"%,.0f".format(item.price * item.quantity)}đ",
                                color      = Color(0xFF1A4A40),
                                fontSize   = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                    HorizontalDivider(color = Color(0xFFEAF9F5))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Tổng cộng", color = Color(0xFF1A4A40), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text(
                            "${"%,.0f".format(totalPrice)}đ",
                            color      = MintGreen,
                            fontWeight = FontWeight.Bold,
                            fontSize   = 15.sp
                        )
                    }
                }
            }

            if (errorMsg.isNotEmpty()) {
                Text(errorMsg, color = Color(0xFFE57373), fontSize = 13.sp)
            }

            // ── Nút xác nhận ─────────────────────────────────────────────────
            Button(
                onClick = {
                    if (receiverName.isBlank()) { errorMsg = "Vui lòng nhập tên người nhận"; return@Button }
                    if (phoneNumber.isBlank())  { errorMsg = "Vui lòng nhập số điện thoại";  return@Button }
                    if (address.isBlank())      { errorMsg = "Vui lòng nhập địa chỉ giao hàng"; return@Button }

                    errorMsg  = ""
                    isLoading = true

                    val uid     = auth.currentUser?.uid ?: return@Button
                    val orderId = UUID.randomUUID().toString()

                    val order = Order(
                        id            = orderId,
                        userId        = uid,
                        items         = orderItems,
                        totalPrice    = totalPrice,
                        address       = address,
                        phoneNumber   = phoneNumber,
                        receiverName  = receiverName,
                        status        = "pending",
                        paymentMethod = "COD",
                        createdAt     = System.currentTimeMillis()
                    )

                    db.collection("orders").document(orderId)
                        .set(order)
                        .addOnSuccessListener {
                            val itemsJson = JSONArray().apply {
                                orderItems.forEach { item ->
                                    put(JSONObject().apply {
                                        put("productId", item.productId)
                                        put("name",      item.name)
                                        put("brandName", item.brandName)
                                        put("imageUrl",  item.imageUrl)
                                        put("price",     item.price)
                                        put("quantity",  item.quantity)
                                    })
                                }
                            }.toString()

                            val entity = OrderEntity(
                                id            = orderId,
                                userId        = uid,
                                totalPrice    = totalPrice,
                                address       = address,
                                phoneNumber   = phoneNumber,
                                receiverName  = receiverName,
                                status        = "pending",
                                paymentMethod = "COD",
                                itemsJson     = itemsJson,
                                createdAt     = System.currentTimeMillis()
                            )

                            CoroutineScope(Dispatchers.IO).launch {
                                AppDatabase.getInstance(context).orderDao().insert(entity)
                            }

                            isLoading   = false
                            showSuccess = true
                        }
                        .addOnFailureListener {
                            isLoading = false
                            errorMsg  = "Đặt hàng thất bại: ${it.message}"
                        }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape    = RoundedCornerShape(14.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = MintGreen),
                enabled  = !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        color       = Color.White,
                        modifier    = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(Icons.Default.CheckCircle, null, tint = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Xác nhận đặt hàng",
                        color      = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize   = 16.sp
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun mintTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor   = MintGreen,
    unfocusedBorderColor = Color(0xFFCCEEE6),
    focusedLabelColor    = MintGreen,
    unfocusedLabelColor  = Color(0xFF8ACABA),
    cursorColor          = MintGreen
)