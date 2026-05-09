# Add project specific ProGuard rules here.

# --- Сохранение данных для сериализации/десериализации (Gson/Moshi/Kotlinx Serialization) ---
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes InnerClasses
-keepattributes EnclosingMethod

# --- Kotlin ---
-keep class kotlin.** { *; }
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**
-keepclassmembers class **$WhenMappings {
    <fields>;
}
-keepclassmembers class kotlin.coroutines.jvm.internal.BaseContinuationImpl {
    <methods>;
}
-dontwarn kotlin.coroutines.jvm.internal.BaseContinuationImpl

# --- Kotlin Coroutines ---
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# --- AndroidX Compose ---
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# --- CameraX ---
-keep class androidx.camera.** { *; }
-dontwarn androidx.camera.**

# --- OkHttp ---
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# --- Наши модели данных (Data classes) ---
-keep class com.example.vizoeye.data.** { *; }
-keep class com.example.vizoeye.domain.** { *; }

# --- SettingsManager и другие классы с reflection ---
-keep class com.example.vizoeye.SettingsManager { *; }
-keep class com.example.vizoeye.AppContainer { *; }
-keep class com.example.vizoeye.VizoEyeApplication { *; }

# --- TtsManager и SoundManager ---
-keep class com.example.vizoeye.TtsManager { *; }
-keep class com.example.vizoeye.SoundManager { *; }

# --- ViewModel ---
-keep class com.example.vizoeye.ui.main.MainViewModel { *; }

# --- Исправление ошибок R8 для missing classes (errorprone) ---
-dontwarn com.google.errorprone.annotations.**
