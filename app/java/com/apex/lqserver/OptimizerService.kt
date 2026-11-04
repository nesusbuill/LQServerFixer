package com.apex.lqserver

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuRemoteProcess
import java.lang.reflect.Method

class OptimizerService : Service() {

    private val tag = "OptimizerService"
    private val channelId = "lqserver_optimizer_channel"
    private val notificationId = 2001

    private val gameBase = "/sdcard/Android/data/com.garena.game.kgvn"
    private val gamePkg = "com.garena.game.kgvn"
    private val lockPatterns = listOf("version", "localhash", "manifest*")
    private val largeFileSize = "2M"
    private val largeFileExcludes = listOf("*.xml", "*.json", "*.txt", "*.hash", "*.cfg")

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(notificationId, buildNotification(0, "Đang chuẩn bị..."))

        serviceScope.launch {
            try {
                runOptimization()
            } catch (e: Exception) {
                Log.e(tag, "Optimization failed", e)
                publish(ProgressState.Error(e.message ?: "Unknown error"))
                updateNotification(0, "Lỗi: ${e.message}")
            } finally {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private suspend fun runOptimization() {
        if (!Shizuku.pingBinder() || Shizuku.checkSelfPermission() != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            publish(ProgressState.Error("Shizuku chưa chạy hoặc chưa có quyền"))
            return
        }

        publish(ProgressState.Running(5, "Đang tải rules từ Server..."))
        updateNotification(5, "Đang tải rules...")

        val rules = try {
            NetworkManager.fetchRules(applicationContext)
        } catch (e: Exception) {
            publish(ProgressState.Error("Không tải được rules: ${e.message}"))
            return
        }

        publish(ProgressState.Running(20, "Khóa file version (chmod 444)..."))
        updateNotification(20, "Khóa file version...")
        lockVersionFiles()

        publish(ProgressState.Running(50, "Xóa tài nguyên nặng (v${rules.gameVersion})..."))
        updateNotification(50, "Xóa tài nguyên nặng...")
        deletePaths(rules)

        publish(ProgressState.Running(80, "Dọn file lớn còn sót..."))
        updateNotification(80, "Dọn file lớn...")
        deleteLargeFiles()

        publish(ProgressState.Running(95, "Khởi động lại game..."))
        updateNotification(95, "Khởi động lại game...")
        forceStop()

        publish(ProgressState.Completed)
        updateNotification(100, "Hoàn tất! Hãy mở lại game.")
    }

    private fun lockVersionFiles() {
        val nameClauses = lockPatterns.joinToString(" -o ") { "-name '$it'" }
        val findExpr = "find $gameBase -type f \\( $nameClauses \\) -exec chmod 444 {} \\;"
        sh("sh", "-c", findExpr)
    }

    private fun deletePaths(rules: OptimizeRules) {
        for (rel in rules.deletePaths) {
            val safeRel = sanitize(rel)
            if (safeRel.isEmpty()) continue
            val full = "$gameBase/$safeRel"
            val cmd = if (safeRel.contains('*')) "rm -rf $full" else "rm -rf '$full'"
            sh("sh", "-c", cmd)
        }
    }

    private fun sanitize(path: String): String {
        val allowed = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789/._-*"
        val sb = StringBuilder()
        for (c in path) if (c in allowed) sb.append(c)
        val cleaned = sb.toString().trim().trimStart('/')
        if (cleaned.contains("..")) return ""
        return cleaned
    }

    private fun deleteLargeFiles() {
        val excludeClauses = largeFileExcludes.joinToString(" ") { "! -name '$it'" }
        val cmd = "find $gameBase -type f -size +$largeFileSize $excludeClauses -delete"
        sh("sh", "-c", cmd)
    }

    private fun forceStop() {
        sh("am", "force-stop", gamePkg)
    }

    private fun sh(vararg command: String): Pair<String, String> {
        return try {
            val clazz = Class.forName("rikka.shizuku.Shizuku")
            val method: Method = clazz.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            )
            method.isAccessible = true
            val process = method.invoke(null, command, null, null) as ShizukuRemoteProcess
            val stdout = process.inputStream.bufferedReader().use { it.readText() }
            val stderr = process.errorStream.bufferedReader().use { it.readText() }
            process.waitFor()
            process.destroy()
            stdout.trim() to stderr.trim()
        } catch (e: Exception) {
            Log.e(tag, "sh() failed: ${e.message}", e)
            "" to (e.message ?: "Unknown error")
        }
    }

    private fun publish(state: ProgressState) {
        _progress.value = state
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "LQ Server Fixer", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(progress: Int, message: String): Notification =
        NotificationCompat.Builder(this, channelId)
            .setContentTitle("LQ Server Fixer")
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setProgress(100, progress, progress == 0)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

    private fun updateNotification(progress: Int, message: String) {
        getSystemService(NotificationManager::class.java).notify(notificationId, buildNotification(progress, message))
    }

    companion object {
        private val _progress = MutableStateFlow<ProgressState>(ProgressState.Idle)
        val progressFlow: StateFlow<ProgressState> = _progress.asStateFlow()
    }
}
