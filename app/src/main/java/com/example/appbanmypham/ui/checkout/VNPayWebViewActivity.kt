// ui/checkout/VNPayWebViewActivity.kt
package com.example.appbanmypham.ui.checkout

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.webkit.*
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.appbanmypham.ui.theme.AppBanMyPhamTheme
import com.example.appbanmypham.ui.theme.AppGradients
import com.example.appbanmypham.ui.theme.MintGreen
import androidx.compose.foundation.background

class VNPayWebViewActivity : ComponentActivity() {

    companion object {
        const val EXTRA_PAYMENT_URL = "payment_url"
        const val RESULT_VNPAY_URL  = "vnpay_return_url"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Nhận deep link nếu mở từ redirect
        val deepLinkUrl = intent?.data?.toString()
        if (deepLinkUrl != null && deepLinkUrl.startsWith("appbanmypham://vnpay_return")) {
            val result = Intent().apply { putExtra(RESULT_VNPAY_URL, deepLinkUrl) }
            setResult(RESULT_OK, result)
            finish()
            return
        }

        val paymentUrl = intent.getStringExtra(EXTRA_PAYMENT_URL) ?: run { finish(); return }

        setContent {
            AppBanMyPhamTheme {
                VNPayWebViewScreen(
                    paymentUrl = paymentUrl,
                    onBack     = { finish() },
                    onReturnUrl = { url ->
                        val result = Intent().apply { putExtra(RESULT_VNPAY_URL, url) }
                        setResult(RESULT_OK, result)
                        finish()
                    }
                )
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun VNPayWebViewScreen(
    paymentUrl  : String,
    onBack      : () -> Unit,
    onReturnUrl : (String) -> Unit
) {
    var isLoading by remember { mutableStateOf(true) }
    var pageTitle by remember { mutableStateOf("Thanh toán VNPay") }

    Scaffold(
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(AppGradients.mintHorizontal)
                    .statusBarsPadding()
                    .padding(horizontal = 4.dp, vertical = 4.dp)
            ) {
                IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart)) {
                    Icon(Icons.Default.ArrowBack, null, tint = Color.White)
                }
                Column(modifier = Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(pageTitle, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text("🔒 Kết nối bảo mật SSL", color = Color.White.copy(0.75f), fontSize = 10.sp)
                }
                if (isLoading)
                    CircularProgressIndicator(
                        color       = Color.White,
                        modifier    = Modifier.size(20.dp).align(Alignment.CenterEnd).padding(end = 16.dp),
                        strokeWidth = 2.dp
                    )
            }
        }
    ) { padding ->
        AndroidView(
            modifier = Modifier.fillMaxSize().padding(padding),
            factory  = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled      = true
                    settings.domStorageEnabled       = true
                    settings.setSupportMultipleWindows(true)
                    settings.mixedContentMode        = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW

                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                            val url = request.url.toString()
                            // Bắt deep link redirect từ VNPay
                            if (url.startsWith("appbanmypham://vnpay_return")) {
                                onReturnUrl(url)
                                return true
                            }
                            return false
                        }

                        override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
                            isLoading = true
                        }

                        override fun onPageFinished(view: WebView, url: String?) {
                            isLoading = false
                            pageTitle = view.title?.takeIf { it.isNotBlank() } ?: "Thanh toán VNPay"
                        }
                    }
                    loadUrl(paymentUrl)
                }
            }
        )
    }
}