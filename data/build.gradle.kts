// :data — Android Library module
// Owner: Component C
// Responsibilities: Room DB, Room entities, DAOs, WorkManager upload jobs.
// Day 1: Minimum stub. Component C will add Room, entities, DAOs, and
//        implement RelayDataSource (from :relay) with a real Room DAO.

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
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
}

dependencies {
    // NOTE for Component C (Day 2+):
    // Add this dependency when implementing RelayDataSource with Room:
    //   implementation(project(":relay"))
    // This allows :data to see RelayDataSource and SOSRequest from :relay.
    // Room dependencies (room-runtime, room-ktx, kapt room-compiler) also go here.

    implementation(libs.androidx.core.ktx)
}
