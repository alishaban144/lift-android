plugins {
    id("com.android.application") version "9.3.0"
}

val signingPath = providers.environmentVariable("LIFT_KEYSTORE").orNull

android {
    namespace = "app.lift.audio"
    compileSdk = 37

    defaultConfig {
        applicationId = "app.lift.audio"
        minSdk = 29
        targetSdk = 37
        versionCode = 1
        versionName = "1.0.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    if (signingPath != null) {
        val publish = signingConfigs.create("publish") {
            storeFile = file(signingPath)
            storePassword = providers.environmentVariable("LIFT_STORE_PASSWORD").get()
            keyAlias = providers.environmentVariable("LIFT_KEY_ALIAS").get()
            keyPassword = providers.environmentVariable("LIFT_KEY_PASSWORD").get()
        }
        buildTypes.getByName("release").signingConfig = publish
    }
}
