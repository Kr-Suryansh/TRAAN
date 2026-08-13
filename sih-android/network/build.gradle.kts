plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
}

android {
    namespace  = "com.sih.network"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
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
    kotlinOptions { jvmTarget = "11" }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)

    // ── Retrofit + OkHttp ─────────────────────────────────────────────────
    api(libs.retrofit)
    implementation(libs.retrofit.converter.moshi)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)

    // ── Moshi (KSP codegen for network DTOs) ──────────────────────────────
    implementation(libs.moshi.kotlin)
    ksp(libs.moshi.kotlin.codegen)

    // ── Testing ───────────────────────────────────────────────────────────
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
}
