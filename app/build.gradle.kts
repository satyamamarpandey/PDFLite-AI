import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.20"
}

android {
    namespace = "com.pdfliteai"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.pdfliteai"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Read local.properties once
    val lp = Properties().apply {
        val f = rootProject.file("local.properties")
        if (f.exists()) f.inputStream().use { load(it) }
    }

    fun lpKey(name: String): String = (lp.getProperty(name, "") ?: "").trim()

    buildTypes {
        debug {
            // ✅ Debug can read from local.properties
            buildConfigField("String", "GROQ_KEY", "\"${lpKey("GROQ_KEY")}\"")
            buildConfigField("String", "OPENROUTER_KEY", "\"${lpKey("OPENROUTER_KEY")}\"")
            buildConfigField("String", "NOVA_KEY", "\"${lpKey("NOVA_KEY")}\"")

            // Optional: for Local compat if you ever want fallback
            buildConfigField("String", "LOCAL_COMPAT_KEY", "\"${lpKey("LOCAL_COMPAT_KEY")}\"")
        }

        release {
            // ⚠️ Don’t ship secrets in release BuildConfig
            buildConfigField("String", "GROQ_KEY", "\"\"")
            buildConfigField("String", "OPENROUTER_KEY", "\"\"")
            buildConfigField("String", "NOVA_KEY", "\"\"")
            buildConfigField("String", "LOCAL_COMPAT_KEY", "\"\"")

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

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
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
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    debugImplementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // ✅ OCR fallback (ML Kit)
    implementation("com.google.mlkit:text-recognition:16.0.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")
}
