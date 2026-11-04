package com.apex.lqserver

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import rikka.shizuku.Shizuku

class MainActivity : ComponentActivity() {

    private val shizukuGranted = mutableStateOf(false)

    private val shizukuPermissionListener =
        Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
            if (requestCode == SHIZUKU_PERMISSION_REQUEST_CODE) {
                shizukuGranted.value = grantResult == PackageManager.PERMISSION_GRANTED
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestNotificationPermissionIfNeeded()
        
        // Bọc try-catch để không văng app nếu Shizuku lỗi
        try {
            Shizuku.addRequestPermissionResultListener(shizukuPermissionListener)
            requestShizukuPermission()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        setContent {
            LQServerFixerTheme {
                MainScreen(
                    shizukuGranted = shizukuGranted.value,
                    onBackClick = { finish() },
                    onOptimizeClick = {
                        if (!shizukuGranted.value) {
                            try { requestShizukuPermission() } catch (e: Exception) { }
                        } else {
                            startOptimizerService()
                        }
                    }
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            Shizuku.removeRequestPermissionResultListener(shizukuPermissionListener)
        } catch (e: Exception) { }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1002)
            }
        }
    }

    private fun requestShizukuPermission() {
        if (!isShizukuInstalled()) {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=moe.shizuku.privileged.api")))
            } catch (_: Exception) { }
            return
        }

        if (Shizuku.isPreV11() || !Shizuku.pingBinder()) {
            Toast.makeText(this, "Shizuku chưa chạy. Vui lòng khởi động Shizuku!", Toast.LENGTH_LONG).show()
            return
        }

        if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            shizukuGranted.value = true
        } else {
            Shizuku.requestPermission(SHIZUKU_PERMISSION_REQUEST_CODE)
        }
    }

    private fun isShizukuInstalled(): Boolean = try {
        packageManager.getPackageInfo("moe.shizuku.privileged.api", 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    private fun startOptimizerService() {
        val intent = Intent(this, OptimizerService::class.java)
        ContextCompat.startForegroundService(this, intent)
    }

    companion object {
        private const val SHIZUKU_PERMISSION_REQUEST_CODE = 1001
    }
}

@Composable
fun LQServerFixerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = Color(0xFF141414),
            surface = Color(0xFF1C1C1E),
            primary = Color(0xFFD0BCFF),
            onBackground = Color.White,
            onSurface = Color.White
        ),
        content = content
    )
}

@Composable
fun MainScreen(
    shizukuGranted: Boolean,
    onBackClick: () -> Unit,
    onOptimizeClick: () -> Unit
) {
    // Dùng collectAsState() thay vì collectAsStateWithLifecycle() để tránh lỗi thư viện
    val progressState by OptimizerService.progressFlow.collectAsState()

    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF141414)) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp).padding(top = 4.dp, bottom = 16.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBackClick) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1E)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(56.dp).background(Color(0xFFFF7A00), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("5v5", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text("Liên Quân Mobile", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text("com.garena.game.kgvn", color = Color.Gray, fontSize = 12.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Card(
                modifier = Modifier.fillMaxWidth().weight(1f),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1E)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
                    val features = listOf(
                        "• Fix Lag Siêu Mượt (Còn 1,3GB)",
                        "• Chơi được đấu thường, rank, đỉnh cao, 3v3",
                        "• Nền sảnh, tổ đội",
                        "• Sử dụng map gốc",
                        "• Có cấu hình cao",
                        "• Không avt",
                        "• Có emote",
                        "• Có skin (Ko mod)",
                        "• Có âm thanh chiêu thức",
                        "• Xoá chi tiết tướng và quái",
                        "• Không các hiệu ứng đổ(ảo choàng băng...)"
                    )
                    features.forEach { Text(it, color = Color.White, fontSize = 14.sp, modifier = Modifier.padding(vertical = 4.dp)) }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("✖ Mỗi bản update chỉ optimize 1 lần.", color = Color(0xFFFF4444), fontWeight = FontWeight.Bold, fontSize = 14.sp)

                    when (val s = progressState) {
                        is ProgressState.Completed -> Text("✅ Tối ưu hoàn tất! Hãy mở lại game.", color = Color(0xFF4CAF50), fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
                        is ProgressState.Error -> Text("❌ Lỗi: ${s.message}", color = Color(0xFFFF4444), fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
                        else -> {}
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            val running = progressState as? ProgressState.Running
            val isRunning = running != null

            Button(
                onClick = onOptimizeClick,
                enabled = !isRunning,
                modifier = Modifier.fillMaxWidth().height(72.dp),
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isRunning) Color.Gray else Color(0xFFD0BCFF),
                    contentColor = Color.White,
                    disabledContainerColor = Color.Gray,
                    disabledContentColor = Color.White
                )
            ) {
                if (isRunning) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Đang tối ưu... ${running!!.percent}%", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Spacer(modifier = Modifier.height(6.dp))
                        LinearProgressIndicator(progress = running.percent / 100f, modifier = Modifier.fillMaxWidth(0.85f), color = Color(0xFFD0BCFF), trackColor = Color.DarkGray)
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (shizukuGranted) "Optimize" else "Cấp quyền Shizuku", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }
    }
}
