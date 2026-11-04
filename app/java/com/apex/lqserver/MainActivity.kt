package com.apex.lqserver

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import rikka.shizuku.Shizuku

class MainActivity : ComponentActivity() {

    private val shizukuGranted = mutableStateOf(false)

    private val shizukuPermissionListener =
        Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
            if (requestCode == 1001) {
                shizukuGranted.value = grantResult == PackageManager.PERMISSION_GRANTED
                if (shizukuGranted.value) {
                    Toast.makeText(this, "Đã cấp quyền Shizuku!", Toast.LENGTH_SHORT).show()
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Xin quyền tự động khi mở App
        checkAndRequestPermissions()

        try {
            Shizuku.addRequestPermissionResultListener(shizukuPermissionListener)
            requestShizukuPermission()
        } catch (e: Throwable) {
            e.printStackTrace()
        }

        setContent {
            LQServerFixerTheme {
                try {
                    MainScreen(
                        shizukuGranted = shizukuGranted.value,
                        onBackClick = { finish() },
                        onOptimizeClick = {
                            if (!shizukuGranted.value) {
                                try { requestShizukuPermission() } catch (e: Throwable) { }
                            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
                                openAllFilesAccessSettings()
                            } else {
                                val intent = Intent(this@MainActivity, OptimizerService::class.java)
                                ContextCompat.startForegroundService(this@MainActivity, intent)
                            }
                        }
                    )
                } catch (e: Throwable) {
                    // Bắt lỗi UI (chống văng)
                    Column(
                        modifier = Modifier.fillMaxSize().background(Color.Red).padding(16.dp),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text("LỖI UI: ${e.message}", color = Color.White, fontWeight = FontWeight.Bold)
                        Text(e.stackTraceToString(), color = Color.White, fontSize = 10.sp)
                    }
                }
            }
        }
    }

    // Hàm tự động xin quyền cho mọi Android
    private fun checkAndRequestPermissions() {
        // 1. Android 13+ (Xin quyền Thông báo)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1002)
            }
        }

        // 2. Android 6 đến 10 (Xin quyền Bộ nhớ thường)
        if (Build.VERSION.SDK_INT in Build.VERSION_CODES.M..Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.WRITE_EXTERNAL_STORAGE, android.Manifest.permission.READ_EXTERNAL_STORAGE), 1003)
            }
        }

        // 3. Android 11+ (Xin quyền Truy cập tất cả file - Hiện cảnh báo)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                openAllFilesAccessSettings()
            }
        }
    }

    // Mở bảng cài đặt xin quyền "Tất cả file"
    private fun openAllFilesAccessSettings() {
        try {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
            intent.addCategory("android.intent.category.DEFAULT")
            intent.data = Uri.parse("package:$packageName")
            startActivity(intent)
            Toast.makeText(this, "Vui lòng cấp quyền 'Truy cập tất cả file' để tối ưu!", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            // Fallback nếu máy không hỗ trợ
            val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
            startActivity(intent)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            Shizuku.removeRequestPermissionResultListener(shizukuPermissionListener)
        } catch (e: Throwable) { }
    }

    private fun requestShizukuPermission() {
        try {
            if (!isShizukuInstalled()) {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=moe.shizuku.privileged.api")))
                return
            }
            if (Shizuku.isPreV11() || !Shizuku.pingBinder()) {
                Toast.makeText(this, "Shizuku chưa chạy. Vui lòng khởi động Shizuku!", Toast.LENGTH_LONG).show()
                return
            }
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                shizukuGranted.value = true
            } else {
                Shizuku.requestPermission(1001)
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    private fun isShizukuInstalled(): Boolean = try {
        packageManager.getPackageInfo("moe.shizuku.privileged.api", 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }
}

@Composable
fun LQServerFixerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = Color(0xFF141414),
            surface = Color(0xFF1C1C1E),
            primary = Color(0xFFD0BCFF)
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
    val progressState by OptimizerService.progressFlow.collectAsState()

    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF141414)) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBackClick) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1E))
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(56.dp).background(Color(0xFFFF7A00), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                        Text("5v5", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text("Liên Quân Mobile", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text("com.garena.game.kgvn", color = Color.Gray, fontSize = 12.sp)
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                modifier = Modifier.fillMaxWidth().weight(1f),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1E))
            ) {
                Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
                    listOf(
                        "• Fix Lag Siêu Mượt (Còn 1,3GB)", "• Chơi được đấu thường, rank, đỉnh cao, 3v3",
                        "• Nền sảnh, tổ đội", "• Sử dụng map gốc", "• Có cấu hình cao",
                        "• Không avt", "• Có emote", "• Có skin (Ko mod)", "• Có âm thanh chiêu thức",
                        "• Xoá chi tiết tướng và quái", "• Không các hiệu ứng đổ(ảo choàng băng...)"
                    ).forEach { Text(it, color = Color.White, fontSize = 14.sp, modifier = Modifier.padding(vertical = 4.dp)) }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("✖ Mỗi bản update chỉ optimize 1 lần.", color = Color(0xFFFF4444), fontWeight = FontWeight.Bold)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            val running = progressState as? ProgressState.Running
            Button(
                onClick = onOptimizeClick,
                enabled = running == null,
                modifier = Modifier.fillMaxWidth().height(72.dp),
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(containerColor = if (running != null) Color.Gray else Color(0xFFD0BCFF))
            ) {
                if (running != null) {
                    Text("Đang tối ưu... ${running.percent}%", color = Color.White, fontWeight = FontWeight.Bold)
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (shizukuGranted) "Optimize" else "Cấp quyền Shizuku", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
