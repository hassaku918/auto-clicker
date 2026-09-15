plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.example.autoclickerblocker"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.autoclickerblocker"
        minSdk = 26
        targetSdk = 36
        versionCode = 3
        versionName = "3.0"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.compose.ui:ui:1.9.1")
    implementation("androidx.compose.ui:ui-tooling-preview:1.9.1")
    implementation("androidx.compose.material3:material3:1.3.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.3")
}
