// AGP 9 has built-in Kotlin support, so org.jetbrains.kotlin.android must NOT be
// applied here: applying it fails the build. See research.md R8.
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.dnoel.binauralbeats"
    // compileSdk 37 is the maximum AGP 9.4.0 supports; targetSdk 36 satisfies the
    // Play requirement without opting into API 37 behavior changes this feature
    // does not need. minSdk 26 is the floor for AudioFocusRequest and notification
    // channels. See research.md R8.
    compileSdk = 37

    defaultConfig {
        applicationId = "com.dnoel.binauralbeats"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    buildFeatures {
        compose = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":core"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.datastore)
    implementation(libs.kotlinx.serialization.json)

    // JUnit 4 here rather than Jupiter, because Robolectric's runner requires it.
    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlinx.coroutines.test)
}
