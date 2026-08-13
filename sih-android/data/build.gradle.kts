plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt.android)
}

android {
    namespace  = "com.sih.data"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release { isMinifyEnabled = false }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions { jvmTarget = "11" }
}

dependencies {
    // ── Module deps ──────────────────────────────────────────────────────
    api(project(":network"))   // expose network DTOs up to :app
    implementation(project(":relay"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services) // tasks.await() for FusedLocationProviderClient

    // ── Room ─────────────────────────────────────────────────────────────
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // ── WorkManager ──────────────────────────────────────────────────────
    implementation(libs.work.runtime.ktx)

    // ── Hilt (DI + HiltWorker) ────────────────────────────────────────────
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
    implementation(libs.hilt.work)
    ksp(libs.hilt.compiler)   // required for @HiltWorker / @AssistedInject

    // ── Moshi (TypeConverter JSON for Room) ───────────────────────────────
    implementation(libs.moshi.kotlin)

    // ── EncryptedSharedPreferences ────────────────────────────────────────
    implementation(libs.security.crypto)

    // ── Location (FusedLocationProviderClient for gateway location) ───────
    implementation(libs.play.services.location)

    // ── Testing ───────────────────────────────────────────────────────────
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.room.testing)
    testImplementation(libs.work.testing)
    testImplementation(libs.mockk)
}
