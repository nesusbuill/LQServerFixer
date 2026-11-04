package com.apex.lqserver

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object NetworkManager {
    // SẾP THAY LINK GITHUB GIST RAW VÀO ĐÂY
    private const val CONFIG_URL = "https://gist.githubusercontent.com/username/id/raw/rules.json"

    fun fetchRules(): List<String> {
        // Danh sách mặc định phòng trường hợp rớt mạng không lấy được luật mới
        val defaultRules = listOf(
            "files/hero*", "files/unit*", "files/effect*", 
            "files/item*", "files/avatar*", "cache/*"
        )

        return try {
            val url = URL(CONFIG_URL)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 5000
            conn.readTimeout = 5000

            if (conn.responseCode == 200) {
                val text = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(text)
                val paths = mutableListOf<String>()
                val array = json.getJSONArray("delete_paths")
                for (i in 0 until array.length()) {
                    paths.add(array.getString(i))
                }
                return paths
            }
            defaultRules
        } catch (e: Exception) {
            e.printStackTrace()
            defaultRules
        }
    }
}
