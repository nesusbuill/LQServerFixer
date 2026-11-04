# Giữ lại các lớp của Shizuku để tránh lỗi reflection
-keep class rikka.shizuku.** { *; }
-keep class moe.shizuku.** { *; }
-keep class androidx.compose.ui.platform.AndroidCompositionLocals_androidKt { *; }

# Giữ lại các hàm native
-keepclasseswithmembernames class * {
    native <methods>;
}

# Giữ lại các lớp Compose
-keep class androidx.compose.runtime.** { *; }
