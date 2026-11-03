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

# Retrofit reflects on the generic signature of every declared method
# (Response<List<ProfileResponse>> etc.). Keeping only the annotated members is not
# enough: R8 also has to keep the interface itself, otherwise it strips the method
# Signature and the first call dies with
#   ClassCastException: java.lang.Class cannot be cast to ParameterizedType
#   at retrofit2.HttpServiceMethod.parseAnnotations
-if interface * { @retrofit2.http.* <methods>; }
-keep,allowobfuscation interface <1>
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-keepattributes Signature,InnerClasses,EnclosingMethod,AnnotationDefault

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

# MapLibre GL (delivery pin picker)
# The Java/Kotlin layer is called from the C++ renderer through JNI by name, so it must
# survive minification. Getting this wrong only shows up in a shrunk build - the map
# renders blank or crashes at style load - so it is kept here rather than trusting the
# AAR's consumer rules alone.
-dontwarn org.maplibre.**
-keep class org.maplibre.android.** { *; }
-keep class org.maplibre.geojson.** { *; }
-keepclassmembers class org.maplibre.android.** {
    native <methods>;
    <fields>;
}