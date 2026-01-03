plugins {
    alias(libs.plugins.android.application)
    id("com.google.gms.google-services")
}

android {
    namespace = "com.omarflex5"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.omarflex5"
        minSdk = 25
        targetSdk = 36
        versionCode = 4
        versionName = "4.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        

    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    // Product flavors for WebView vs GeckoView
    flavorDimensions += "engine"
    productFlavors {
        create("webview") {
            dimension = "engine"
            // Default variant - uses Android WebView
        }
        create("gecko") {
            dimension = "engine"
            // GeckoView variant - better CF bypass
            // GeckoView supports minSdk 21+, enabling Android 6 (API 23)
            minSdk = 23
            
            // CRITICAL: Force gecko flavor to run in 32-bit mode (armeabi-v7a)
            // This is required because we are only bundling the 32-bit GeckoView library to save space.
            ndk {
                abiFilters.add("armeabi-v7a")
            }
        }
    }
}

dependencies {
    // GeckoView - only for gecko flavor
    // Using v101 (last stable for API 23) with proven WebExtension support for cookie extraction
    val geckoVersion = "101.0.20220608170832"
    // Optimization: Target ONLY armeabi-v7a to reduce APK size
    "geckoImplementation"("org.mozilla.geckoview:geckoview-armeabi-v7a:$geckoVersion")
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.constraintlayout)
    implementation(libs.glide)
    annotationProcessor("com.github.bumptech.glide:compiler:4.16.0")
    implementation("com.github.bumptech.glide:okhttp3-integration:4.16.0") // OkHttp for cookie-aware images
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.lifecycle.livedata)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.exoplayer.dash)
    implementation(libs.media3.datasource)
    implementation("androidx.media3:media3-datasource-okhttp:1.2.0") 
    implementation(libs.media3.cast)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)

    // Networking
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.11.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.11.0")

    // Room Database
    implementation("androidx.room:room-runtime:2.6.1")
    annotationProcessor("androidx.room:room-compiler:2.6.1")

    // Firebase
    implementation(platform("com.google.firebase:firebase-bom:32.7.0"))
    implementation("com.google.firebase:firebase-firestore")

    // Gson for JSON serialization
    implementation("com.google.code.gson:gson:2.10.1")
    
    // Jsoup for HTML parsing
    implementation("org.jsoup:jsoup:1.17.2")

    // Google Cast
    implementation("com.google.android.gms:play-services-cast-framework:21.4.0")

    // Local Proxy Server
    implementation("org.nanohttpd:nanohttpd:2.3.1")

    // Local Broadcast Manager
    implementation("androidx.localbroadcastmanager:localbroadcastmanager:1.1.0")

    // WorkManager
    implementation("androidx.work:work-runtime:2.9.0")
}