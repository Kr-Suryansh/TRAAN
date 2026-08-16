// :data — Android Library module
// Owner: Component C
// Responsibilities: Room DB, Room entities, DAOs, WorkManager upload jobs.
// Day 6: Populated with Component C's Room persistence infrastructure (ported
//        verbatim from the C repo) plus the RoomRelayDataSource that implements
//        RelayDataSource (from :relay) so the relay mesh persists SOS records
//        in Room instead of the temporary in-memory test store.
//
// Room annotation processing uses kapt (not KSP) because this build uses
// Kotlin 2.0.0 and kapt needs no version lockstep with the Kotlin plugin.

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    kotlin("kapt")
}

android {
    namespace  = "com.sih.data"
    compileSdk = 35

    defaultConfig {
        minSdk = 23
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    // ── Module deps ──────────────────────────────────────────────────────
    // :relay exposes the RelayDataSource interface + relay models. :data
    // implements the interface — dependency direction stays :relay ↓ interface
    // ↑ :data implementation (never :relay → :data).
    implementation(project(":relay"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)

    // kotlinx-serialization-json — used by the SOSRequest mapper to encode the
    // medical_snapshot JSON string (UserMedicalProfile is @Serializable in :relay).
    implementation(libs.kotlinx.serialization.json)

    // ── Room (persistence) ───────────────────────────────────────────────
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    kapt(libs.room.compiler)

    // ── Moshi (JSON for Room TypeConverters) ─────────────────────────────
    implementation(libs.moshi.kotlin)

    // ── EncryptedSharedPreferences (device identity) ─────────────────────
    implementation(libs.security.crypto)

    // ── Testing ──────────────────────────────────────────────────────────
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
}
