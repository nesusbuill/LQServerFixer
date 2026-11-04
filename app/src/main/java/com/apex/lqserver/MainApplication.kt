package com.apex.lqserver

import android.app.Application

class MainApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Khởi tạo an toàn chống văng
        try {
            // Bất kỳ init nào cần thiết
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
