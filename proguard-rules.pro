# Noorix Learning - release minification rules.
# Referenced by the `release` build type (proguardFiles).

# --- OkHttp / Okio ---
-dontwarn okhttp3.**
-dontwarn okio.**

# --- Retrofit ---
-keepattributes Signature, *Annotation*, EnclosingMethod, InnerClasses
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}

# --- Moshi (reflection on model fields; KSP codegen adds most of this,
#      these rules cover custom adapters and annotations) ---
-keepclassmembers @com.squareup.moshi.JsonClass class * {
    <fields>;
}
-keepclassmembers class * {
    @com.squareup.moshi.FromJson *;
    @com.squareup.moshi.ToJson *;
}
-keepnames enum **
-dontwarn org.jetbrains.annotations.**
