package com.example.appbanmypham.ui.auth

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
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.appbanmypham.ui.admin.DashboardActivity
import com.example.appbanmypham.ui.product.ProductActivity
import com.example.appbanmypham.ui.theme.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await

class LoginActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Nếu đã đăng nhập rồi thì bỏ qua màn login
        if (FirebaseAuth.getInstance().currentUser != null) {
            startActivity(
                Intent(this, ProductActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
            )
            finish()
            return
        }

        setContent {
            AppBanMyPhamTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = BackgroundPrimary) {
                    LoginScreen(
                        onGoRegister = { startActivity(Intent(this, RegisterActivity::class.java)) },
                        onGoHome = {
                            startActivity(Intent(this, ProductActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            })
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun LoginScreen(
    onGoRegister: () -> Unit = {},
    onGoHome: () -> Unit = {}
) {
    val context = LocalContext.current
    val auth    = remember { FirebaseAuth.getInstance() }
    val db      = remember { FirebaseFirestore.getInstance() }
    val scope   = rememberCoroutineScope()

    var email     by remember { mutableStateOf("") }
    var password  by remember { mutableStateOf("") }
    var showPass  by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMsg  by remember { mutableStateOf("") }

    fun navigateTo(cls: Class<*>) {
        context.startActivity(
            Intent(context, cls).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
        )
    }

    fun doLogin() {
        if (email.isBlank() || password.isBlank()) {
            errorMsg = "Vui lòng điền đầy đủ thông tin"
            return
        }
        isLoading = true
        errorMsg  = ""

        scope.launch {
            try {
                // Timeout 15 giây cho toàn bộ quá trình login
                withTimeout(15_000L) {

                    // Bước 1: Firebase Auth
                    val result = withContext(Dispatchers.IO) {
                        auth.signInWithEmailAndPassword(email.trim(), password).await()
                    }

                    val uid = result.user?.uid
                        ?: throw Exception("Không lấy được thông tin tài khoản")

                    // Bước 2: Đọc role từ Firestore, timeout riêng 5 giây
                    val role = try {
                        withTimeout(5_000L) {
                            val doc = withContext(Dispatchers.IO) {
                                db.collection("users").document(uid).get().await()
                            }
                            doc.getLong("role")?.toInt() ?: 0
                        }
                    } catch (e: Exception) {
                        // Firestore lỗi/offline → dùng role mặc định (user thường)
                        0
                    }

                    // Bước 3: Navigate
                    isLoading = false
                    navigateTo(
                        if (role == 1) DashboardActivity::class.java
                        else ProductActivity::class.java
                    )
                }

            } catch (e: TimeoutCancellationException) {
                isLoading = false
                errorMsg = "Kết nối quá chậm, vui lòng thử lại"
            } catch (e: Exception) {
                isLoading = false
                errorMsg = when {
                    e.message?.contains("INVALID_LOGIN_CREDENTIALS", ignoreCase = true) == true -> "Email hoặc mật khẩu không đúng"
                    e.message?.contains("no user record",            ignoreCase = true) == true -> "Tài khoản không tồn tại"
                    e.message?.contains("badly formatted",           ignoreCase = true) == true -> "Email không hợp lệ"
                    e.message?.contains("network",                   ignoreCase = true) == true -> "Lỗi kết nối mạng"
                    e.message?.contains("NETWORK_ERROR",             ignoreCase = true) == true -> "Lỗi kết nối mạng"
                    else -> "Đăng nhập thất bại: ${e.message}"
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundPrimary)
            .verticalScroll(rememberScrollState())
    ) {
        // ── Hero Section ──────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp)
                .background(brush = AppGradients.mintHorizontal)
                .padding(horizontal = 20.dp, vertical = 20.dp)
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.25f))
                    .clickable { onGoHome() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Home,
                    contentDescription = "Về trang chủ",
                    tint     = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(horizontal = 8.dp, vertical = 8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.White.copy(alpha = 0.3f)),
                        contentAlignment = Alignment.Center
                    ) { Text("🌿", fontSize = 16.sp) }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "LUMIÈRE",
                        color = Color.White, fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold, letterSpacing = 2.sp
                    )
                }
                Spacer(modifier = Modifier.height(14.dp))
                Text("Chào mừng", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Text(
                    "trở lại!",
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 26.sp, fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "Đăng nhập để khám phá bộ sưu tập mới nhất",
                    color = Color.White.copy(alpha = 0.82f), fontSize = 13.sp
                )
            }
        }

        // ── Form Card ─────────────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .offset(y = (-24).dp)
                .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                .background(BackgroundPrimary)
                .padding(horizontal = 28.dp, vertical = 28.dp)
        ) {
            Text(
                "ĐĂNG NHẬP TÀI KHOẢN",
                color = MintGreen, fontSize = 11.sp,
                fontWeight = FontWeight.Medium, letterSpacing = 2.sp
            )
            Spacer(modifier = Modifier.height(20.dp))

            LoginFieldLabel("Email")
            OutlinedTextField(
                value = email,
                onValueChange = { email = it; errorMsg = "" },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("yourname@email.com", color = Color(0xFFAAD8CE)) },
                shape = RoundedCornerShape(14.dp),
                singleLine = true,
                enabled = !isLoading,
                colors = loginTextFieldColors()
            )

            Spacer(modifier = Modifier.height(14.dp))

            LoginFieldLabel("Mật khẩu")
            OutlinedTextField(
                value = password,
                onValueChange = { password = it; errorMsg = "" },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Mật khẩu", color = Color(0xFFAAD8CE)) },
                shape = RoundedCornerShape(14.dp),
                singleLine = true,
                enabled = !isLoading,
                visualTransformation = if (showPass) VisualTransformation.None
                else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showPass = !showPass }) {
                        Icon(
                            if (showPass) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = null, tint = Color(0xFFAAD8CE)
                        )
                    }
                },
                colors = loginTextFieldColors()
            )

            Spacer(modifier = Modifier.height(10.dp))
            Text(
                "Quên mật khẩu?",
                fontSize = 12.sp, color = MintGreen, fontWeight = FontWeight.Medium,
                modifier = Modifier.align(Alignment.End).clickable(enabled = !isLoading) { }
            )
            Spacer(modifier = Modifier.height(16.dp))

            if (errorMsg.isNotEmpty()) {
                Text(
                    errorMsg,
                    color = Color(0xFFF09595), fontSize = 13.sp,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }

            // Nút đăng nhập
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(brush = AppGradients.mintHorizontal)
                    .clickable(enabled = !isLoading) { doLogin() },
                contentAlignment = Alignment.Center
            ) {
                if (isLoading) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "Đang đăng nhập...",
                            color = Color.White, fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                } else {
                    Text(
                        "Đăng nhập",
                        color = Color.White, fontSize = 15.sp,
                        fontWeight = FontWeight.Medium, letterSpacing = 0.5.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFFEAF9F5))
                    .clickable(enabled = !isLoading) { onGoHome() },
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Home, null, tint = MintGreen, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Tiếp tục không đăng nhập", color = MintGreen, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                Text("Chưa có tài khoản? ", fontSize = 12.sp, color = Color(0xFF8ACABA))
                Text(
                    "Đăng ký ngay",
                    fontSize = 12.sp, color = MintGreen, fontWeight = FontWeight.Medium,
                    modifier = Modifier.clickable(enabled = !isLoading) { onGoRegister() }
                )
            }
        }
    }
}

@Composable
private fun LoginFieldLabel(text: String) {
    Text(
        text = text, color = MintGreen, fontSize = 11.sp,
        fontWeight = FontWeight.Medium, modifier = Modifier.padding(bottom = 6.dp)
    )
}

@Composable
private fun loginTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor   = MintGreen,
    unfocusedBorderColor = Color(0xFFB2E8DA),
    focusedTextColor     = Color(0xFF1A4A40),
    unfocusedTextColor   = Color(0xFF1A4A40),
    cursorColor          = MintGreen,
    errorBorderColor     = Color(0xFFF09595)
)

@Preview(showBackground = true)
@Composable
fun LoginPreview() {
    AppBanMyPhamTheme { LoginScreen() }
}