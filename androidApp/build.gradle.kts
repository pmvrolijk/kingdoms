plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

/** The app version (Design §2.4), and the version code derived from it: 1.2.3 → 10203. */
val appVersion: String = providers.gradleProperty("kingdoms.version").getOrElse(libs.versions.kingdoms.get())
val appVersionCode = appVersion.split(".").map { it.toInt() }.let { (major, minor, patch) -> major * 10_000 + minor * 100 + patch }

/** Release signing, only when the keystore is given (CI decodes it from repository secrets). */
val keystore = providers.environmentVariable("KINGDOMS_KEYSTORE")

android {
    namespace = "nl.lamia.kingdoms"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "nl.lamia.kingdoms"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = appVersionCode
        versionName = appVersion
    }
    signingConfigs {
        if (keystore.isPresent) {
            create("release") {
                storeFile = file(keystore.get())
                storePassword = providers.environmentVariable("KINGDOMS_KEYSTORE_PASSWORD").get()
                keyAlias = providers.environmentVariable("KINGDOMS_KEY_ALIAS").get()
                keyPassword = providers.environmentVariable("KINGDOMS_KEY_PASSWORD").get()
            }
        }
    }
    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
        }
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(projects.composeApp)
    implementation(libs.androidx.activity.compose)
}
