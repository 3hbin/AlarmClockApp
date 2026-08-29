plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.gms.google-services")
}

android {
    namespace = "com.example.alarmclock"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.alarmclock"
        minSdk = 28
        targetSdk = 34
        versionCode = 35
        versionName = "3.27"
        buildConfigField("String", "SPOTIFY_CLIENT_ID", "\"d0d25405fe2640608a6611b6cfdf1b44\"")
        buildConfigField("String", "YOUTUBE_API_KEY", "\"AIzaSyDeh5FsoNyVKEURsSLeSmx4DNp_rJfdD5M\"")
        buildConfigField("String", "WEATHER_API_KEY", "\"82b5a70ad1ea5e93d8482e0c17712f93\"")
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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    implementation("androidx.coordinatorlayout:coordinatorlayout:1.2.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")

    // Firebase
    implementation(platform("com.google.firebase:firebase-bom:33.1.2"))
    implementation("com.google.firebase:firebase-firestore-ktx")
    implementation("com.google.firebase:firebase-auth-ktx")

    // Network for weather
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.android.gms:play-services-location:21.3.0")

    // CameraX + ML Kit Face (quét mặt tự làm, không dùng Biometric hệ thống)
    // Guava ListenableFuture (CameraX ProcessCameraProvider)
    implementation("com.google.guava:guava:33.0.0-android")
    implementation("androidx.concurrent:concurrent-futures:1.2.0")
    implementation("androidx.camera:camera-core:1.3.4")
    implementation("androidx.camera:camera-camera2:1.3.4")
    implementation("androidx.camera:camera-lifecycle:1.3.4")
    implementation("androidx.camera:camera-view:1.3.4")
    implementation("com.google.mlkit:face-detection:16.1.6")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("com.google.android.gms:play-services-auth:21.2.0")
}
