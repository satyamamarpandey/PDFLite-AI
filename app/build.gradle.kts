// app/build.gradle.kts
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.20"
}

val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

val telemetryBaseUrl = localProps.getProperty("TELEMETRY_BASE_URL")
    ?: "https://pdflite-ai-proxy.7satyampandey.workers.dev"

// Lightweight app token (not a true secret, but used for gating/rate limits on Worker)
val appToken = localProps.getProperty("APP_TOKEN") ?: ""

kotlin {
    jvmToolchain(17)
}

android {
    namespace = "com.pdfliteai"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.pdfliteai"
        minSdk = 26
        targetSdk = 36
        versionCode = 4
        versionName = "1.0.4"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "TELEMETRY_BASE_URL", "\"$telemetryBaseUrl\"")
        buildConfigField("String", "APP_TOKEN", "\"$appToken\"")

        // ABI filters (your existing setup)
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            isShrinkResources = false
            isDebuggable = true

            ndk {
                abiFilters.clear()
                abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
            }
        }

        release {
            // ✅ TEMP: stop Play internal crashes caused by R8/minify
            isMinifyEnabled = false
            isShrinkResources = false
            isDebuggable = false

            // ✅ Keep proguard files wired so you can re-enable later
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )

            // ✅ Helps remove Play Console “native debug symbols missing” warning
            // (see step #3 below for how to upload symbols)
            ndk {
                abiFilters.clear()
                abiFilters += listOf("arm64-v8a", "armeabi-v7a")
                debugSymbolLevel = "FULL"
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        jniLibs {
            useLegacyPackaging = false
        }
        resources {
            excludes += setOf(
                "META-INF/LICENSE*",
                "META-INF/NOTICE*",
                "META-INF/DEPENDENCIES"
            )
        }
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.navigation:navigation-compose:2.8.3")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    implementation("com.tom-roush:pdfbox-android:2.0.27.0")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    debugImplementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    implementation("com.google.android.gms:play-services-mlkit-text-recognition:19.0.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")

    implementation("androidx.work:work-runtime-ktx:2.9.0")

    implementation("com.github.mhiew:android-pdf-viewer:3.2.0-beta.1") {
        exclude(group = "com.android.support")
    }

    // ✅ Update (fixes known issues; current as of Feb 18, 2026)
    implementation("com.google.android.gms:play-services-auth:21.5.1")

    // ✅ Recommended modern Google sign-in stack (Credential Manager + GoogleID)
    implementation("androidx.credentials:credentials:1.5.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.2.0")

    // ✅ Optional but recommended for best support on Android 13 and below
    implementation("androidx.credentials:credentials-play-services-auth:1.5.0")
    implementation("com.android.billingclient:billing-ktx:7.1.1")
}