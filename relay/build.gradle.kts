plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt.android)
}

android {
    namespace  = "com.sih.relay"
    compileSdk = 35

    defaultConfig {
        minSdk = 23  // Android 6.0 — minimum for modern BLE + runtime permissions
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // ── Module deps ───────────────────────────────────────────────────────
    implementation(project(":network"))  // for SosRequestDto in RelayRepository

    // Nearby Connections API — core transport layer for phone-to-phone relay.
    implementation(libs.play.services.nearby)

    // Coroutines — provides Flow<List<SOSRequest>> for getRelayStore() in RelayApi.
    implementation(libs.kotlinx.coroutines.android)

    // JSON serialization — encodes SOSRequest and RelayManifest to/from byte arrays
    // for transfer over Nearby Connections payload channel.
    implementation(libs.kotlinx.serialization.json)

    // Android KTX — Kotlin extension utilities
    implementation(libs.androidx.core.ktx)

    // ── Hilt (for RelayRepository @Singleton @Inject) ─────────────────────
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)

    // ── Testing ───────────────────────────────────────────────────────────
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
