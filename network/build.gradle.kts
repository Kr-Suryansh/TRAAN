// :network — Android Library module
// Owner: Shared (see day1-contracts-and-repo-setup.md §4)
// Responsibilities: Retrofit client, network models matching §1/§2 of the contract,
//                  authentication token management, SOS batch upload call.
// Day 1: Minimum stub.

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace  = "com.sih.network"
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
}

dependencies {
    // NOTE: Retrofit, OkHttp, and Kotlinx Serialization converter go here (Day 2+).
    implementation(libs.androidx.core.ktx)
}
