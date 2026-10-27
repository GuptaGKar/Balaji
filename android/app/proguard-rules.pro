# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# Retrofit & OkHttp
-dontwarn retrofit2.**
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class retrofit2.** { *; }
-keep interface retrofit2.** { *; }
-keepattributes Signature, *Annotation*, InnerClasses, EnclosingMethod
-keepclassmembers class * {
    @retrofit2.http.* <methods>;
}

# Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod
-dontwarn kotlinx.serialization.**
-keep class kotlinx.serialization.** { *; }
-keepclassmembers class kotlinx.serialization.** { *; }
-keep @kotlinx.serialization.Serializable class * { *; }
-keepclassmembers @kotlinx.serialization.Serializable class * {
    *;
}
-keepclassmembers class * {
    @kotlinx.serialization.Serializable *;
}
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclassmembers class * {
    *** Companion;
}
-keep class * implements kotlinx.serialization.KSerializer { *; }

# Balaji Domain Models, Network DTOs, and Interfaces (preserve field names for JSON mapping)
-keep class com.balaji.provision.domain.model.** { *; }
-keepclassmembers class com.balaji.provision.domain.model.** { *; }
-keep class com.balaji.provision.data.network.** { *; }
-keepclassmembers class com.balaji.provision.data.network.** { *; }
-keep class com.balaji.provision.data.repository.** { *; }
-keepclassmembers class com.balaji.provision.data.repository.** { *; }
-keep class com.balaji.provision.data.auth.** { *; }
-keepclassmembers class com.balaji.provision.data.auth.** { *; }
-keep class com.balaji.provision.ui.** { *; }

# Hilt & Dagger
-dontwarn dagger.hilt.**
-keep class dagger.hilt.** { *; }

# Coroutines
-dontwarn kotlinx.coroutines.**
-keep class kotlinx.coroutines.** { *; }

# DataStore
-dontwarn androidx.datastore.**
-keep class androidx.datastore.** { *; }
-keepclassmembers class androidx.datastore.** { *; }

# Coil
-dontwarn io.coil.**
-keep class io.coil.** { *; }

# Retrofit Converter for Kotlinx Serialization
-dontwarn com.jakewharton.retrofit.**
-keep class com.jakewharton.retrofit.** { *; }

# Preserve line numbers and source file names for crash analysis
-keepattributes SourceFile, LineNumberTable