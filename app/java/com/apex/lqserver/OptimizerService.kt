package com.apex.lqserver

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.*
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuRemoteProcess
import java.lang.reflect.InvocationTargetException

class OptimizerService : Service() {
    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)
    private val LQ_PKG = "com.garena.game.kgvn"
    private val CHANNEL_ID = "lq_server_ch"

    override fun onCreate() {
        super.onCreate()
        try {
            createNotificationChannel()
            val n = buildNotif("Đang khởi tạo...", 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceCompat.startForeground(this, 1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(1, n)
            }
        } catch (_: Exception) {}
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        scope.launch {
            try {
                val extDir = "/sdcard/Android/data/$LQ_PKG"
                val dataDir = "/data/data/$LQ_PKG"

                emit(10, "Đang kéo luật từ Server...")
                val pathsToDelete = NetworkManager.fetchRules()

                emit(20, "Khóa file Version chống tải lại...")
                sh("find $extDir -name '*version*' -exec chmod 444 {} \\; 2>/dev/null")
                sh("find $extDir -name 'localhash' -exec chmod 444 {} \\; 2>/dev/null")
                sh("find $extDir -name 'manifest*' -exec chmod 444 {} \\; 2>/dev/null")
                delay(500)

                var currentProgress = 30
                val step = 55 / (if (pathsToDelete.isEmpty()) 1 else pathsToDelete.size)

                for (path in pathsToDelete) {
                    emit(currentProgress, "Xóa: $path")
                    sh("rm -rf $extDir/$path 2>/dev/null")
                    currentProgress += step
                    delay(100)
                }

                emit(85, "Quét xóa file Model nặng...")
                sh("find $extDir/files -type f \\( -name '*.bundle' -o -name '*.assets' \\) -size +2M -delete 2>/dev/null")

                sh("rm -rf $extDir/cache/* 2>/dev/null")
                sh("rm -rf $dataDir/cache/* 2>/dev/null")
                sh("find $dataDir -path '*cache*shader*' -delete 2>/dev/null")

                emit(95, "Force-stop Liên Quân...")
                sh("am force-stop $LQ_PKG")
                delay(800)

                emit(100, "Hoàn tất! Đã xóa theo luật Server.")
                ProgressState.finish(); delay(2500); stopSelf()

            } catch (e: Exception) {
                e.printStackTrace(); ProgressState.reset(); stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun emit(p: Int, msg: String) {
        ProgressState.update(p, msg); updateNotif(msg, p)
    }

    private fun sh(cmd: String) {
        try {
            if (Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                val c = Class.forName("rikka.shizuku.Shizuku")
                val m = c.getDeclaredMethod("newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java)
                m.isAccessible = true
                (m.invoke(null, arrayOf("sh", "-c", cmd), null, null) as ShizukuRemoteProcess).waitFor()
            }
        } catch (_: InvocationTargetException) { } catch (_: Exception) { }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL_ID, "LQ Server Fixer", NotificationManager.IMPORTANCE_LOW)
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(ch)
        }
    }

    private fun buildNotif(t: String, p: Int): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("LQ Server Fixer")
            .setContentText(t)
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setProgress(100, p, false)
            .setOngoing(true)
            .build()

    private fun updateNotif(t: String, p: Int) {
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(1, buildNotif(t, p))
    }

    override fun onDestroy() { job.cancel(); super.onDestroy() }
    override fun onBind(intent: Intent?): IBinder? = null
}
