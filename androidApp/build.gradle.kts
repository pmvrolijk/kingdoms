plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "nl.lamia.kingdoms"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "nl.lamia.kingdoms"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "0.1.0"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(projects.composeApp)
    implementation(libs.androidx.activity.compose)
}
