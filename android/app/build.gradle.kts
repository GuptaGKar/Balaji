import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.dagger.hilt.android")
    id("kotlin-kapt")
}

android {
    namespace = "com.balaji.provision"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.balaji.provision"
        minSdk = 24
        targetSdk = 36
        versionCode = 8
        versionName = "1.7"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
        val supabaseUrl = (project.findProperty("SUPABASE_URL") as String?)
            ?: System.getenv("SUPABASE_URL")
            ?: "https://czbwwnlnmhhzketyjnzt.supabase.co/"
        val supabaseAnonKey = (project.findProperty("SUPABASE_ANON_KEY") as String?)
            ?: System.getenv("SUPABASE_ANON_KEY")
            ?: "sb_publishable_pr_hXgQlfx4W-Z4HETIdBg_VoNIBEpo"
        buildConfigField("String", "SUPABASE_URL", "\"$supabaseUrl\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"$supabaseAnonKey\"")
        val staffWebUrl = (project.findProperty("STAFF_WEB_URL") as String?)
            ?: System.getenv("STAFF_WEB_URL")
            ?: ""
        buildConfigField(
            "String",
            "STAFF_WEB_URL",
            "\"${staffWebUrl.replace("\\", "\\\\").replace("\"", "\\\"")}\""
        )
        val storeWebUrl = (project.findProperty("STORE_WEB_URL") as String?)
            ?: System.getenv("STORE_WEB_URL")
            ?: ""
        buildConfigField(
            "String",
            "STORE_WEB_URL",
            "\"${storeWebUrl.trimEnd('/').replace("\\", "\\\\").replace("\"", "\\\"")}\""
        )
    }

    flavorDimensions += "audience"
    productFlavors {
        create("customer") {
            dimension = "audience"
            applicationId = "com.balaji.provision"
            resValue("string", "app_name", "Balaji Provision Store")
            buildConfigField("boolean", "IS_STAFF", "false")
        }
        create("staff") {
            dimension = "audience"
            applicationId = "com.balaji.provision.staff"
            resValue("string", "app_name", "Balaji Staff")
            buildConfigField("boolean", "IS_STAFF", "true")
        }
    }

    val keystorePropertiesFile = rootProject.file("keystore.properties")
    val keystoreProperties = Properties().apply {
        if (keystorePropertiesFile.exists()) {
            keystorePropertiesFile.inputStream().use { load(it) }
        }
    }

    signingConfigs {
        create("release") {
            if (keystorePropertiesFile.exists()) {
                val configuredStore = keystoreProperties.getProperty("storeFile")
                storeFile = configuredStore?.let { rootProject.file(it) }
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
                enableV4Signing = true
            }
        }
    }

    // R8 stays on for release by default. A build without shrinking can be produced to
    // isolate a minification-only failure on a real device:
    //   ./gradlew :app:assembleCustomerRelease -Pbalaji.minifyRelease=false
    // Both variants share the upload key and versionCode, so either installs over the other.
    val minifyRelease = (project.findProperty("balaji.minifyRelease") as String?)
        ?.toBoolean() ?: true

    buildTypes {
        release {
            isMinifyEnabled = minifyRelease
            isShrinkResources = minifyRelease
            isDebuggable = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            isMinifyEnabled = false
            isDebuggable = true
        }
    }
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    
    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api"
        )
    }
    
    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.6"
    }
    
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1,LICENSE,NOTICE}"
        }
    }
}

dependencies {
    // Core Android
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.compose.ui:ui:1.5.4")
    implementation("androidx.compose.ui:ui-graphics:1.5.4")
    implementation("androidx.compose.ui:ui-tooling-preview:1.5.4")
    implementation("androidx.compose.material3:material3:1.1.2")
    implementation("androidx.compose.material3:material3-window-size-class:1.1.2")
    implementation("androidx.compose.material:material-icons-extended:1.5.4")
    implementation("androidx.constraintlayout:constraintlayout-compose:1.0.1")
    
    // Navigation
    implementation("androidx.navigation:navigation-compose:2.7.5")
    
    // Hilt DI
    implementation("com.google.dagger:hilt-android:2.48")
    kapt("com.google.dagger:hilt-compiler:2.48")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")
    
    // Coroutines & Flow
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")

    // Location
    implementation("com.google.android.gms:play-services-location:21.3.0")
    
    // DataStore (cart/session persistence)
    implementation("androidx.datastore:datastore-preferences:1.0.0")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("androidx.datastore:datastore-preferences-rxjava3:1.0.0")
    implementation("io.reactivex.rxjava3:rxjava:3.1.8")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-rx3:1.7.3")
    
    // Networking (Retrofit + OkHttp)
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
    implementation("com.jakewharton.retrofit:retrofit2-kotlinx-serialization-converter:1.0.0")
    
    // Image loading
    implementation("io.coil-kt:coil-compose:2.5.0")
    
    // UI Testing
    debugImplementation("androidx.compose.ui:ui-test-manifest:1.5.4")
    debugImplementation("androidx.compose.ui:ui-test-junit4:1.5.4")
    
    // Unit Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.1.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    testImplementation("org.mockito:mockito-core:5.7.0")
    
    // Android UI Testing
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4:1.5.4")
}

kapt {
    correctErrorTypes = true
}