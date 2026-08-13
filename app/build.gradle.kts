// :app — Android Application module
// Owner: Component C
// This is the citizen-facing app shell. Component A+B (:relay) is consumed from here.
//
// Day 1: Minimum stub. Component C will fill in Jetpack Compose, Room wiring,
//        WorkManager, navigation, and RelayDataSource injection.

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace  = "com.sih.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.sih.app"
        minSdk        = 23
        targetSdk     = 35
        versionCode   = 1
        versionName   = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
    // :relay — A+B component. :app calls startRelay(), stopRelay(), getRelayStore().
    // :app is also responsible for injecting a RelayDataSource implementation into RelayManager.
    implementation(project(":relay"))

    // :data — Component C. :app wires the Room-backed RelayDataSource from :data into :relay.
    implementation(project(":data"))

    // :network — Retrofit client for backend upload (WorkManager gateway job lives here/in :data).
    implementation(project(":network"))

    implementation(libs.androidx.core.ktx)
}
