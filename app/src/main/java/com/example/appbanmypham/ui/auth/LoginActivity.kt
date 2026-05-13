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

class LoginActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppBanMyPhamTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = BackgroundPrimary) {
                    LoginScreen(
                        onGoRegister = {
                            startActivity(Intent(this, RegisterActivity::class.java))
                        },
                        onGoHome = {
                            val intent = Intent(this, ProductActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            }
                            startActivity(intent)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun LoginScreen(
    onGoRegister : () -> Unit = {},
    onGoHome     : () -> Unit = {}
) {
    val context = LocalContext.current
    val auth    = remember { FirebaseAuth.getInstance() }
    val db      = remember { FirebaseFirestore.getInstance() }

    var email     by remember { mutableStateOf("") }
    var password  by remember { mutableStateOf("") }
    var showPass  by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMsg  by remember { mutableStateOf("") }

    fun doLogin() {
        if (email.isBlank() || password.isBlank()) {
            errorMsg = "Vui lòng điền đầy đủ thông tin"
            return
        }
        isLoading = true
        errorMsg  = ""
        auth.signInWithEmailAndPassword(email.trim(), password)
            .addOnSuccessListener { result ->
                val uid = result.user?.uid ?: run {
                    isLoading = false
                    errorMsg  = "Không lấy được uid"
                    return@addOnSuccessListener
                }
                db.collection("users").document(uid).get()
                    .addOnSuccessListener { doc ->
                        isLoading = false
                        val role   = doc.getLong("role")?.toInt() ?: 0
                        val intent = if (role == 1) {
                            Intent(context, DashboardActivity::class.java)
                        } else {
                            Intent(context, ProductActivity::class.java)
                        }
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        context.startActivity(intent)
                    }
                    .addOnFailureListener { e ->
                        isLoading = false
                        errorMsg  = "Lỗi đọc dữ liệu: ${e.message}"
                    }
            }
            .addOnFailureListener { e ->
                isLoading = false
                errorMsg  = when {
                    e.message?.contains("no user record")    == true -> "Tài khoản không tồn tại"
                    e.message?.contains("password is invalid") == true -> "Mật khẩu không đúng"
                    e.message?.contains("badly formatted")   == true -> "Email không hợp lệ"
                    e.message?.contains("network error")     == true -> "Lỗi kết nối mạng"
                    else -> "Đăng nhập thất bại"
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
            // Nút Home — góc trên phải
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

            // Text hero — góc dưới trái
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
                    ) {
                        Text("🌿", fontSize = 16.sp)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "LUMIÈRE",
                        color         = Color.White,
                        fontSize      = 15.sp,
                        fontWeight    = FontWeight.SemiBold,
                        letterSpacing = 2.sp
                    )
                }
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    "Chào mừng",
                    color      = Color.White,
                    fontSize   = 26.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "trở lại!",
                    color      = Color.White.copy(alpha = 0.75f),
                    fontSize   = 26.sp,
                    fontWeight = FontWeight.Bold,
                    fontStyle  = FontStyle.Italic
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "Đăng nhập để khám phá bộ sưu tập mới nhất",
                    color    = Color.White.copy(alpha = 0.82f),
                    fontSize = 13.sp
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
                color         = MintGreen,
                fontSize      = 11.sp,
                fontWeight    = FontWeight.Medium,
                letterSpacing = 2.sp
            )

            Spacer(modifier = Modifier.height(20.dp))

            LoginFieldLabel("Email")
            OutlinedTextField(
                value         = email,
                onValueChange = { email = it; errorMsg = "" },
                modifier      = Modifier.fillMaxWidth(),
                placeholder   = { Text("yourname@email.com", color = Color(0xFFAAD8CE)) },
                shape         = RoundedCornerShape(14.dp),
                singleLine    = true,
                colors        = loginTextFieldColors()
            )

            Spacer(modifier = Modifier.height(14.dp))

            LoginFieldLabel("Mật khẩu")
            OutlinedTextField(
                value         = password,
                onValueChange = { password = it; errorMsg = "" },
                modifier      = Modifier.fillMaxWidth(),
                placeholder   = { Text("Mật khẩu", color = Color(0xFFAAD8CE)) },
                shape         = RoundedCornerShape(14.dp),
                singleLine    = true,
                visualTransformation = if (showPass) VisualTransformation.None
                else PasswordVisualTransformation(),
                trailingIcon  = {
                    IconButton(onClick = { showPass = !showPass }) {
                        Icon(
                            if (showPass) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = null,
                            tint = Color(0xFFAAD8CE)
                        )
                    }
                },
                colors = loginTextFieldColors()
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                "Quên mật khẩu?",
                fontSize   = 12.sp,
                color      = MintGreen,
                fontWeight = FontWeight.Medium,
                modifier   = Modifier
                    .align(Alignment.End)
                    .clickable { }
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (errorMsg.isNotEmpty()) {
                Text(
                    errorMsg,
                    color    = Color(0xFFF09595),
                    fontSize = 13.sp,
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
                    CircularProgressIndicator(
                        color       = Color.White,
                        modifier    = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        "Đăng nhập",
                        color         = Color.White,
                        fontSize      = 15.sp,
                        fontWeight    = FontWeight.Medium,
                        letterSpacing = 0.5.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Nút về trang chủ (không cần đăng nhập)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFFEAF9F5))
                    .clickable { onGoHome() },
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Home,
                        contentDescription = null,
                        tint     = MintGreen,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Tiếp tục không đăng nhập",
                        color      = MintGreen,
                        fontSize   = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Text("Chưa có tài khoản? ", fontSize = 12.sp, color = Color(0xFF8ACABA))
                Text(
                    "Đăng ký ngay",
                    fontSize   = 12.sp,
                    color      = MintGreen,
                    fontWeight = FontWeight.Medium,
                    modifier   = Modifier.clickable { onGoRegister() }
                )
            }
        }
    }
}

@Composable
private fun LoginFieldLabel(text: String) {
    Text(
        text       = text,
        color      = MintGreen,
        fontSize   = 11.sp,
        fontWeight = FontWeight.Medium,
        modifier   = Modifier.padding(bottom = 6.dp)
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
    AppBanMyPhamTheme {
        LoginScreen()
    }
}