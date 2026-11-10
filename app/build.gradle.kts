plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.gms.google-services")
}

android {
    namespace = "com.sangam.messenger"
    compileSdk = 34

    defaultConfig {
        // Only the ABIs real phones use. Shipping mips/x86/armeabi as well
        // tripled the download for no benefit.
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }

        applicationId = "com.sangam.messenger"
        minSdk = 26
        targetSdk = 34
        versionCode = 129
        versionName = "2.14.2"

        // Backend connection is injected at build time (CI env / -P properties).
        val supabaseUrl: String = (project.findProperty("SUPABASE_URL") as String?)
            ?: System.getenv("SUPABASE_URL") ?: ""
        val supabaseKey: String = (project.findProperty("SUPABASE_ANON_KEY") as String?)
            ?: System.getenv("SUPABASE_ANON_KEY") ?: ""
        buildConfigField("String", "SUPABASE_URL", "\"$supabaseUrl\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"$supabaseKey\"")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    signingConfigs {
        create("release") {
            val ksFile = file("release.keystore")
            if (ksFile.exists()) {
                storeFile = ksFile
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = if (file("release.keystore").exists()) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.03")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.8.0")
    // Fingerprint unlock for the app lock
    implementation("androidx.biometric:biometric:1.1.0")

    // Image loading for profile photos
    implementation("io.coil-kt:coil-compose:2.7.0")

    // Reads photo orientation so portrait photos stay portrait
    implementation("androidx.exifinterface:exifinterface:1.3.7")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.1")

    // Supabase backend (accounts, message relay, encrypted image blobs)
    implementation("io.github.jan-tennert.supabase:gotrue-kt:2.6.1")
    implementation("io.github.jan-tennert.supabase:postgrest-kt:2.6.1")
    implementation("io.github.jan-tennert.supabase:storage-kt:2.6.1")
    implementation("io.ktor:ktor-client-android:2.3.12")

    // Firebase Cloud Messaging (push notifications)
    implementation(platform("com.google.firebase:firebase-bom:33.2.0"))
    implementation("com.google.firebase:firebase-messaging")

    // Signal protocol (X3DH + Double Ratchet)
    implementation("org.whispersystems:signal-protocol-android:2.8.1")

    // Encrypted on-device key storage
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // QR scanning: CameraX for the preview, ML Kit to read the code.
    implementation("androidx.camera:camera-core:1.3.4")
    implementation("androidx.camera:camera-camera2:1.3.4")
    implementation("androidx.camera:camera-lifecycle:1.3.4")
    implementation("androidx.camera:camera-view:1.3.4")
    // Unbundled: the model lives in Play Services instead of the APK, which
    // keeps the download ~14MB smaller. Needs Play Services on the phone.
    implementation("com.google.android.gms:play-services-mlkit-barcode-scanning:18.3.1")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
